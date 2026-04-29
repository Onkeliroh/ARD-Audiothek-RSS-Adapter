package validator

import (
	"errors"
	"net/url"
	"strings"
)

const (
	primaryHost = "www.ardaudiothek.de"
	soundsHost  = "www.ardsounds.de"
)

// InvalidFeedURLError is returned when the supplied URL is not a valid Audiothek show URL.
type InvalidFeedURLError struct {
	Message string
}

func (e *InvalidFeedURLError) Error() string { return e.Message }

// Normalize validates rawValue and returns its canonical string form. An
// *InvalidFeedURLError is returned for any malformed input.
func Normalize(rawValue string) (string, error) {
	trimmed := strings.TrimSpace(rawValue)
	if trimmed == "" {
		return "", &InvalidFeedURLError{Message: "Feed URL must not be empty."}
	}
	u, err := url.Parse(trimmed)
	if err != nil {
		return "", &InvalidFeedURLError{Message: "Feed URL must be a valid URL."}
	}
	scheme := strings.ToLower(u.Scheme)
	if scheme == "" {
		return "", &InvalidFeedURLError{Message: "Feed URL must include http:// or https://."}
	}
	if scheme != "http" && scheme != "https" {
		return "", &InvalidFeedURLError{Message: "Feed URL must start with http:// or https://."}
	}
	if u.Host == "" {
		return "", &InvalidFeedURLError{Message: "Feed URL must include a hostname."}
	}
	host := strings.ToLower(u.Hostname())
	if host != primaryHost && host != soundsHost {
		return "", &InvalidFeedURLError{Message: "Feed URL must use host www.ardaudiothek.de or www.ardsounds.de."}
	}
	return u.String(), nil
}

// IsInvalidFeedURLError reports whether err is an *InvalidFeedURLError.
func IsInvalidFeedURLError(err error) bool {
	var e *InvalidFeedURLError
	return errors.As(err, &e)
}
