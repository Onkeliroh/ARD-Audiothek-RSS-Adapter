package parser_test

import (
	"os"
	"strings"
	"testing"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/parser"
)

func readFixture(t *testing.T) string {
	t.Helper()
	data, err := os.ReadFile("../../../testdata/jagd-auf-fantomas.html")
	if err != nil {
		t.Fatalf("could not read fixture: %v", err)
	}
	return string(data)
}

func wrapPayload(payload string) string {
	return `<html><body><script id="__NEXT_DATA__" type="application/json">` +
		payload +
		`</script></body></html>`
}

func TestParsesShowDetailsFromNextData(t *testing.T) {
	html := readFixture(t)
	show, err := parser.Parse(html, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if show.Title != "Jagd auf Fantomas | ARD Hörspiel-Serie" {
		t.Errorf("title = %q", show.Title)
	}
	wantURL := "https://www.ardaudiothek.de/sendung/jagd-auf-fantomas-ard-hoerspiel-serie/urn:ard:show:ef3205b54d97da0e/"
	if show.CanonicalURL != wantURL {
		t.Errorf("canonicalURL = %q, want %q", show.CanonicalURL, wantURL)
	}
	if !show.HasMoreEpisodes {
		t.Error("expected HasMoreEpisodes = true")
	}
	if len(show.Episodes) != 12 {
		t.Errorf("episodes count = %d, want 12", len(show.Episodes))
	}
	first := show.Episodes[0]
	if first.Title != "Jagd auf Fantomas (Trailer)" {
		t.Errorf("first episode title = %q", first.Title)
	}
	if first.Link != "https://www.ardaudiothek.de/episode/urn:ard:extra:95856bc858a741da/" {
		t.Errorf("first episode link = %q", first.Link)
	}
	if first.Audio == nil {
		t.Fatal("expected audio asset for first episode")
	}
	if !strings.HasSuffix(first.Audio.URL, ".mp3") {
		t.Errorf("audio URL %q does not end with .mp3", first.Audio.URL)
	}
}

func TestParseFallbackFieldsAndFiltersInvalidEpisodes(t *testing.T) {
	html := wrapPayload(`{
		"props": {
			"pageProps": {
				"initialData": {
					"data": {
						"result": {
							"coreId": "urn:ard:show:fallback",
							"title": "Fallback",
							"description": "  Trim me  ",
							"path": "",
							"image": { "url1X1": "https://images.example.com/base-{width}" },
							"items": {
								"pageInfo": { "hasNextPage": true },
								"nodes": [
									{ "id": "missing-title", "path": "/episode/ignored/" },
									{
										"coreId": "urn:ard:episode:ok",
										"title": " Episode Trim ",
										"summary": "  Short  ",
										"publishDate": "not-a-date",
										"duration": 180,
										"path": "/episode/valid/",
										"image": { "url": "https://images.example.com/ep-{width}" },
										"audios": []
									}
								]
							}
						}
					}
				}
			}
		}
	}`)
	show, err := parser.Parse(html, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if show.Title != "Fallback" {
		t.Errorf("title = %q", show.Title)
	}
	if show.Description != "Trim me" {
		t.Errorf("description = %q", show.Description)
	}
	if show.CanonicalURL != "https://www.ardaudiothek.de" {
		t.Errorf("canonicalURL = %q", show.CanonicalURL)
	}
	if show.ImageURL != "https://images.example.com/base-512" {
		t.Errorf("imageURL = %q", show.ImageURL)
	}
	if !show.HasMoreEpisodes {
		t.Error("expected HasMoreEpisodes = true")
	}
	if len(show.Episodes) != 1 {
		t.Fatalf("episodes count = %d, want 1", len(show.Episodes))
	}

	ep := show.Episodes[0]
	if ep.Title != "Episode Trim" {
		t.Errorf("episode title = %q", ep.Title)
	}
	if ep.Summary != "Short" {
		t.Errorf("episode summary = %q", ep.Summary)
	}
	if ep.PublishDate != nil {
		t.Errorf("expected nil publishDate for invalid date, got %v", ep.PublishDate)
	}
	if ep.DurationSeconds == nil || *ep.DurationSeconds != 180 {
		t.Errorf("durationSeconds = %v", ep.DurationSeconds)
	}
	if ep.Link != "https://www.ardaudiothek.de/episode/valid/" {
		t.Errorf("link = %q", ep.Link)
	}
	if ep.ImageURL != "https://images.example.com/ep-512" {
		t.Errorf("imageURL = %q", ep.ImageURL)
	}
	if ep.Audio != nil {
		t.Errorf("expected nil audio for empty audios array")
	}
}

func TestParseThrowsWhenNextDataScriptIsMissing(t *testing.T) {
	_, err := parser.Parse("<html><body><p>No script</p></body></html>", "")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !parser.IsShowParsingError(err) {
		t.Errorf("expected ShowParsingError, got %T", err)
	}
}

func TestParseThrowsWhenResultNodeIsMissing(t *testing.T) {
	html := wrapPayload(`{"props":{"pageProps":{"initialData":{"data":{}}}}}`)
	_, err := parser.Parse(html, "")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !parser.IsShowParsingError(err) {
		t.Errorf("expected ShowParsingError, got %T", err)
	}
}

func TestParseThrowsWhenNextPayloadIsEmpty(t *testing.T) {
	html := `<html><body><script id="__NEXT_DATA__" type="application/json"></script></body></html>`
	_, err := parser.Parse(html, "")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "empty") {
		t.Errorf("error %q does not contain 'empty'", err.Error())
	}
}

func TestParseThrowsWhenNextPayloadIsInvalidJSON(t *testing.T) {
	html := `<html><body><script id="__NEXT_DATA__" type="application/json">{not-json}</script></body></html>`
	_, err := parser.Parse(html, "")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "could not be parsed") {
		t.Errorf("error %q does not contain 'could not be parsed'", err.Error())
	}
}

func TestParsePreferFirstUsableAudioURL(t *testing.T) {
	html := wrapPayload(`{
		"props": {
			"pageProps": {
				"initialData": {
					"data": {
						"result": {
							"coreId": "urn:ard:show:audio",
							"title": "Audio Pref",
							"description": null,
							"path": "/sendung/audio-pref/",
							"image": { "url": "" },
							"items": {
								"pageInfo": { "hasNextPage": false },
								"nodes": [{
									"coreId": "urn:ard:episode:audio",
									"title": "Episode",
									"summary": null,
									"publishDate": null,
									"duration": null,
									"path": "/episode/audio/",
									"image": { "url": "" },
									"audios": [
										{ "url": "//relative-path.mp3" },
										{ "url": "https://cdn.example.com/preferred.mp3", "mimeType": "audio/aac" }
									]
								}]
							}
						}
					}
				}
			}
		}
	}`)

	show, err := parser.Parse(html, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	audio := show.Episodes[0].Audio
	if audio == nil {
		t.Fatal("expected audio asset")
	}
	if audio.URL != "https://cdn.example.com/preferred.mp3" {
		t.Errorf("audio URL = %q, want https://cdn.example.com/preferred.mp3", audio.URL)
	}
	if audio.MimeType != "audio/aac" {
		t.Errorf("audio mimeType = %q, want audio/aac", audio.MimeType)
	}
}

func TestParseUsesProvidedBaseURLForCanonicalLinks(t *testing.T) {
	html := wrapPayload(`{
		"props": {
			"pageProps": {
				"initialData": {
					"data": {
						"result": {
							"coreId": "urn:ard:show:sounds",
							"title": "Sounds Show",
							"description": "A sounds show",
							"path": "/sendung/sounds-show/urn:ard:show:sounds/",
							"image": { "url": "" },
							"items": {
								"pageInfo": { "hasNextPage": false },
								"nodes": [{
									"coreId": "urn:ard:episode:sounds",
									"title": "Sounds Episode",
									"summary": null,
									"publishDate": null,
									"duration": null,
									"path": "/episode/sounds-episode/urn:ard:episode:sounds/",
									"image": { "url": "" },
									"audios": [{ "url": "https://cdn.example.com/audio.mp3" }]
								}]
							}
						}
					}
				}
			}
		}
	}`)

	show, err := parser.Parse(html, "https://www.ardsounds.de/sendung/sounds-show/urn:ard:show:sounds/")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	wantShowURL := "https://www.ardsounds.de/sendung/sounds-show/urn:ard:show:sounds/"
	if show.CanonicalURL != wantShowURL {
		t.Errorf("show CanonicalURL = %q, want %q", show.CanonicalURL, wantShowURL)
	}
	if len(show.Episodes) != 1 {
		t.Fatalf("episodes count = %d, want 1", len(show.Episodes))
	}
	wantEpLink := "https://www.ardsounds.de/episode/sounds-episode/urn:ard:episode:sounds/"
	if show.Episodes[0].Link != wantEpLink {
		t.Errorf("episode Link = %q, want %q", show.Episodes[0].Link, wantEpLink)
	}
}
