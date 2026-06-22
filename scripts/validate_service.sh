#!/bin/bash
# Validate Order Microservice deployment
# This script is called by AWS CodeDeploy to verify the deployment was successful

set -e

echo "=== Validating Order Microservice Deployment ==="

# Define variables
APP_URL="http://localhost:8080/api"
MAX_ATTEMPTS=30
ATTEMPT=0

# Function to check application health
check_health() {
  curl -f -s "$APP_URL/health" >/dev/null 2>&1
  return $?
}

# Function to check database connectivity
check_database() {
  curl -f -s "$APP_URL/customers" >/dev/null 2>&1
  return $?
}

# Wait for application to respond
echo "Checking application health endpoint..."
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if check_health; then
    echo "✓ Application health check passed"
    break
  fi
  echo "Waiting for application health check... (attempt $((ATTEMPT+1))/$MAX_ATTEMPTS)"
  sleep 1
  ATTEMPT=$((ATTEMPT+1))
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo "✗ Application health check failed"
  exit 1
fi

# Check database connectivity
echo "Checking database connectivity..."
if check_database; then
  echo "✓ Database connectivity verified"
else
  echo "✗ Database connectivity check failed (this might be expected on first deployment)"
fi

# Check Docker container is running
echo "Checking Docker container status..."
CONTAINER_ID=$(docker ps -q --filter "ancestor=orderms-repository:latest" | head -n 1)
if [ -n "$CONTAINER_ID" ]; then
  echo "✓ Docker container is running: $CONTAINER_ID"
else
  echo "⚠ No Docker container running (if using JAR deployment, this is OK)"
fi

# Check application logs for errors
echo "Checking application logs..."
if [ -f "/var/log/orderms/application.log" ]; then
  if grep -i "error\|exception" /var/log/orderms/application.log | head -n 5; then
    echo "⚠ Errors detected in logs (review manually)"
  else
    echo "✓ No critical errors in application logs"
  fi
fi

echo ""
echo "=== Deployment Validation Complete ==="
echo "Application is running at: $APP_URL"
exit 0
