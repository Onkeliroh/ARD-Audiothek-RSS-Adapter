FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ENV DEBIAN_FRONTEND=noninteractive

# Install tooling and OpenJDK 21.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        unzip \
        git \
        maven \
        openjdk-21-jdk \
        docker.io \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=${JAVA_HOME}/bin:${PATH}

# Allow the vscode user to talk to the host Docker daemon and prep Maven cache.
RUN groupadd -f docker \
    && usermod -aG docker vscode \
    && mkdir -p /home/vscode/.m2 \
    && chown -R vscode:vscode /home/vscode/.m2
