package client_test

import (
	"io"
	"net/http"
	"os"
	"strings"
	"testing"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/client"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/validator"
)

// roundTripFunc adapts a function to the http.RoundTripper interface.
type roundTripFunc func(req *http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) { return f(req) }

func httpClientWith(fn roundTripFunc) *http.Client {
	return &http.Client{Transport: fn}
}

const audiothekURL = "https://www.ardaudiothek.de/sendung/jagd-auf-fantomas-ard-hoerspiel-serie/urn:ard:show:ef3205b54d97da0e/"
const ardsoundsURL = "https://www.ardsounds.de/sendung/reclaim-tic-tac-toe/urn:ard:show:bc0ac195183639e0/"

func readFixture(t *testing.T) string {
	t.Helper()
	data, err := os.ReadFile("../../../testdata/jagd-auf-fantomas.html")
	if err != nil {
		t.Fatalf("could not read fixture: %v", err)
	}
	return string(data)
}

func TestFetchShowForwardsHeadersToURL(t *testing.T) {
	sampleHTML := readFixture(t)

	var capturedReq *http.Request
	mockClient := httpClientWith(func(req *http.Request) (*http.Response, error) {
		capturedReq = req
		return &http.Response{
			StatusCode: 200,
			Body:       io.NopCloser(strings.NewReader(sampleHTML)),
		}, nil
	})

	c := client.New(mockClient)
	show, err := c.FetchShow(audiothekURL)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if show.Title != "Jagd auf Fantomas | ARD Hörspiel-Serie" {
		t.Errorf("title = %q", show.Title)
	}
	if capturedReq.URL.String() != audiothekURL {
		t.Errorf("request URL = %q, want %q", capturedReq.URL.String(), audiothekURL)
	}
	if !strings.Contains(capturedReq.Header.Get("Accept"), "text/html") {
		t.Errorf("Accept header = %q, expected to contain text/html", capturedReq.Header.Get("Accept"))
	}
	if capturedReq.Header.Get("Accept-Language") != "de-DE,de;q=0.9" {
		t.Errorf("Accept-Language = %q", capturedReq.Header.Get("Accept-Language"))
	}
}

func TestFetchShowThrowsForNonSuccessResponse(t *testing.T) {
	mockClient := httpClientWith(func(req *http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: 502,
			Status:     "502 Bad Gateway",
			Body:       io.NopCloser(strings.NewReader("nope")),
		}, nil
	})

	c := client.New(mockClient)
	_, err := c.FetchShow(audiothekURL)
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !client.IsShowRetrievalError(err) {
		t.Errorf("expected ShowRetrievalError, got %T", err)
	}
	if !strings.Contains(err.Error(), "502") {
		t.Errorf("error %q does not contain '502'", err.Error())
	}
}

func TestFetchShowRejectsInvalidURLs(t *testing.T) {
	mockClient := httpClientWith(func(req *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(""))}, nil
	})

	c := client.New(mockClient)
	_, err := c.FetchShow("urn:ard:show:ef3205b54d97da0e")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !validator.IsInvalidFeedURLError(err) {
		t.Errorf("expected InvalidFeedURLError, got %T", err)
	}
}

func TestFetchShowSupportsArdSoundsHost(t *testing.T) {
	sampleHTML := readFixture(t)

	var capturedReq *http.Request
	mockClient := httpClientWith(func(req *http.Request) (*http.Response, error) {
		capturedReq = req
		return &http.Response{
			StatusCode: 200,
			Body:       io.NopCloser(strings.NewReader(sampleHTML)),
		}, nil
	})

	c := client.New(mockClient)
	_, err := c.FetchShow(ardsoundsURL)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if capturedReq.URL.String() != ardsoundsURL {
		t.Fatalf("request URL = %q, want %q", capturedReq.URL.String(), ardsoundsURL)
	}
}
