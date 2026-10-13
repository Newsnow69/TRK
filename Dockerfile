FROM ubuntu:latest
LABEL authors="vitic"

ENTRYPOINT ["top", "-b"]