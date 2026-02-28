package rss_test

import (
	"encoding/xml"
	"strings"
	"testing"
	"time"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/models"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/rss"
)

func ptr[T any](v T) *T { return &v }

func TestBuildProducesFullyPopulatedRSSFeed(t *testing.T) {
	newerPublishDate := mustParseTime("2024-02-03T10:15:30Z")
	olderPublishDate := mustParseTime("2024-01-01T08:00:00Z")

	show := &models.ShowDetails{
		ID:           "show-1",
		Title:        "Sample Show",
		Description:  "",
		CanonicalURL: "https://www.ardaudiothek.de/sendung/sample-show/",
		ImageURL:     "https://images.example.com/show.jpg",
		Episodes: []models.EpisodeDetails{
			{
				ID:              "episode-1",
				Title:           "Episode 1",
				Summary:         "A detailed summary",
				PublishDate:     &newerPublishDate,
				DurationSeconds: ptr(int64(3723)),
				Audio: &models.AudioAsset{
					URL:         "https://cdn.example.com/stream.mp3",
					MimeType:    "",
					DownloadURL: "https://cdn.example.com/download.mp3",
					LengthBytes: ptr(int64(1024)),
				},
				Link:     "https://www.ardaudiothek.de/episode/episode-1/",
				ImageURL: "https://images.example.com/cover1.jpg",
			},
			{
				ID:              "episode-2",
				Title:           "Episode 2",
				Summary:         "",
				PublishDate:     &olderPublishDate,
				DurationSeconds: nil,
				Audio: &models.AudioAsset{
					URL:         "https://cdn.example.com/stream2.ogg",
					MimeType:    "audio/ogg",
					DownloadURL: "",
					LengthBytes: nil,
				},
				Link:     "https://www.ardaudiothek.de/episode/episode-2/",
				ImageURL: "",
			},
		},
		HasMoreEpisodes: false,
	}

	xmlStr, err := rss.Build(show)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Verify it is valid XML
	if err := xml.Unmarshal([]byte(xmlStr), &struct{ XMLName xml.Name }{}); err != nil {
		t.Fatalf("expected valid XML, got parse error: %v", err)
	}

	// Check iTunes namespace
	if !strings.Contains(xmlStr, "xmlns:itunes") {
		t.Error("expected iTunes namespace declaration")
	}
	if !strings.Contains(xmlStr, "itunes:") {
		t.Error("expected iTunes elements")
	}

	// Explicit should use "false" not "no"
	if !strings.Contains(xmlStr, "<itunes:explicit>false</itunes:explicit>") {
		t.Error("expected <itunes:explicit>false</itunes:explicit>")
	}
	if strings.Contains(xmlStr, "<itunes:explicit>no</itunes:explicit>") {
		t.Error("itunes:explicit must not contain 'no'")
	}

	// Title and link
	if !strings.Contains(xmlStr, "<title>Sample Show</title>") {
		t.Error("expected show title in feed")
	}
	if !strings.Contains(xmlStr, show.CanonicalURL) {
		t.Error("expected canonical URL in feed")
	}

	// Default description fallback
	if !strings.Contains(xmlStr, "Episodenfeed aus der ARD Audiothek") {
		t.Error("expected default description")
	}

	// pubDate is derived from the latest episode
	if !strings.Contains(xmlStr, "2024") {
		t.Error("expected year 2024 in pubDate")
	}

	// Episode 1 enclosure
	if !strings.Contains(xmlStr, "https://cdn.example.com/download.mp3") {
		t.Error("expected download URL in enclosure")
	}
	if !strings.Contains(xmlStr, `type="audio/mpeg"`) {
		t.Error("expected default mime type audio/mpeg")
	}
	if !strings.Contains(xmlStr, `length="1024"`) {
		t.Error("expected length=1024 in enclosure")
	}

	// Description HTML for episode 1
	if !strings.Contains(xmlStr, `<img src="https://images.example.com/cover1.jpg"`) {
		t.Error("expected cover image in description")
	}
	if !strings.Contains(xmlStr, "A detailed summary") {
		t.Error("expected summary in description")
	}
	if !strings.Contains(xmlStr, "<strong>Spielzeit:</strong> 1h 2m 3s") {
		t.Error("expected duration in description")
	}

	// Episode 2 should have an enclosure with length=0 (no LengthBytes provided)
	if strings.Count(xmlStr, "<enclosure") != 2 {
		t.Errorf("expected exactly 2 enclosures, got different count in:\n%s", xmlStr)
	}
	// Episode 1 should have length=1024, Episode 2 should have length=0
	if !strings.Contains(xmlStr, `length="0"`) {
		t.Error("expected length=0 for episode without LengthBytes")
	}

	// Episode 2 description should contain fallback
	if !strings.Contains(xmlStr, "Keine Beschreibung") {
		t.Error("expected 'Keine Beschreibung' for episode without summary")
	}
}

func TestBuildHandlesMissingDatesAndAudioFallbacks(t *testing.T) {
	show := &models.ShowDetails{
		ID:           "show-2",
		Title:        "Metadata Showcase",
		Description:  "Offizieller Beschreibungstext",
		CanonicalURL: "https://www.ardaudiothek.de/sendung/metadata-show/",
		ImageURL:     "",
		Episodes: []models.EpisodeDetails{
			{
				ID:              "episode-3",
				Title:           "Episode 3",
				Summary:         "Compact overview",
				PublishDate:     nil,
				DurationSeconds: ptr(int64(125)),
				Audio: &models.AudioAsset{
					URL:         "https://cdn.example.com/stream2.mp3",
					MimeType:    "audio/aac",
					DownloadURL: "",
					LengthBytes: nil,
				},
				Link:     "https://www.ardaudiothek.de/episode/episode-3/",
				ImageURL: "",
			},
			{
				ID:              "episode-4",
				Title:           "Episode 4",
				Summary:         "Quick listen",
				PublishDate:     nil,
				DurationSeconds: ptr(int64(42)),
				Audio:           nil,
				Link:            "https://www.ardaudiothek.de/episode/episode-4/",
				ImageURL:        "https://images.example.com/cover4.jpg",
			},
		},
		HasMoreEpisodes: true,
	}

	xmlStr, err := rss.Build(show)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if !strings.Contains(xmlStr, "Offizieller Beschreibungstext") {
		t.Error("expected show description")
	}
	// No pubDate when all episodes have nil PublishDate
	if strings.Contains(xmlStr, "<pubDate>") {
		t.Error("expected no pubDate when episodes have no dates")
	}

	// Episode 3: should have enclosure with length=0 (has URL but no lengthBytes)
	if !strings.Contains(xmlStr, "<enclosure") {
		t.Error("expected enclosure for episode 3 with audio URL")
	}
	if !strings.Contains(xmlStr, `length="0"`) {
		t.Error("expected length=0 for episode 3 without LengthBytes")
	}
	if !strings.Contains(xmlStr, `type="audio/aac"`) {
		t.Error("expected audio/aac mime type for episode 3")
	}
	// Episode 4: no enclosure (no audio asset at all)
	if strings.Count(xmlStr, "<enclosure") != 1 {
		t.Errorf("expected exactly 1 enclosure (only for episode 3), got different count")
	}

	// Duration formatting
	if !strings.Contains(xmlStr, "2m 5s") {
		t.Errorf("expected '2m 5s' for 125 seconds in:\n%s", xmlStr)
	}
	if !strings.Contains(xmlStr, "42s") {
		t.Errorf("expected '42s' for 42 seconds in:\n%s", xmlStr)
	}

	// Episode 4 image in description
	if !strings.Contains(xmlStr, `<img src="https://images.example.com/cover4.jpg"`) {
		t.Error("expected episode 4 cover in description")
	}
}

func TestFormatDuration(t *testing.T) {
	tests := []struct {
		seconds int64
		want    string
	}{
		{0, "0s"},
		{42, "42s"},
		{60, "1m 0s"},
		{125, "2m 5s"},
		{3600, "1h 0m 0s"},
		{3723, "1h 2m 3s"},
	}
	for _, tc := range tests {
		show := &models.ShowDetails{
			Title:        "t",
			CanonicalURL: "https://example.org/",
			Episodes: []models.EpisodeDetails{{
				ID:              "id",
				Title:           "e",
				DurationSeconds: &tc.seconds,
				Link:            "https://example.org/e/",
			}},
		}
		xmlStr, err := rss.Build(show)
		if err != nil {
			t.Fatalf("Build error for %d: %v", tc.seconds, err)
		}
		if !strings.Contains(xmlStr, tc.want) {
			t.Errorf("seconds=%d: expected %q in output:\n%s", tc.seconds, tc.want, xmlStr)
		}
	}
}

func mustParseTime(s string) time.Time {
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		panic(err)
	}
	return t
}
