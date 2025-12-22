# Copilot Instructions

## Big Picture Architecture
- This service is a small Ktor server (see `src/main/kotlin/de/ard/audiothek/Application.kt`) exposing `GET /rss/feed/{feedId}` plus a health root.
- Request flow: HTTP route → `ShowPageClient.fetchShow()` scrapes the public ARD Audiothek web page → `ArdShowPageParser.parse()` decodes the `__NEXT_DATA__` payload into `ShowDetails`/`EpisodeDetails` → `RssFeedBuilder.build()` turns that into an RSS 2.0 document.
- All episodes and shows use the lightweight domain layer in `src/main/kotlin/de/ard/audiothek/ard/ShowModels.kt`; treat these as immutable DTOs when passing between components.
- Rome (`com.rometools:rome`) handles RSS serialization, so prefer building `SyndFeedImpl`/`SyndEntryImpl` objects instead of manual XML.

## Key Conventions & Integration Points
- HTTP fetching lives exclusively in `ShowPageClient`; it normalizes feed IDs that arrive as URNs (e.g. `urn:ard:show:...`), canonical paths (`/sendung/foo`), or full URLs. Reuse `resolvePageUrl()` instead of duplicating URL math.
- Parsing relies on Jsoup + Jackson. `ArdShowPageParser` expects a `script#__NEXT_DATA__` element and throws `ShowParsingException` when structure changes; bubble that up to Ktor `StatusPages` so callers get a 500 instead of silent fallbacks.
- Images often contain `{width}` placeholders; the parser already rewrites them to `512`. Follow that pattern if you add more media variants.
- RSS descriptions are HTML fragments built inside `RssFeedBuilder`. Keep markup simple and sanitized—currently limited to paragraph tags, optional `<img>`, and a duration block.
- Default server config is in `src/main/resources/application.conf`. `ktor.deployment.port` honors the `PORT` env var automatically; add new settings here rather than sprinkling `System.getenv()` calls.

## Build, Run, Test
- Standard workflow: `mvn clean verify` for full compile + JUnit 5 suites (`ApplicationTest`, `ArdShowPageParserTest`). These tests spin up an in-memory Ktor app and parse the real-world HTML at `src/test/resources/Jagd auf Fantomas.html`.
- Local dev run: `mvn exec:java -Dexec.mainClass=de.ard.audiothek.ApplicationKt` or `java -jar target/ard-audiothek-rss-adapter-1.0-SNAPSHOT.jar` after packaging.
- Native image: `mvn -Pnative -DskipTests package` generates `target/native/ard-audiothek-rss-adapter`. Ensure your machine provides GraalVM + matching architecture before invoking the profile.

## When Extending Functionality
- Reuse the shared `HttpClient` defined in `Application.module()`; remember to close new resources via `ApplicationStopped` hooks.
- Throw `ShowRetrievalException` for upstream HTTP failures and `ShowParsingException` for payload issues so StatusPages keeps returning 502 vs 500 correctly.
- Any new outbound requests should send realistic Accept/Accept-Language headers to avoid ARD blocking traffic; copy the existing header set in `ShowPageClient`.
- Prefer suspending functions and Ktor coroutines instead of blocking calls; every endpoint currently runs on the default event loop.
- Add fixtures under `src/test/resources` and cover tricky parsing cases with focused tests similar to `ArdShowPageParserTest`.
