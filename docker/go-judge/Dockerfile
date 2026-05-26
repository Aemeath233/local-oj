# syntax=docker/dockerfile:1.7

FROM python:3.12-slim AS python-base

FROM criyle/go-judge:v1.12.0

USER root

# Copy Python 3.12 from python-base to ensure precise Python 3.12 environment in sandbox
COPY --from=python-base /usr/local /usr/local
RUN ln -sf /usr/local/bin/python3 /usr/bin/python3 && ldconfig

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    set -eux; \
    sed -i \
        -e 's|http://deb.debian.org/debian-security|http://mirrors.aliyun.com/debian-security|g' \
        -e 's|http://deb.debian.org/debian|http://mirrors.aliyun.com/debian|g' \
        /etc/apt/sources.list.d/debian.sources; \
    rm -f /etc/apt/apt.conf.d/docker-clean; \
    printf 'Acquire::Retries "5";\nAcquire::http::Timeout "60";\nAcquire::https::Timeout "60";\nBinary::apt::APT::Keep-Downloaded-Packages "true";\n' > /etc/apt/apt.conf.d/80-retries; \
    retry_install() { \
        for attempt in 1 2 3 4 5 6 7 8; do \
            apt-get update; \
            if apt-get install -y --no-install-recommends "$@"; then \
                return 0; \
            fi; \
            sleep 5; \
        done; \
        return 1; \
    }; \
    retry_install ca-certificates; \
    sed -i \
        -e 's|http://mirrors.aliyun.com/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        -e 's|http://mirrors.aliyun.com/debian|https://mirrors.aliyun.com/debian|g' \
        /etc/apt/sources.list.d/debian.sources; \
    retry_install \
        gcc \
        g++ \
        openjdk-21-jdk-headless; \
    for cmd in gcc g++ java javac python3; do \
        if ! command -v "$cmd" >/dev/null; then \
            echo "missing runtime command: $cmd" >&2; \
            exit 1; \
        fi; \
    done; \
    rm -rf /var/lib/apt/lists/*
