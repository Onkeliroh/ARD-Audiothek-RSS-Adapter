package validator_test

import (
	"strings"
	"testing"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/validator"
)

func TestNormalize(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		want    string
		wantErr string
	}{
		{
			name:  "valid https URL",
			input: "https://www.ardaudiothek.de/sendung/foo/bar/",
			want:  "https://www.ardaudiothek.de/sendung/foo/bar/",
		},
		{
			name:  "valid http URL",
			input: "http://www.ardaudiothek.de/sendung/foo/bar/",
			want:  "http://www.ardaudiothek.de/sendung/foo/bar/",
		},
		{
			name:    "non ARD host",
			input:   "https://example.org/path",
			wantErr: "www.ardaudiothek.de",
		},
		{
			name:    "empty string",
			input:   "",
			wantErr: "must not be empty",
		},
		{
			name:    "whitespace only",
			input:   "   ",
			wantErr: "must not be empty",
		},
		{
			name:    "no scheme",
			input:   "www.ardaudiothek.de/sendung/foo/",
			wantErr: "http:// or https://",
		},
		{
			name:    "urn scheme",
			input:   "urn:ard:show:ef3205b54d97da0e",
			wantErr: "http:// or https://",
		},
		{
			name:    "ftp scheme",
			input:   "ftp://example.org/",
			wantErr: "http:// or https://",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := validator.Normalize(tc.input)
			if tc.wantErr != "" {
				if err == nil {
					t.Fatalf("expected error containing %q, got nil", tc.wantErr)
				}
				if !strings.Contains(err.Error(), tc.wantErr) {
					t.Errorf("error %q does not contain %q", err.Error(), tc.wantErr)
				}
				if !validator.IsInvalidFeedURLError(err) {
					t.Errorf("expected InvalidFeedURLError, got %T", err)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if got != tc.want {
				t.Errorf("Normalize(%q) = %q, want %q", tc.input, got, tc.want)
			}
		})
	}
}
