#!/usr/bin/env bash
TAG=1.0.1
NAME=recording-bot

docker build -t $DOCKER_USERNAME/$NAME:$TAG .
docker push $DOCKER_USERNAME/$NAME:$TAG

