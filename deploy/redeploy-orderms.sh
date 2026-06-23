#!/usr/bin/env bash
# Redeploy OrderMS on EC2: git pull → stop → build → start
#
# Usage (from anywhere after setup-home-symlink.sh):
#   redeploy-orderms.sh              # systemd + JAR (recommended if orderms service installed)
#   redeploy-orderms.sh --systemd    # same as default
#   redeploy-orderms.sh --nohup      # legacy: nohup mvn spring-boot:run
#
# One-time symlink:
#   ./deploy/setup-home-symlink.sh

set -euo pipefail

APP_DIR="/home/ubuntu/microservice/Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL"
SERVICE_NAME="orderms"
PORT="${APPLICATION_PORT:-8020}"
HEALTH_URL="http://localhost:${PORT}/api/actuator/health"
MODE="systemd"

usage() {
  echo "Usage: $(basename "$0") [--systemd|--nohup]"
  echo "  --systemd  git pull, mvn package, systemctl restart orderms (default)"
  echo "  --nohup    git pull, kill process, nohup mvn spring-boot:run"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --systemd) MODE="systemd"; shift ;;
    --nohup)   MODE="nohup"; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

if [[ ! -d "$APP_DIR" ]]; then
  echo "ERROR: App directory not found: $APP_DIR"
  exit 1
fi

cd "$APP_DIR"

echo "==> Pulling latest code..."
git pull

stop_orderms() {
  echo "==> Stopping OrderMS..."

  if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files "${SERVICE_NAME}.service" &>/dev/null; then
    if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
      sudo systemctl stop "${SERVICE_NAME}"
      echo "    Stopped systemd service: ${SERVICE_NAME}"
    fi
  fi

  # Legacy nohup / manual mvn processes
  if pgrep -f "${APP_DIR}.*spring-boot:run" >/dev/null 2>&1; then
    pkill -f "${APP_DIR}.*spring-boot:run" || true
    sleep 2
    echo "    Stopped mvn spring-boot:run"
  fi

  if pgrep -f "${APP_DIR}/target/orderms-.*\\.jar" >/dev/null 2>&1; then
    pkill -f "${APP_DIR}/target/orderms-.*\\.jar" || true
    sleep 2
    echo "    Stopped java -jar process"
  fi
}

start_systemd() {
  if [[ ! -f deploy/orderms.service ]]; then
    echo "ERROR: deploy/orderms.service not found. Run git pull or use --nohup."
    exit 1
  fi

  echo "==> Building JAR..."
  mvn clean package -DskipTests -q

  if [[ ! -f target/orderms-1.0.0.jar ]]; then
    echo "ERROR: target/orderms-1.0.0.jar not found after build"
    exit 1
  fi

  if ! systemctl list-unit-files "${SERVICE_NAME}.service" &>/dev/null; then
    echo "==> Installing systemd unit (first time)..."
    sudo cp deploy/orderms.service "/etc/systemd/system/${SERVICE_NAME}.service"
    sudo systemctl daemon-reload
    sudo systemctl enable "${SERVICE_NAME}"
  fi

  echo "==> Starting via systemd..."
  sudo systemctl restart "${SERVICE_NAME}"
  sudo systemctl status "${SERVICE_NAME}" --no-pager || true
}

start_nohup() {
  echo "==> Starting via nohup mvn spring-boot:run..."
  mkdir -p logs
  nohup mvn spring-boot:run -DskipTests >> logs/application.log 2>&1 &
  echo "    PID: $!"
  echo "    Log: ${APP_DIR}/logs/application.log"
}

wait_for_health() {
  echo "==> Waiting for health check (up to 90s)..."
  for _ in $(seq 1 30); do
    if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
      echo "    Health: UP — $HEALTH_URL"
      return 0
    fi
    sleep 3
  done
  echo "    WARN: Health check not ready yet. Check logs."
  return 1
}

stop_orderms

if [[ "$MODE" == "nohup" ]]; then
  start_nohup
else
  if command -v systemctl >/dev/null 2>&1; then
    start_systemd
  else
    echo "WARN: systemctl not found, falling back to --nohup"
    start_nohup
  fi
fi

wait_for_health || true

echo ""
echo "Done. Mode: $MODE"
