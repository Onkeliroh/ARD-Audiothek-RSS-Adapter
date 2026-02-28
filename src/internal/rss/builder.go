package rss

import (
	"bytes"
	"encoding/xml"
	"fmt"
	"strings"
	"time"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/models"
)

const (
	defaultAuthor     = "ARD Audiothek"
	defaultOwnerEmail = "info@ard-audiothek.de"
	defaultCategory   = "Society & Culture"
	defaultDesc       = "Episodenfeed aus der ARD Audiothek"
)

// Build converts a ShowDetails into a valid RSS 2.0 + iTunes XML string.
func Build(show *models.ShowDetails) (string, error) {
	description := show.Description
	if description == "" {
		description = defaultDesc
	}

	var pubDate string
	var latest *time.Time
	for i := range show.Episodes {
		ep := &show.Episodes[i]
		if ep.PublishDate != nil {
			if latest == nil || ep.PublishDate.After(*latest) {
				latest = ep.PublishDate
			}
		}
	}
	if latest != nil {
		pubDate = latest.UTC().Format(time.RFC1123Z)
	}

	feed := rssRoot{
		Version:   "2.0",
		ItunesNS:  "http://www.itunes.com/dtds/podcast-1.0.dtd",
		ContentNS: "http://purl.org/rss/1.0/modules/content/",
		Channel: rssChannel{
			Title:        show.Title,
			Link:         show.CanonicalURL,
			Description:  description,
			Language:     "de-DE",
			PubDate:      pubDate,
			ItunesAuthor: defaultAuthor,
			ItunesOwner: itunesOwner{
				Name:  defaultAuthor,
				Email: defaultOwnerEmail,
			},
			ItunesSummary:  description,
			ItunesCategory: itunesCategory{Text: defaultCategory},
			ItunesExplicit: "false",
		},
	}

	if show.ImageURL != "" {
		feed.Channel.Image = &rssImage{
			URL:   show.ImageURL,
			Title: show.Title,
			Link:  show.CanonicalURL,
		}
		feed.Channel.ItunesImage = &itunesImage{Href: show.ImageURL}
	}

	for i := range show.Episodes {
		ep := &show.Episodes[i]
		item := buildItem(ep)
		feed.Channel.Items = append(feed.Channel.Items, item)
	}

	var buf bytes.Buffer
	buf.WriteString(xml.Header)
	enc := xml.NewEncoder(&buf)
	enc.Indent("", "  ")
	if err := enc.Encode(feed); err != nil {
		return "", fmt.Errorf("could not encode RSS feed: %w", err)
	}
	return buf.String(), nil
}

func buildItem(ep *models.EpisodeDetails) rssItem {
	item := rssItem{
		Title:          ep.Title,
		Link:           ep.Link,
		GUID:           rssGUID{Value: ep.ID},
		ItunesAuthor:   defaultAuthor,
		ItunesSummary:  ep.Summary,
		ItunesExplicit: "false",
	}

	if ep.PublishDate != nil {
		item.PubDate = ep.PublishDate.UTC().Format(time.RFC1123Z)
	}

	item.Description = rssDescription{Value: buildDescription(ep)}

	if ep.DurationSeconds != nil {
		item.ItunesDuration = fmt.Sprintf("%d", *ep.DurationSeconds)
	}
	if ep.ImageURL != "" {
		item.ItunesImage = &itunesImage{Href: ep.ImageURL}
	}

	if ep.Audio != nil {
		audioURL := ep.Audio.DownloadURL
		if audioURL == "" {
			audioURL = ep.Audio.URL
		}
		mimeType := ep.Audio.MimeType
		if mimeType == "" {
			mimeType = "audio/mpeg"
		}
		if audioURL != "" {
			length := int64(0)
			if ep.Audio.LengthBytes != nil {
				length = *ep.Audio.LengthBytes
			}
			item.Enclosure = &rssEnclosure{
				URL:    audioURL,
				Type:   mimeType,
				Length: length,
			}
		}
	}

	return item
}

func buildDescription(ep *models.EpisodeDetails) string {
	var sb strings.Builder
	if ep.ImageURL != "" {
		fmt.Fprintf(&sb, `<p><img src="%s" alt="Episode cover" loading="lazy" /></p>`, ep.ImageURL)
	}
	if ep.Summary != "" {
		fmt.Fprintf(&sb, "<p>%s</p>", ep.Summary)
	}
	if ep.DurationSeconds != nil {
		fmt.Fprintf(&sb, "<p><strong>Spielzeit:</strong> %s</p>", formatDuration(*ep.DurationSeconds))
	}
	if sb.Len() == 0 {
		summary := ep.Summary
		if summary == "" {
			summary = "Keine Beschreibung verfuegbar."
		}
		return "<p>" + summary + "</p>"
	}
	return sb.String()
}

func formatDuration(seconds int64) string {
	h := seconds / 3600
	m := (seconds % 3600) / 60
	s := seconds % 60
	var sb strings.Builder
	if h > 0 {
		fmt.Fprintf(&sb, "%dh ", h)
	}
	if m > 0 || h > 0 {
		fmt.Fprintf(&sb, "%dm ", m)
	}
	fmt.Fprintf(&sb, "%ds", s)
	return strings.TrimSpace(sb.String())
}

// ─── XML structs ────────────────────────────────────────────────────────────

type rssRoot struct {
	XMLName   xml.Name   `xml:"rss"`
	Version   string     `xml:"version,attr"`
	ItunesNS  string     `xml:"xmlns:itunes,attr"`
	ContentNS string     `xml:"xmlns:content,attr"`
	Channel   rssChannel `xml:"channel"`
}

type rssChannel struct {
	Title          string         `xml:"title"`
	Link           string         `xml:"link"`
	Description    string         `xml:"description"`
	Language       string         `xml:"language"`
	PubDate        string         `xml:"pubDate,omitempty"`
	Image          *rssImage      `xml:"image,omitempty"`
	ItunesAuthor   string         `xml:"itunes:author"`
	ItunesOwner    itunesOwner    `xml:"itunes:owner"`
	ItunesImage    *itunesImage   `xml:"itunes:image,omitempty"`
	ItunesSummary  string         `xml:"itunes:summary"`
	ItunesCategory itunesCategory `xml:"itunes:category"`
	ItunesExplicit string         `xml:"itunes:explicit"`
	Items          []rssItem      `xml:"item"`
}

type rssImage struct {
	URL   string `xml:"url"`
	Title string `xml:"title"`
	Link  string `xml:"link"`
}

type itunesOwner struct {
	Name  string `xml:"itunes:name"`
	Email string `xml:"itunes:email"`
}

type itunesImage struct {
	Href string `xml:"href,attr"`
}

type itunesCategory struct {
	Text string `xml:"text,attr"`
}

type rssItem struct {
	Title          string         `xml:"title"`
	Link           string         `xml:"link"`
	GUID           rssGUID        `xml:"guid"`
	PubDate        string         `xml:"pubDate,omitempty"`
	Description    rssDescription `xml:"description"`
	Enclosure      *rssEnclosure  `xml:"enclosure,omitempty"`
	ItunesAuthor   string         `xml:"itunes:author"`
	ItunesSummary  string         `xml:"itunes:summary,omitempty"`
	ItunesDuration string         `xml:"itunes:duration,omitempty"`
	ItunesImage    *itunesImage   `xml:"itunes:image,omitempty"`
	ItunesExplicit string         `xml:"itunes:explicit"`
}

type rssGUID struct {
	Value string `xml:",chardata"`
}

type rssDescription struct {
	Value string `xml:",cdata"`
}

type rssEnclosure struct {
	URL    string `xml:"url,attr"`
	Type   string `xml:"type,attr"`
	Length int64  `xml:"length,attr"`
}
