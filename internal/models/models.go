package models

import "time"

// ShowDetails is the domain view of a show page returned by the ARD Audiothek.
type ShowDetails struct {
	ID             string
	Title          string
	Description    string
	CanonicalURL   string
	ImageURL       string
	Episodes       []EpisodeDetails
	HasMoreEpisodes bool
}

// EpisodeDetails holds the metadata for a single episode.
type EpisodeDetails struct {
	ID              string
	Title           string
	Summary         string
	PublishDate     *time.Time
	DurationSeconds *int64
	Audio           *AudioAsset
	Link            string
	ImageURL        string
}

// AudioAsset describes a streamable/downloadable audio file.
type AudioAsset struct {
	URL         string
	MimeType    string
	DownloadURL string
	LengthBytes *int64
}
