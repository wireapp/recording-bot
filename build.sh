#!/usr/bin/env bash
docker build -t $DOCKER_USERNAME/recording-bot:1.0.0 .
docker push $DOCKER_USERNAME/recording-bot
kubectl delete pod -l name=recording -n prod
kubectl get pods -l name=recording -n prod

