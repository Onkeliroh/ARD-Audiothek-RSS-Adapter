# Copilot Instructions

## Big Picture Architecture
- This service is a small Go HTTP server (see `cmd/server/main.go`) exposing `GET /rss/feed/{feedUrl...}` plus a health endpoint and UI.
- Request flow: HTTP route → `ShowPageClient.FetchShow()` scrapes the public ARD Audiothek web page → `parser.Parse()` decodes the `__NEXT_DATA__` payload into `ShowDetails`/`EpisodeDetails` → `rss.Build()` turns that into an RSS 2.0 document.
- All episodes and shows use the lightweight domain layer in `internal/models/models.go`; treat these as immutable DTOs when passing between components.
- RSS serialization uses Go's `encoding/xml` with custom structs in `internal/rss/builder.go`.

## Key Conventions & Integration Points
- HTTP fetching lives exclusively in `client.ShowPageClient`; it expects callers to pass the exact Audiothek show URL (http/https) and returns the parsed HTML response.
- Parsing relies on goquery (jQuery-like for Go). `parser.Parse()` expects a `script#__NEXT_DATA__` element and returns `ShowParsingError` when structure changes; bubble that up to HTTP handlers which return 500.
- Images often contain `{width}` placeholders; the parser already rewrites them to `512`. Follow that pattern if you add more media variants.
- RSS descriptions are HTML fragments built inside `rss.buildDescription()`. Keep markup simple and sanitized—currently limited to paragraph tags, optional `<img>`, and a duration block.
- RSS enclosures are always generated when an audio URL exists, even without file size information (falls back to `length="0"`). This ensures compatibility with podcast players like Audiobookshelf.
- Default server config uses environment variables. `PORT` defaults to `8411`, `CACHE_TTL_SECONDS` defaults to `21600` (6 hours).

## Build, Run, Test
- Standard workflow: `go test -race ./...` for full test suite. Tests include `builder_test.go`, `parser_test.go`, `client_test.go`, and `server_test.go`.
- Local dev run: `go build -o server ./cmd/server && ./server` or `go run ./cmd/server`.
- Container image: `docker build -t ard-audiothek-rss-adapter .` builds a Docker image.
- Tests parse real-world HTML at `testdata/jagd-auf-fantomas.html`.

## When Extending Functionality
- Reuse the shared `http.Client` configured with a 30-second timeout.
- Return `ShowRetrievalError` for upstream HTTP failures and `ShowParsingError` for payload issues so handlers can return appropriate status codes (502 vs 500).
- Any new outbound requests should send realistic Accept/Accept-Language headers to avoid ARD blocking traffic.
- The service uses standard Go concurrency patterns; avoid blocking calls where possible.
- Add fixtures under `testdata/` and cover tricky parsing cases with focused tests.
- **Always update tests and documentation when making code changes** to keep everything in sync.
