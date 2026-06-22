#!/bin/bash
# Stop the Order Microservice application
# This script is called by AWS CodeDeploy before deployment

set -e

echo "=== Stopping Order Microservice ==="

# Define variables
JAR_NAME="orderms-app.jar"
PID_FILE="/var/run/orderms.pid"
LOG_DIR="/var/log/orderms"

# Check if PID file exists and kill the process
if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if ps -p "$PID" > /dev/null 2>&1; then
    echo "Stopping process with PID: $PID"
    kill "$PID"
    
    # Wait for graceful shutdown
    echo "Waiting for graceful shutdown..."
    sleep 5
    
    # Force kill if still running
    if ps -p "$PID" > /dev/null 2>&1; then
      echo "Force killing process..."
      kill -9 "$PID"
    fi
  fi
  rm -f "$PID_FILE"
fi

# Also try to kill by process name
PIDS=$(pgrep -f "java -jar $JAR_NAME" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
  echo "Killing running instances: $PIDS"
  echo "$PIDS" | xargs kill -9 || true
fi

# Stop Docker containers if running
docker ps -q --filter "ancestor=orderms-repository:latest" | xargs -r docker stop
docker ps -a -q --filter "ancestor=orderms-repository:latest" | xargs -r docker rm

echo "✓ Application stopped successfully"
exit 0
