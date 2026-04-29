package parser

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/models"
)

const (
	defaultImageWidth = "512"
	defaultBaseURL    = "https://www.ardaudiothek.de"
)

// ShowParsingError is returned when the ARD Audiothek page cannot be parsed.
type ShowParsingError struct {
	Message string
}

func (e *ShowParsingError) Error() string { return e.Message }

// IsShowParsingError reports whether err is a *ShowParsingError.
func IsShowParsingError(err error) bool {
	var e *ShowParsingError
	return errors.As(err, &e)
}

// Parse extracts ShowDetails from the raw HTML of an ARD Audiothek show page.
// pageBaseURL is used to build absolute canonical URLs; if empty it defaults to
// "https://www.ardaudiothek.de".
func Parse(html string, pageBaseURL string) (*models.ShowDetails, error) {
	base := resolveBase(pageBaseURL)
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, &ShowParsingError{Message: fmt.Sprintf("could not parse HTML: %s", err)}
	}

	payload, err := extractNextData(doc)
	if err != nil {
		return nil, err
	}

	resultNode, ok := deepGet(payload, "props", "pageProps", "initialData", "data", "result")
	if !ok {
		return nil, &ShowParsingError{Message: "Could not locate show data in __NEXT_DATA__ payload"}
	}

	title := stringField(resultNode, "title")
	description := strings.TrimSpace(stringField(resultNode, "description"))
	path := stringField(resultNode, "path")
	imageURL := extractImageURL(resultNode)

	itemsNode, _ := resultNode["items"].(map[string]any)
	episodesNode, _ := itemsNode["nodes"].([]any)
	pageInfo, _ := itemsNode["pageInfo"].(map[string]any)
	hasMore, _ := pageInfo["hasNextPage"].(bool)

	var episodes []models.EpisodeDetails
	for _, raw := range episodesNode {
		node, ok := raw.(map[string]any)
		if !ok {
			continue
		}
		ep, err := mapEpisode(node, base)
		if err != nil {
			continue
		}
		episodes = append(episodes, *ep)
	}

	id := stringField(resultNode, "coreId")
	if id == "" {
		id = stringField(resultNode, "id")
	}

	return &models.ShowDetails{
		ID:              id,
		Title:           title,
		Description:     description,
		CanonicalURL:    buildCanonicalURL(path, base),
		ImageURL:        imageURL,
		Episodes:        episodes,
		HasMoreEpisodes: hasMore,
	}, nil
}

func mapEpisode(node map[string]any, base string) (*models.EpisodeDetails, error) {
	title := strings.TrimSpace(stringField(node, "title"))
	if title == "" {
		return nil, errors.New("episode has no title")
	}
	linkPath := stringField(node, "path")
	if linkPath == "" {
		return nil, errors.New("episode has no path")
	}

	summary := strings.TrimSpace(stringField(node, "summary"))
	var publishDate *time.Time
	if raw := stringField(node, "publishDate"); raw != "" {
		if t, err := time.Parse(time.RFC3339, raw); err == nil {
			publishDate = &t
		}
	}

	var durationSeconds *int64
	if durRaw, ok := node["duration"]; ok && durRaw != nil {
		switch v := durRaw.(type) {
		case float64:
			d := int64(v)
			durationSeconds = &d
		case json.Number:
			if d, err := v.Int64(); err == nil {
				durationSeconds = &d
			}
		}
	}

	imageURL := extractImageURL(node)

	var audio *models.AudioAsset
	if audiosRaw, ok := node["audios"].([]any); ok && len(audiosRaw) > 0 {
		// prefer first audio whose url starts with http
		var chosen map[string]any
		for _, a := range audiosRaw {
			candidate, ok := a.(map[string]any)
			if !ok {
				continue
			}
			u := stringField(candidate, "url")
			if strings.HasPrefix(strings.ToLower(u), "http") {
				chosen = candidate
				break
			}
		}
		if chosen == nil {
			if first, ok := audiosRaw[0].(map[string]any); ok {
				chosen = first
			}
		}
		if chosen != nil {
			audio = &models.AudioAsset{
				URL:         stringField(chosen, "url"),
				MimeType:    stringField(chosen, "mimeType"),
				DownloadURL: stringField(chosen, "downloadUrl"),
			}
			if fs, ok := chosen["fileSize"]; ok && fs != nil {
				switch v := fs.(type) {
				case float64:
					l := int64(v)
					audio.LengthBytes = &l
				case json.Number:
					if l, err := v.Int64(); err == nil {
						audio.LengthBytes = &l
					}
				}
			}
		}
	}

	id := stringField(node, "coreId")
	if id == "" {
		id = stringField(node, "id")
	}

	return &models.EpisodeDetails{
		ID:              id,
		Title:           title,
		Summary:         summary,
		PublishDate:     publishDate,
		DurationSeconds: durationSeconds,
		Audio:           audio,
		Link:            buildCanonicalURL(linkPath, base),
		ImageURL:        imageURL,
	}, nil
}

func extractNextData(doc *goquery.Document) (map[string]any, error) {
	script := doc.Find("script#__NEXT_DATA__")
	if script.Length() == 0 {
		return nil, &ShowParsingError{Message: "__NEXT_DATA__ script tag is missing from the ARD Audiothek page"}
	}
	payload := strings.TrimSpace(script.Text())
	if payload == "" {
		return nil, &ShowParsingError{Message: "__NEXT_DATA__ payload is empty"}
	}
	var result map[string]any
	if err := json.Unmarshal([]byte(payload), &result); err != nil {
		return nil, &ShowParsingError{Message: fmt.Sprintf("__NEXT_DATA__ payload could not be parsed: %s", err)}
	}
	return result, nil
}

func buildCanonicalURL(path, base string) string {
	if strings.TrimSpace(path) == "" {
		return base
	}
	sanitized := strings.TrimPrefix(path, "/")
	trimmedBase := strings.TrimRight(base, "/")
	result := trimmedBase + "/" + sanitized
	if !strings.HasSuffix(result, "/") {
		result += "/"
	}
	return result
}

// resolveBase returns the scheme+host portion of pageURL, falling back to
// defaultBaseURL when pageURL is empty or cannot be parsed.
func resolveBase(pageURL string) string {
	if pageURL == "" {
		return defaultBaseURL
	}
	u, err := url.Parse(pageURL)
	if err != nil || u.Scheme == "" || u.Host == "" {
		return defaultBaseURL
	}
	return u.Scheme + "://" + u.Host
}

func extractImageURL(node map[string]any) string {
	imageNode, _ := node["image"].(map[string]any)
	if imageNode == nil {
		return ""
	}
	for _, key := range []string{"url1X1", "url"} {
		if v := stringField(imageNode, key); v != "" {
			return strings.ReplaceAll(v, "{width}", defaultImageWidth)
		}
	}
	return ""
}

// deepGet traverses a nested map using the given keys.
func deepGet(m map[string]any, keys ...string) (map[string]any, bool) {
	current := m
	for _, k := range keys {
		next, ok := current[k].(map[string]any)
		if !ok {
			return nil, false
		}
		current = next
	}
	return current, true
}

// stringField returns the string value of key in m, or "" if missing/not a string.
func stringField(m map[string]any, key string) string {
	v, ok := m[key]
	if !ok || v == nil {
		return ""
	}
	s, _ := v.(string)
	return s
}
