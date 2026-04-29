# GNU Make (Git Bash, MSYS2, WSL, Linux, macOS).
GO ?= go
# Pinned for reproducible CI/local lint; bump when upgrading Go toolchain.
STATICCHECK ?= honnef.co/go/tools/cmd/staticcheck@v0.7.0
PACKAGES ?= ./...
BINARY ?= server

.PHONY: help deps tidy lint lint-fix test cover build check ci docker-build clean

help:
	@echo "Targets:"
	@echo "  make deps         go mod download"
	@echo "  make tidy         go mod tidy"
	@echo "  make lint         go vet + staticcheck"
	@echo "  make test         go test -race"
	@echo "  make cover        coverage.out + coverage.html"
	@echo "  make build        go build -> $(BINARY)"
	@echo "  make check        lint, test, build (local CI)"
	@echo "  make ci           same as check"
	@echo "  make docker-build docker build (image ard-audiothek-rss-adapter)"
	@echo "  make clean        remove $(BINARY), coverage artifacts"

deps:
	$(GO) mod download

tidy:
	$(GO) mod tidy

lint:
	$(GO) vet $(PACKAGES)
	$(GO) run $(STATICCHECK) $(PACKAGES)

lint-fix:
	$(GO) fmt $(PACKAGES)
	$(GO) fix $(PACKAGES)

test:
	$(GO) test -race $(PACKAGES)

cover:
	$(GO) test -race $(PACKAGES) -coverprofile=coverage.out
	$(GO) tool cover -html=coverage.out -o coverage.html
	@echo "Coverage report: coverage.html"

build:
	$(GO) build -o $(BINARY) ./src/cmd/server

check: lint test build
ci: check

docker-build:
	docker build -t ard-audiothek-rss-adapter .

clean:
	rm -f $(BINARY) server.exe coverage.out coverage.html
