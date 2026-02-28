package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/internal/cache"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/internal/client"
)

// roundTripFunc adapts a function to http.RoundTripper.
type roundTripFunc func(req *http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) { return f(req) }

const testAudiothekURL = "https://www.ardaudiothek.de/sendung/jagd-auf-fantomas-ard-hoerspiel-serie/urn:ard:show:ef3205b54d97da0e/"

func readFixture(t *testing.T) string {
	t.Helper()
	data, err := os.ReadFile("../../testdata/jagd-auf-fantomas.html")
	if err != nil {
		t.Fatalf("could not read fixture: %v", err)
	}
	return string(data)
}

func newTestServerWith(t *testing.T, transport http.RoundTripper) *httptest.Server {
	t.Helper()
	httpClient := &http.Client{Transport: transport}
	showClient := client.New(httpClient)
	feedCache := cache.New(time.Hour)
	return httptest.NewServer(newServer(showClient, feedCache))
}

func TestRootRespondsWithStatusText(t *testing.T) {
	ts := newTestServerWith(t, roundTripFunc(func(req *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(""))}, nil
	}))
	defer ts.Close()

	resp, err := http.Get(ts.URL + "/")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "ARD Audiothek RSS Mapper") {
		t.Error("expected 'ARD Audiothek RSS Mapper' in response body")
	}
}

func TestHealthEndpointRespondsWithOK(t *testing.T) {
	ts := newTestServerWith(t, roundTripFunc(func(req *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(""))}, nil
	}))
	defer ts.Close()

	resp, err := http.Get(ts.URL + "/health")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "ARD Audiothek RSS Adapter is running." {
		t.Errorf("body = %q", string(body))
	}
}

func TestRSSFeedEndpointReturnsCachedDocument(t *testing.T) {
	sampleHTML := readFixture(t)
	requestCount := 0

	// Upstream server serving the fixture
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount++
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		io.WriteString(w, sampleHTML)
	}))
	defer upstream.Close()

	httpClient := upstream.Client()
	showClient := client.New(httpClient)
	feedCache := cache.New(time.Hour)

	appSrv := httptest.NewServer(newServer(showClient, feedCache))
	defer appSrv.Close()

	encodedURL := url.QueryEscape(upstream.URL + "/show/")

	resp1, err := http.Get(appSrv.URL + "/rss/feed/" + encodedURL)
	if err != nil {
		t.Fatal(err)
	}
	defer resp1.Body.Close()
	body1, _ := io.ReadAll(resp1.Body)

	if resp1.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, body = %s", resp1.StatusCode, string(body1))
	}
	if !strings.Contains(string(body1), "Jagd auf Fantomas") {
		t.Error("expected show title in RSS feed")
	}

	// Second request — should use cache
	resp2, err := http.Get(appSrv.URL + "/rss/feed/" + encodedURL)
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	body2, _ := io.ReadAll(resp2.Body)

	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("second request status = %d", resp2.StatusCode)
	}
	if string(body1) != string(body2) {
		t.Error("expected same body from cache")
	}
	if requestCount != 1 {
		t.Errorf("upstream hit %d times, want 1", requestCount)
	}
}

func TestRSSFeedBubblesUpstreamFailures(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		io.WriteString(w, "upstream-error")
	}))
	defer upstream.Close()

	httpClient := upstream.Client()
	showClient := client.New(httpClient)
	feedCache := cache.New(time.Hour)

	appSrv := httptest.NewServer(newServer(showClient, feedCache))
	defer appSrv.Close()

	encodedURL := url.QueryEscape(upstream.URL + "/problem-feed")
	resp, err := http.Get(appSrv.URL + "/rss/feed/" + encodedURL)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusBadGateway {
		t.Errorf("status = %d, want 502, body = %s", resp.StatusCode, string(body))
	}
	if !strings.Contains(string(body), "Failed to fetch show page") {
		t.Error("expected error message in response")
	}
}

func TestRSSFeedReturns500WhenParserFails(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		io.WriteString(w, "<html><body>No NEXT data</body></html>")
	}))
	defer upstream.Close()

	httpClient := upstream.Client()
	showClient := client.New(httpClient)
	feedCache := cache.New(time.Hour)

	appSrv := httptest.NewServer(newServer(showClient, feedCache))
	defer appSrv.Close()

	encodedURL := url.QueryEscape(upstream.URL + "/parser-error")
	resp, err := http.Get(appSrv.URL + "/rss/feed/" + encodedURL)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusInternalServerError {
		t.Errorf("status = %d, want 500, body = %s", resp.StatusCode, string(body))
	}
	if !strings.Contains(string(body), "script tag is missing") {
		t.Errorf("expected parse error message, got: %s", string(body))
	}
}

func TestRSSFeedReturns400ForInvalidURLs(t *testing.T) {
	ts := newTestServerWith(t, roundTripFunc(func(req *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(""))}, nil
	}))
	defer ts.Close()

	resp, err := http.Get(ts.URL + "/rss/feed/urn:ard:show:ef3205b54d97da0e")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("status = %d, want 400, body = %s", resp.StatusCode, string(body))
	}
	if !strings.Contains(string(body), "Feed URL") {
		t.Error("expected Feed URL error message")
	}
}
