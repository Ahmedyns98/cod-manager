#!/bin/sh
# Maven wrapper for Linux and macOS. Same idea as mvnw.cmd: download Maven once
# into ~/.m2/wrapper, then delegate to it.
set -e

MAVEN_VERSION=3.9.9
WRAPPER_HOME="$HOME/.m2/wrapper"
MAVEN_HOME="$WRAPPER_HOME/apache-maven-$MAVEN_VERSION"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "Downloading Maven $MAVEN_VERSION once into $WRAPPER_HOME ..."
  mkdir -p "$WRAPPER_HOME"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" \
    | tar -xz -C "$WRAPPER_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
