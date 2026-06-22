#!/bin/bash
# Start the Order Microservice application
# This script is called by AWS CodeDeploy after installation

set -e

echo "=== Starting Order Microservice ==="

# Source environment variables
if [ -f /etc/orderms/orderms.env ]; then
  source /etc/orderms/orderms.env
fi

# Define variables
APPLICATION_HOME="/opt/orderms"
JAR_FILE="orderms-app.jar"
SERVICE_USER="orderms"
LOG_DIR="/var/log/orderms"
PID_FILE="/var/run/orderms.pid"

# Ensure logs directory exists
mkdir -p "$LOG_DIR"
chown -R "$SERVICE_USER:$SERVICE_USER" "$LOG_DIR"

# Navigate to application directory
cd "$APPLICATION_HOME"

# Set environment variables from AWS Secrets Manager and Parameter Store
export SPRING_DATASOURCE_URL=$(aws ssm get-parameter --name "/orderms/rds/db-url" --query 'Parameter.Value' --output text)
export SPRING_DATASOURCE_USERNAME=$(aws ssm get-parameter --name "/orderms/rds/db-username" --query 'Parameter.Value' --output text)
export SPRING_DATASOURCE_PASSWORD=$(aws ssm get-parameter --name "/orderms/rds/db-password" --with-decryption --query 'Parameter.Value' --output text)
export SPRING_PROFILES_ACTIVE=$(aws ssm get-parameter --name "/orderms/app/spring-profiles" --query 'Parameter.Value' --output text)
export AWS_REGION="ap-south-2"

# Java options for container environments
export JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintFlagsFinal -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"

# Start the application as the service user
echo "Starting application with user: $SERVICE_USER"
sudo -u "$SERVICE_USER" bash -c "cd $APPLICATION_HOME && nohup java -jar $JAR_FILE > $LOG_DIR/application.log 2>&1 &" 

# Get the PID
sleep 2
PID=$(pgrep -f "java -jar $JAR_FILE" | head -n 1)

if [ -z "$PID" ]; then
  echo "ERROR: Failed to start application"
  exit 1
fi

echo $PID > "$PID_FILE"
echo "Application started successfully with PID: $PID"

# Wait for application to be ready
echo "Waiting for application to be ready..."
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
  if curl -f http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "✓ Application is ready"
    exit 0
  fi
  echo "Waiting for application... (attempt $((attempt+1))/$max_attempts)"
  sleep 1
  attempt=$((attempt+1))
done

echo "ERROR: Application failed to start within timeout"
exit 1
