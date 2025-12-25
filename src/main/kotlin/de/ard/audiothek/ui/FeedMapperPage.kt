package de.ard.audiothek.ui

import kotlinx.html.*

fun HTML.feedMapperPage() {
    head {
        meta { charset = "UTF-8" }
        meta {
            attributes["name"] = "viewport"
            content = "width=device-width, initial-scale=1"
        }
        title { +"ARD Audiothek RSS Mapper" }
        sharedStyles()
    }
    body {
        main {
            h1 { +"ARD Audiothek RSS Mapper" }
            p {
                +"Paste any Audiothek show link, canonical path, or URN to get the matching RSS feed exposed by this service."
            }
            label {
                attributes["for"] = "audiothek-input"
                +"Audiothek Link or URN"
            }
            div(classes = "form-row") {
                input(InputType.text) {
                    id = "audiothek-input"
                    placeholder = "Example: urn:ard:show:de76181e5527c837"
                    attributes["autocomplete"] = "off"
                    attributes["spellcheck"] = "false"
                }
                button(type = ButtonType.button) {
                    id = "map-button"
                    attributes["aria-label"] = "Generate RSS link"
                    +"Map"
                }
            }
            div(classes = "status") {
                id = "status"
                +"Paste a value and press enter to start."
            }
            section(classes = "result-card is-hidden") {
                id = "result"
                attributes["aria-live"] = "polite"
                span { +"RSS Feed" }
                a(href = "", target = "_blank") {
                    id = "result-link"
                    attributes["rel"] = "noopener noreferrer"
                    +"link"
                }
            }
            section(classes = "examples") {
                label { +"Examples" }
                button(type = ButtonType.button) {
                    attributes["data-sample"] = "urn:ard:show:de76181e5527c837"
                    +"URN"
                }
                button(type = ButtonType.button) {
                    attributes["data-sample"] =
                        "https://www.ardaudiothek.de/sendung/grosse-geschichten-ard-literatur-hoerspiele/urn:ard:show:de76181e5527c837/"
                    +"Full URL"
                }
                button(type = ButtonType.button) {
                    attributes["data-sample"] =
                        "grosse-geschichten-ard-literatur-hoerspiele/urn:ard:show:de76181e5527c837"
                    +"Path"
                }
            }
        }
        script {
            unsafe {
                raw(
                    """
                    (function () {
                        const input = document.getElementById("audiothek-input");
                        const mapButton = document.getElementById("map-button");
                        const status = document.getElementById("status");
                        const resultCard = document.getElementById("result");
                        const resultLink = document.getElementById("result-link");
                        const exampleButtons = document.querySelectorAll(".examples button[data-sample]");

                        // This might be an issue in the future. The base URL should ideally be configurable or use the host ip address.
                        const baseUrl = `${'$'}{window.location.origin}/rss/feed/`;

                        function renderResult() {
                            const rawValue = input.value.trim();
                            if (!rawValue) {
                                status.textContent = "Please paste a valid Audiothek link or URN.";
                                resultCard.classList.add("is-hidden");
                                return;
                            }

                            const encodedValue = encodeURIComponent(rawValue);
                            const finalUrl = `${'$'}{baseUrl}${'$'}{encodedValue}`;

                            resultLink.href = finalUrl;
                            resultLink.textContent = finalUrl;
                            resultCard.classList.remove("is-hidden");
                            status.textContent = "Link ready. Click below to open the RSS feed.";
                        }

                        mapButton.addEventListener("click", renderResult);
                        input.addEventListener("keydown", (event) => {
                            if (event.key === "Enter") {
                                event.preventDefault();
                                renderResult();
                            }
                        });

                        exampleButtons.forEach((button) => {
                            button.addEventListener("click", () => {
                                input.value = button.dataset.sample || "";
                                renderResult();
                                input.focus();
                            });
                        });
                    })();
                    """.trimIndent()
                )
            }
        }
    }
}

fun HTML.errorPage(statusCode: Int, message: String) {
    head {
        meta { charset = "UTF-8" }
        meta {
            attributes["name"] = "viewport"
            content = "width=device-width, initial-scale=1"
        }
        title { +"ARD Audiothek RSS Mapper | Error" }
        sharedStyles()
    }
    body {
        main {
            h1 { +"Our RSS rocket hit turbulence" }
            p {
                +"The feed you requested tripped our parsing crew, so we had to abort the mission."
            }
            div(classes = "status") {
                span { +"Status $statusCode" }
            }
            section(classes = "result-card") {
                span { +"What you can do" }
                p {
                    +message
                }
                button(type = ButtonType.button) {
                    attributes["onclick"] = "window.location='/'"
                    +"Back to mapper"
                }
            }
        }
    }
}

private fun HEAD.sharedStyles() {
    style {
        unsafe {
            raw(
                """
                :root {
                    color-scheme: light;
                    --bg-gradient: linear-gradient(135deg, #001a4d, #00357a, #0b6cff);
                    --card-bg: rgba(255, 255, 255, 0.08);
                    --card-border: rgba(255, 255, 255, 0.25);
                    --accent: #ffca28;
                    --text-primary: #f4f6fb;
                    --text-muted: #b9c3e6;
                    --input-bg: rgba(255, 255, 255, 0.15);
                    --font-sans: "Segoe UI", "Helvetica Neue", "Liberation Sans", sans-serif;
                }

                * {
                    box-sizing: border-box;
                }

                body {
                    margin: 0;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 2rem;
                    font-family: var(--font-sans);
                    background: var(--bg-gradient);
                    color: var(--text-primary);
                }

                main {
                    width: min(640px, 100%);
                    background: var(--card-bg);
                    border: 1px solid var(--card-border);
                    border-radius: 24px;
                    padding: 2.5rem;
                    backdrop-filter: blur(12px);
                    box-shadow: 0 25px 60px rgba(0, 14, 56, 0.4);
                    animation: floatIn 500ms ease;
                }

                @keyframes floatIn {
                    from {
                        transform: translateY(30px);
                        opacity: 0;
                    }
                    to {
                        transform: translateY(0);
                        opacity: 1;
                    }
                }

                h1 {
                    margin-top: 0;
                    margin-bottom: 0.5rem;
                    font-weight: 600;
                    font-size: clamp(1.9rem, 3vw, 2.4rem);
                }

                p {
                    margin-top: 0;
                    margin-bottom: 1.5rem;
                    line-height: 1.6;
                    color: var(--text-muted);
                }

                .form-row {
                    display: flex;
                    gap: 0.75rem;
                    flex-wrap: wrap;
                }

                label {
                    font-size: 0.85rem;
                    letter-spacing: 0.04em;
                    text-transform: uppercase;
                    color: var(--accent);
                }

                input[type="text"] {
                    flex: 1;
                    min-width: 240px;
                    padding: 0.95rem 1.1rem;
                    border-radius: 16px;
                    border: 1px solid transparent;
                    background: var(--input-bg);
                    color: var(--text-primary);
                    font-size: 1rem;
                    transition: border-color 150ms ease, background 150ms ease;
                }

                input[type="text"]:focus {
                    outline: none;
                    border-color: var(--accent);
                    background: rgba(255, 255, 255, 0.22);
                }

                button {
                    padding: 0.95rem 1.5rem;
                    border: none;
                    border-radius: 16px;
                    background: var(--accent);
                    color: #0c1a3c;
                    font-weight: 600;
                    font-size: 1rem;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    cursor: pointer;
                    transition: transform 120ms ease, box-shadow 120ms ease;
                }

                button:active {
                    transform: scale(0.98);
                }

                button:hover {
                    box-shadow: 0 10px 20px rgba(255, 202, 40, 0.35);
                }

                .status {
                    margin-top: 1rem;
                    min-height: 1.25rem;
                    font-size: 0.95rem;
                    color: var(--text-muted);
                }

                .result-card {
                    margin-top: 1.5rem;
                    padding: 1.5rem;
                    border-radius: 18px;
                    border: 1px solid rgba(255, 255, 255, 0.25);
                    background: rgba(12, 26, 60, 0.6);
                    display: flex;
                    flex-direction: column;
                    gap: 0.5rem;
                    transition: opacity 200ms ease;
                }

                .result-card.is-hidden {
                    opacity: 0;
                    pointer-events: none;
                }

                .result-card span {
                    font-size: 0.85rem;
                    letter-spacing: 0.04em;
                    text-transform: uppercase;
                    color: var(--text-muted);
                }

                .result-card a {
                    word-break: break-all;
                    color: var(--accent);
                    font-size: 1rem;
                    text-decoration: none;
                }

                .result-card a:hover {
                    text-decoration: underline;
                }

                .examples {
                    margin-top: 2rem;
                    display: grid;
                    gap: 0.45rem;
                }

                .examples button {
                    background: rgba(255, 255, 255, 0.15);
                    color: var(--text-primary);
                    text-transform: none;
                    font-size: 0.9rem;
                    letter-spacing: normal;
                    padding: 0.8rem 1rem;
                }

                @media (max-width: 540px) {
                    body {
                        padding: 1.25rem;
                    }

                    main {
                        padding: 1.75rem;
                    }

                    .form-row {
                        flex-direction: column;
                    }

                    button {
                        width: 100%;
                    }
                }
                """.trimIndent()
            )
        }
    }
}
