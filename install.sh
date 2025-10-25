#!/bin/bash

set -e

if [ -z "$PAN_HOME" ]; then
    echo "The environment variable PAN_HOME is not set."
    exit 1
fi

PAN_HOME="${PAN_HOME%/}"

echo "PAN_HOME is set to: $PAN_HOME"

mvn clean package -DskipTests

INSTALL_BIN="$PAN_HOME/bin"
INSTALL_ETC="$PAN_HOME/etc"
INSTALL_LIB="$PAN_HOME/lib"
INSTALL_LOG="$PAN_HOME/log"
mkdir -p "$INSTALL_BIN" "$INSTALL_ETC" "$INSTALL_LIB" "$INSTALL_LOG"

JAR_NAME=$(ls target/*.jar | grep -v 'sources' | head -n 1)

if [ -f "$JAR_NAME" ]; then
    cp "$JAR_NAME" "$INSTALL_LIB/app.jar"
    echo "JAR deployed successfully to $INSTALL_LIB/app.jar"
else
    echo "Compilation failed, the JAR file was not found."
    exit 1
fi

CONFIG_SRC="src/main/resources/pan.properties"
CONFIG_DEST="$INSTALL_ETC/pan.properties"

if [ ! -f "$CONFIG_SRC" ]; then
    echo "Default configuration file not found at $CONFIG_SRC"
    exit 1
fi

if [ -f "$CONFIG_DEST" ]; then
    read -r -p "Overwrite configuration file? (Y/n) " -n 1 response
    echo

    response=$(echo "$response" | tr '[:upper:]' '[:lower:]')

    if [[ "$response" == "y" || "$response" == "" ]]; then
        cp "$CONFIG_SRC" "$CONFIG_DEST"
        echo "Default configuration file copied to $CONFIG_DEST"
    fi
else
    cp "$CONFIG_SRC" "$CONFIG_DEST"
    echo "Default configuration file copied to $CONFIG_DEST"
fi

START_SCRIPT_SRC="script/start.sh"
START_SCRIPT_DESC="$INSTALL_BIN/start.sh"

if [ -f "$START_SCRIPT_SRC" ]; then
    cp "$START_SCRIPT_SRC" "$START_SCRIPT_DESC"
    chmod +x "$START_SCRIPT_DESC"
    echo "Start script copied to $START_SCRIPT_DESC"
else
    echo "Start script not found at $START_SCRIPT_SRC"
    exit 1
fi

echo "Use $START_SCRIPT_DESC to start the application."