#!/bin/bash
# Install dependencies for Order Microservice
# This script is called by AWS CodeDeploy during installation

set -e

echo "=== Installing Order Microservice Dependencies ==="

# Update system packages
echo "Updating system packages..."
apt-get update
apt-get upgrade -y

# Install Java 17 if not already installed
if ! command -v java &> /dev/null; then
  echo "Installing Java 17..."
  apt-get install -y openjdk-17-jre-headless
else
  echo "✓ Java is already installed"
  java -version
fi

# Install Docker if not already installed
if ! command -v docker &> /dev/null; then
  echo "Installing Docker..."
  apt-get install -y docker.io
  systemctl start docker
  systemctl enable docker
else
  echo "✓ Docker is already installed"
  docker --version
fi

# Install curl if not already installed
if ! command -v curl &> /dev/null; then
  echo "Installing curl..."
  apt-get install -y curl
fi

# Create application directory
APPLICATION_HOME="/opt/orderms"
if [ ! -d "$APPLICATION_HOME" ]; then
  mkdir -p "$APPLICATION_HOME"
fi

# Create application user if not exists
if ! id -u orderms >/dev/null 2>&1; then
  echo "Creating application user..."
  useradd -m -s /bin/bash -d "$APPLICATION_HOME" orderms
fi

# Create logs directory
LOG_DIR="/var/log/orderms"
mkdir -p "$LOG_DIR"
chown -R orderms:orderms "$LOG_DIR"

# Add orderms user to docker group
usermod -aG docker orderms || true

# Create environment file
mkdir -p /etc/orderms
cat > /etc/orderms/orderms.env << 'EOF'
# Order Microservice Environment Variables
AWS_REGION=ap-south-2
SPRING_PROFILES_ACTIVE=aws
LOG_DIR=/var/log/orderms
EOF

chmod 600 /etc/orderms/orderms.env
chown orderms:orderms /etc/orderms/orderms.env

# Install AWS CLI if not already installed
if ! command -v aws &> /dev/null; then
  echo "Installing AWS CLI..."
  apt-get install -y awscli
fi

# Create systemd service for the application
cat > /etc/systemd/system/orderms.service << 'EOF'
[Unit]
Description=Order Microservice Application
After=network.target

[Service]
Type=simple
User=orderms
WorkingDirectory=/opt/orderms
ExecStart=/opt/orderms/scripts/start_server.sh
ExecStop=/bin/kill -s TERM $MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd daemon
systemctl daemon-reload

echo "✓ All dependencies installed successfully"
exit 0
