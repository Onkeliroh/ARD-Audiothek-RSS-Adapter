# ARD Audiothek RSS Adapter

This project allows the use/client to access rss feeds that connect to the ARD Audiothek.

## Setup

- ktor, kotlin, maven

The tool is written in Kotlin using the Ktor framework and built with Maven.

The tool offers the following API endpoints:

1. GET `/rss/feed/{feedUrl...}` - Returns a valid RSS feed document filled with items fetched from the ARD Audiothek show page located at `feedUrl`. The value must be a fully-qualified `https://www.ardaudiothek.de/...` URL. Because the route captures the entire remainder of the path, you can pass the raw URL (`/rss/feed/https://www.ardaudiothek.de/sendung/foo/…/`) or provide the percent-encoded variant if preferred.

The tool offerst the following configuration options via either environment variables or a configuration file located at `~/.ardara.conf`:

- `PORT`: The port on which the server listens (default: `8411`)
- `CACHE_TTL_MINUTES`: The time-to-live for cached feed data in minutes (default: 6 hours = 360 minutes)
- `LOG_LEVEL`: The logging level for the application (default: `INFO`)
- `ARD_API_BASE_URL`: The base URL for the ARD Audiothek API (default: `https://api.ardmediathek.de/sendung`)

## Building and Running

To build the project, ensure you have Maven installed. Then run:

```bash
mvn clean package
```
To run the application, execute the following command:

```bash
java -jar target/ard-audiothek-rss-adapter.jar
```

The server will start and listen on the configured port (default: `8411`).
You can then access the RSS feed endpoint by navigating to:

```
http://localhost:8411/rss/feed/https://www.ardaudiothek.de/sendung/<slug>/<urn>/
```
Replace the trailing portion with the exact Audiothek show URL (URL-encode it if your tooling cannot send raw URLs inside the path).

## Coverage Report

To generate a code coverage report, run the following Maven command:

```bash
mvn clean verify jacoco:report
```

## Container Image

Use the Jib plugin to build and push an OCI image without a local Docker daemon:

```bash
mvn -Djib.to.image=ghcr.io/your-org/ard-audiothek-rss-adapter:dev jib:build
```

You can add extra tags with `-Djib.to.tags=latest` or supply credentials via the `JIB_TO_AUTH_USERNAME`/`JIB_TO_AUTH_PASSWORD` environment variables when publishing.

