# ARD Audiothek RSS Adapter

This project allows the use/client to access rss feeds that connect to the ARD Audiothek.

## Setup

- ktor, kotlin, maven, graalvm

The tool is wirten in kotlin using the ktor framework and build with maven and graalvm for native image support.

The tool offers the following API endpoints:

1. GET `/rss/feed/{feedId}` - Returns a valid RSS feed document filled with items fetched from the ARD Audiothek for the given feedId. The feedId can be the ARD Audiothek URN identifier of productions (e.g. `urn:ard:show:ef3205b54d97da0e`), the canonical `/sendung/...` path, or a full page URL. The adapter scrapes the public show page, parses the embedded `__NEXT_DATA__` payload, and transforms every listed episode into an RSS item containing title, summary, publication timestamp, runtime, and the playable audio URL.

The tool offerst the following configuration options via either environment variables or a configuration file located at `~/.ardara.conf`:

- `PORT`: The port on which the server listens (default: `8411`)
- `CACHE_TTL_MINUTES`: The time-to-live for cached feed data in minutes (default: 6 hours = 360 minutes)
- `LOG_LEVEL`: The logging level for the application (default: `INFO`)
- `ARD_API_BASE_URL`: The base URL for the ARD Audiothek API (default: `https://api.ardmediathek.de/sendung`)

## Building and Running

To build the project, ensure you have Maven and GraalVM installed. Then run:

```bash
mvn clean package
```
To run the application, execute the following command:

```bash
java -jar target/ard-audiothek-rss-adapter-1.0-SNAPSHOT.jar
```

The server will start and listen on the configured port (default: `8411`).
You can then access the RSS feed endpoint by navigating to:

```
http://localhost:8411/rss/feed/{feedId}
```
Replace `{feedId}` with the desired ARD Audiothek URN identifier.

## Coverage Report

To generate a code coverage report, run the following Maven command:

```bash
mvn clean verify jacoco:report
```

### Native Build (GraalVM)

To produce a Linux x86_64 native executable, install GraalVM and run:

```
mvn -Pnative -DskipTests package
```

The resulting binary is written to `target/native/ard-audiothek-rss-adapter`. Ensure your build machine matches the target architecture (Intel/AMD 64-bit Linux) when invoking the native profile.

