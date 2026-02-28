# ARD Audiothek RSS Adapter

The ARD Audiothek RSS Adapter allows you to create RSS feeds for ARD Audiothek shows that do not provide their own feeds. It works by scraping the show page for episode information and generating a valid RSS 2.0 feed on the fly.

This tool is meant to be a simple, self-hostable service that can be used to create custom RSS feeds for ARD Audiothek content, enabling users to subscribe to shows in their preferred podcast apps even if the original show does not offer an RSS feed.

I use it in combination with [AudioBookShelf](https://github.com/advplyr/audiobookshelf).

The ARD Audiothek RSS Adapter uses a simple in-memory cache to store generated RSS feeds for a configurable duration (default 6 hours) to reduce load on the ARD Audiothek servers and improve response times for frequently accessed shows. You can configure the cache TTL via the `AARA_CACHE_TTL_SECONDS` environment variable.

## Stack

- Go (standard library: `net/http`, `html/template`, `encoding/xml`)
- [goquery](https://github.com/PuerkitoBio/goquery) for HTML parsing

The tool exposes the following HTTP endpoints:

1. `GET /` – Feed mapper UI (enter an Audiothek show URL and get the RSS feed link).
2. `GET /health` – Returns `ARD Audiothek RSS Adapter is running.`
3. `GET /rss/feed/{feedUrl...}` – Returns a valid RSS 2.0 feed filled with items fetched from the ARD Audiothek show page at `feedUrl`. The value must be a fully-qualified `https://www.ardaudiothek.de/…` URL. You can pass the raw URL (`/rss/feed/https://www.ardaudiothek.de/sendung/foo/…/`) or provide the percent-encoded variant.

## Configuration

| Environment variable      | Default       | Description                |
|---------------------------|---------------|----------------------------|
| `AARA_PORT`               | `8411`        | Port the server listens on |
| `AARA_CACHE_TTL_SECONDS`  | `21600` (6 h) | RSS cache TTL in seconds   |

## Building and Running

```bash
go build -o server ./cmd/server
./server
```

Or with Docker:

```bash
docker build -t ard-audiothek-rss-adapter .
docker run -p 8411:8411 ard-audiothek-rss-adapter
```

The server starts and listens on the configured port (default `8411`). Access the RSS feed at:

```
http://localhost:8411/rss/feed/https://www.ardaudiothek.de/sendung/<slug>/<urn>/
```

Replace the trailing portion with the exact Audiothek show URL (URL-encode it if your tooling cannot send raw URLs inside the path).

## Tests

```bash
go test -race ./...
```
