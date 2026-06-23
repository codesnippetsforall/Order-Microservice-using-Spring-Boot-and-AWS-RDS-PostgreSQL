#!/usr/bin/env bash
# Run on EC2 as ubuntu (script uses sudo for systemd install).
# Usage:
#   chmod +x deploy/install-systemd.sh
#   ./deploy/install-systemd.sh          # JAR mode (recommended)
#   ./deploy/install-systemd.sh maven    # mvn spring-boot:run mode

set -euo pipefail

APP_DIR="/home/ubuntu/microservice/Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL"
MODE="${1:-jar}"
SERVICE_NAME="orderms"

if [[ ! -d "$APP_DIR" ]]; then
  echo "ERROR: App directory not found: $APP_DIR"
  exit 1
fi

cd "$APP_DIR"

if [[ "$MODE" == "maven" ]]; then
  UNIT_FILE="deploy/orderms-maven.service"
else
  UNIT_FILE="deploy/orderms.service"
  echo "Building JAR..."
  mvn clean package -DskipTests -q
  if [[ ! -f target/orderms-1.0.0.jar ]]; then
    echo "ERROR: target/orderms-1.0.0.jar not found after build"
    exit 1
  fi
fi

if [[ ! -f "$UNIT_FILE" ]]; then
  echo "ERROR: $UNIT_FILE not found. Pull latest code from git first."
  exit 1
fi

echo "Installing systemd unit from $UNIT_FILE ..."
sudo cp "$UNIT_FILE" "/etc/systemd/system/${SERVICE_NAME}.service"
sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}.service"
sudo systemctl restart "${SERVICE_NAME}.service"

echo ""
echo "Status:"
sudo systemctl status "${SERVICE_NAME}.service" --no-pager || true
echo ""
echo "Health (wait ~30s if just started):"
curl -sf "http://localhost:8020/api/actuator/health" && echo || echo "Not ready yet — check: journalctl -u orderms -f"
