#!/bin/bash

if [ -z "$PAN_HOME" ]; then
    echo "The environment variable PAN_HOME is not set."
    exit 1
fi
PAN_HOME=${PAN_HOME%/}

echo "The system will use the configuration file: $PAN_HOME/etc/pan.properties"

# Start the Spring Boot application
java -Dpan.home="$PAN_HOME" -jar "$PAN_HOME/lib/app.jar"