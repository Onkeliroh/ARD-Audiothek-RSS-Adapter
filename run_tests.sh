#!/bin/bash

set -euo pipefail

go test -v ./... -coverprofile=coverage.out
go tool cover -html=coverage.out -o coverage.html
echo "Coverage report generated: coverage.html"
