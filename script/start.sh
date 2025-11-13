#!/bin/bash

if [ -z "$PAN_HOME" ]; then
    echo "The environment variable PAN_HOME is not set."
    exit 1
fi
PAN_HOME=${PAN_HOME%/}

echo "The system will use the configuration file: $PAN_HOME/etc/pan.properties"

JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8"

# Start the Spring Boot application
exec java $JAVA_OPTS -Dpan.home="$PAN_HOME" -jar "$PAN_HOME/lib/app.jar"