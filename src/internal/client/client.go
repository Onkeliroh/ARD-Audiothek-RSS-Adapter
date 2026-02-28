package client

import (
	"errors"
	"fmt"
	"io"
	"net/http"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/models"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/parser"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/validator"
)

// ShowRetrievalError is returned when the upstream HTTP request fails.
type ShowRetrievalError struct {
	Message string
}

func (e *ShowRetrievalError) Error() string { return e.Message }

// IsShowRetrievalError reports whether err is a *ShowRetrievalError.
func IsShowRetrievalError(err error) bool {
	var e *ShowRetrievalError
	return errors.As(err, &e)
}

// HTTPClient is the minimal interface used for fetching show pages.
type HTTPClient interface {
	Do(req *http.Request) (*http.Response, error)
}

// ShowPageClient fetches and parses ARD Audiothek show pages.
type ShowPageClient struct {
	httpClient HTTPClient
}

// New creates a ShowPageClient backed by the given HTTPClient.
func New(httpClient HTTPClient) *ShowPageClient {
	return &ShowPageClient{httpClient: httpClient}
}

// FetchShow retrieves the show page at pageURL and parses it into ShowDetails.
func (c *ShowPageClient) FetchShow(pageURL string) (*models.ShowDetails, error) {
	normalizedURL, err := validator.Normalize(pageURL)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest(http.MethodGet, normalizedURL, nil)
	if err != nil {
		return nil, &ShowRetrievalError{Message: fmt.Sprintf("could not create request for %s: %s", normalizedURL, err)}
	}
	req.Header.Set("Accept", "text/html; charset=utf-8")
	req.Header.Set("Accept-Language", "de-DE,de;q=0.9")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, &ShowRetrievalError{Message: fmt.Sprintf("Failed to fetch show page %s: %s", normalizedURL, err)}
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		bodyBytes, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		return nil, &ShowRetrievalError{
			Message: fmt.Sprintf(
				"Failed to fetch show page %s : %d %s. Body: %s",
				normalizedURL, resp.StatusCode, resp.Status, string(bodyBytes),
			),
		}
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, &ShowRetrievalError{Message: fmt.Sprintf("Failed to read response body from %s: %s", normalizedURL, err)}
	}

	return parser.Parse(string(bodyBytes))
}
