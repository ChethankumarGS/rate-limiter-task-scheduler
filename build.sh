#!/bin/sh
set -e
rm -rf out && mkdir -p out
javac -d out src/main/java/rl/*.java src/test/java/rl/*.java
