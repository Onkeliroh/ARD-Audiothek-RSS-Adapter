package main

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"html/template"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/cache"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/client"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/parser"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/rss"
	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/validator"
)

//go:embed templates
var templateFS embed.FS

const (
	defaultPort     = 8411
	defaultCacheTTL = 6 * time.Hour
)

type errorPageData struct {
	StatusCode int
	ErrorCode  string
}

const (
	errorCodeInvalidFeedURL      = "E_INVALID_FEED_URL"
	errorCodeUpstreamUnavailable = "E_UPSTREAM_UNAVAILABLE"
	errorCodeParsingFailed       = "E_PARSING_FAILED"
	errorCodeUnexpectedFailure   = "E_UNEXPECTED_FAILURE"
)

type server struct {
	showClient *client.ShowPageClient
	feedCache  *cache.RSSFeedCache
	indexTmpl  *template.Template
	errorTmpl  *template.Template
	logger     *slog.Logger
}

// newServer wires up the handler tree and returns the resulting http.Handler.
// It is exported for use in tests.
func newServer(showClient *client.ShowPageClient, feedCache *cache.RSSFeedCache) http.Handler {
	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))
	indexTmpl := template.Must(template.ParseFS(templateFS, "templates/index.html"))
	errorTmpl := template.Must(template.ParseFS(templateFS, "templates/error.html"))

	srv := &server{
		showClient: showClient,
		feedCache:  feedCache,
		indexTmpl:  indexTmpl,
		errorTmpl:  errorTmpl,
		logger:     logger,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /", srv.handleIndex)
	mux.HandleFunc("GET /health", srv.handleHealth)
	mux.HandleFunc("GET /rss/feed/", srv.handleRSSFeed)
	return mux
}

func main() {
	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))

	port := resolvePort()
	cacheTTL := resolveCacheTTL()

	httpClient := &http.Client{Timeout: 30 * time.Second}
	showClient := client.New(httpClient)
	feedCache := cache.New(cacheTTL)

	mux := newServer(showClient, feedCache)

	addr := fmt.Sprintf(":%d", port)
	httpServer := &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	errCh := make(chan error, 1)
	logger.Info("Server ready", "addr", fmt.Sprintf("http://localhost%s/", addr))
	go func() {
		errCh <- httpServer.ListenAndServe()
	}()

	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			logger.Error("Server shutdown failed", "err", err)
			os.Exit(1)
		}
		logger.Info("Server stopped")
	case err := <-errCh:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("Server error", "err", err)
			os.Exit(1)
		}
	}
}

func (s *server) handleIndex(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := s.indexTmpl.Execute(w, nil); err != nil {
		s.logger.Error("template render error", "err", err)
	}
}

func (s *server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	fmt.Fprint(w, "ARD Audiothek RSS Adapter is running.")
}

func (s *server) handleRSSFeed(w http.ResponseWriter, r *http.Request) {
	// Path: /rss/feed/{feedUrl...}
	rawFeedURL := strings.TrimPrefix(r.URL.Path, "/rss/feed/")

	// The URL might be percent-encoded
	if decoded, err := url.QueryUnescape(rawFeedURL); err == nil {
		rawFeedURL = decoded
	}

	feedURL, err := validator.Normalize(rawFeedURL)
	if err != nil {
		s.logger.Warn("Invalid feed URL", "url", rawFeedURL, "err", err)
		s.respondError(w, http.StatusBadRequest, errorCodeInvalidFeedURL)
		return
	}

	xmlStr, err := s.feedCache.GetOrLoad(feedURL, func() (string, error) {
		show, err := s.showClient.FetchShow(feedURL)
		if err != nil {
			return "", err
		}
		return rss.Build(show)
	})

	if err != nil {
		if validator.IsInvalidFeedURLError(err) {
			s.logger.Warn("Invalid feed URL", "url", feedURL, "err", err)
			s.respondError(w, http.StatusBadRequest, errorCodeInvalidFeedURL)
		} else if client.IsShowRetrievalError(err) {
			s.logger.Warn("Upstream request failed", "url", feedURL, "err", err)
			s.respondError(w, http.StatusBadGateway, errorCodeUpstreamUnavailable)
		} else if parser.IsShowParsingError(err) {
			s.logger.Error("Parsing feed failed", "url", feedURL, "err", err)
			s.respondError(w, http.StatusInternalServerError, errorCodeParsingFailed)
		} else {
			s.logger.Error("Unhandled error while rendering RSS feed", "url", feedURL, "err", err)
			s.respondError(w, http.StatusInternalServerError, errorCodeUnexpectedFailure)
		}
		return
	}

	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	fmt.Fprint(w, xmlStr)
}

func (s *server) respondError(w http.ResponseWriter, statusCode int, errorCode string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(statusCode)
	data := errorPageData{
		StatusCode: statusCode,
		ErrorCode:  errorCode,
	}
	if err := s.errorTmpl.Execute(w, data); err != nil {
		s.logger.Error("error template render error", "err", err)
	}
}

func resolvePort() int {
	if v := os.Getenv("AARA_PORT"); v != "" {
		if p, err := strconv.Atoi(v); err == nil && p > 0 {
			return p
		}
	}
	return defaultPort
}

func resolveCacheTTL() time.Duration {
	if v := os.Getenv("AARA_CACHE_TTL_SECONDS"); v != "" {
		if s, err := strconv.ParseInt(v, 10, 64); err == nil && s > 0 {
			return time.Duration(s) * time.Second
		}
	}
	return defaultCacheTTL
}
