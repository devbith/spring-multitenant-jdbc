#!/usr/bin/env bash

NAME=${1:-"default-postgres"}
PORT=${2:-5432}

docker ps -aq -f "name=${NAME}" | while read l; do docker rm -f $1; echo "stop ping $1"; done

docker run --name $NAME \
	-p ${PORT}:5432 \
	-e POSTGRES_USER=user \
	-e PGUSER=user \
	-e POSTGRES_DB=my-db \
	-e POSTGRES_PASSWORD=pw \
	postgres:14.12


