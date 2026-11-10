FROM ubuntu:stonking

RUN apt update && \
    DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends mysql-server && \
    rm -rf /var/lib/apt/lists/*
