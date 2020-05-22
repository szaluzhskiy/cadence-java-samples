#!/usr/bin/env bash

../gradlew :app-child:bootJar --stacktrace &&
docker build -t app/app-child:v1 .