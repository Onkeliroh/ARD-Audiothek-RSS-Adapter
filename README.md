# ARD Audiothek RSS Adapter

This project allows the user/client to access RSS feeds that connect to the ARD Audiothek.

## Stack

- Go (standard library: `net/http`, `html/template`, `encoding/xml`)
- [goquery](https://github.com/PuerkitoBio/goquery) for HTML parsing

The tool exposes the following HTTP endpoints:

1. `GET /` – Feed mapper UI (enter an Audiothek show URL and get the RSS feed link).
2. `GET /health` – Returns `ARD Audiothek RSS Adapter is running.`
3. `GET /rss/feed/{feedUrl...}` – Returns a valid RSS 2.0 feed filled with items fetched from the ARD Audiothek show page at `feedUrl`. The value must be a fully-qualified `https://www.ardaudiothek.de/…` URL. You can pass the raw URL (`/rss/feed/https://www.ardaudiothek.de/sendung/foo/…/`) or provide the percent-encoded variant.

## Configuration

| Environment variable | Default | Description |
|---|---|---|
| `PORT` | `8411` | Port the server listens on |
| `CACHE_TTL_SECONDS` | `21600` (6 h) | RSS cache TTL in seconds |

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

