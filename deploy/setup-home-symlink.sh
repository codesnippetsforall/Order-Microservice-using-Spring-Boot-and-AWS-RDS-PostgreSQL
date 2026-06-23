#!/usr/bin/env bash
# One-time setup on EC2: symlink redeploy script to /home/ubuntu
#
# Usage:
#   cd /home/ubuntu/microservice/Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL
#   chmod +x deploy/setup-home-symlink.sh deploy/redeploy-orderms.sh
#   ./deploy/setup-home-symlink.sh
#
# Then from any SSH session:
#   ~/redeploy-orderms.sh
#   ~/redeploy-orderms.sh --nohup

set -euo pipefail

APP_DIR="/home/ubuntu/microservice/Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL"
SCRIPT="${APP_DIR}/deploy/redeploy-orderms.sh"
LINK="/home/ubuntu/redeploy-orderms.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "ERROR: $SCRIPT not found. git pull first."
  exit 1
fi

chmod +x "$SCRIPT"
ln -sf "$SCRIPT" "$LINK"

echo "Symlink created:"
echo "  $LINK -> $SCRIPT"
echo ""
echo "Run from home directory:"
echo "  ~/redeploy-orderms.sh"
echo "  ~/redeploy-orderms.sh --nohup    # legacy mvn spring-boot:run"
