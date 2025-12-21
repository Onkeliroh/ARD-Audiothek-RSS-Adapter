FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ARG GRAALVM_VERSION=22.0.2
ARG GRAALVM_DIST=graalvm-community-jdk-${GRAALVM_VERSION}
ARG GRAALVM_ARCHIVE=${GRAALVM_DIST}_linux-x64_bin.tar.gz

ENV DEBIAN_FRONTEND=noninteractive

# Install tooling and GraalVM.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        unzip \
        git \
        maven \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/${GRAALVM_ARCHIVE} \
        https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/${GRAALVM_ARCHIVE} \
    && mkdir -p /opt/${GRAALVM_DIST} \
    && tar -xzf /tmp/${GRAALVM_ARCHIVE} -C /opt/${GRAALVM_DIST} --strip-components=1 \
    && rm /tmp/${GRAALVM_ARCHIVE}

ENV GRAALVM_HOME=/opt/${GRAALVM_DIST}
ENV JAVA_HOME=${GRAALVM_HOME}
ENV PATH=${JAVA_HOME}/bin:${PATH}

# Validate that native-image is bundled now that gu is gone (see oracle/graal#6855).
RUN test -x ${GRAALVM_HOME}/bin/native-image \
    && ${GRAALVM_HOME}/bin/native-image --version

# Maven local cache directory with open permissions for the vscode user.
RUN mkdir -p /home/vscode/.m2 && chown -R vscode:vscode /home/vscode/.m2
