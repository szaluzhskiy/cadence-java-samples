#!/usr/bin/env bash

../gradlew :app-parent:bootJar --stacktrace &&
docker build -t app/app-parent:v1 .