#!/usr/bin/env bash
# Provision Amazon ElastiCache Redis (cache.t4g.micro) and store connection in Secrets Manager.
#
# Usage: ./deploy/setup-elasticache-redis.sh
#
# Prerequisites: AWS CLI, EC2 and RDS in the same VPC (default VPC).

set -euo pipefail

REGION="${AWS_REGION:-ap-south-2}"
VPC_ID="${VPC_ID:-vpc-0792ec609a9916a37}"
EC2_SG_ID="${EC2_SG_ID:-sg-0182b5f5e37cd1a00}"
CLUSTER_ID="${REDIS_CLUSTER_ID:-winsoon-orderms-redis}"
SUBNET_GROUP="${REDIS_SUBNET_GROUP:-winsoon-redis-subnet-group}"
REDIS_SG_NAME="${REDIS_SG_NAME:-winsoon-redis-sg}"
SECRET_ID="${REDIS_SECRET_ID:-winsoon/orderms/redis}"
NODE_TYPE="${REDIS_NODE_TYPE:-cache.t4g.micro}"

SUBNETS=(
  "subnet-02e943fc170f4ee3f"
  "subnet-0fc3331b87674151f"
  "subnet-06b592c8a4a52938b"
)

echo "==> Region:      $REGION"
echo "==> VPC:         $VPC_ID"
echo "==> Cluster:     $CLUSTER_ID"
echo "==> Node type:   $NODE_TYPE"

echo "==> Step 1: Cache subnet group"
if ! aws elasticache describe-cache-subnet-groups \
    --cache-subnet-group-name "$SUBNET_GROUP" --region "$REGION" >/dev/null 2>&1; then
  aws elasticache create-cache-subnet-group \
    --region "$REGION" \
    --cache-subnet-group-name "$SUBNET_GROUP" \
    --cache-subnet-group-description "OrderMS ElastiCache subnets" \
    --subnet-ids "${SUBNETS[@]}"
  echo "    Created subnet group $SUBNET_GROUP"
else
  echo "    Subnet group $SUBNET_GROUP exists"
fi

echo "==> Step 2: Redis security group"
REDIS_SG_ID="$(aws ec2 describe-security-groups \
  --region "$REGION" \
  --filters "Name=group-name,Values=$REDIS_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"

if [[ -z "$REDIS_SG_ID" || "$REDIS_SG_ID" == "None" ]]; then
  REDIS_SG_ID="$(aws ec2 create-security-group \
    --region "$REGION" \
    --group-name "$REDIS_SG_NAME" \
    --description "ElastiCache Redis for OrderMS" \
    --vpc-id "$VPC_ID" \
    --query GroupId --output text)"
  aws ec2 authorize-security-group-ingress \
    --region "$REGION" \
    --group-id "$REDIS_SG_ID" \
    --protocol tcp \
    --port 6379 \
    --source-group "$EC2_SG_ID"
  echo "    Created security group $REDIS_SG_ID (6379 from EC2 SG $EC2_SG_ID)"
else
  echo "    Security group $REDIS_SG_ID exists"
fi

echo "==> Step 3: ElastiCache Redis cluster"
if ! aws elasticache describe-cache-clusters \
    --cache-cluster-id "$CLUSTER_ID" --region "$REGION" >/dev/null 2>&1; then
  aws elasticache create-cache-cluster \
    --region "$REGION" \
    --cache-cluster-id "$CLUSTER_ID" \
    --engine redis \
    --engine-version "7.1" \
    --cache-node-type "$NODE_TYPE" \
    --num-cache-nodes 1 \
    --cache-subnet-group-name "$SUBNET_GROUP" \
    --security-group-ids "$REDIS_SG_ID" \
    --tags Key=Name,Value=winsoon-orderms-redis Key=Service,Value=orderms
  echo "    Creating cluster (waiting for available, ~5-10 min)..."
  aws elasticache wait cache-cluster-available --region "$REGION" --cache-cluster-id "$CLUSTER_ID"
else
  echo "    Cluster $CLUSTER_ID exists"
  aws elasticache wait cache-cluster-available --region "$REGION" --cache-cluster-id "$CLUSTER_ID" 2>/dev/null || true
fi

REDIS_HOST="$(aws elasticache describe-cache-clusters \
  --region "$REGION" \
  --cache-cluster-id "$CLUSTER_ID" \
  --show-cache-node-info \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint.Address' \
  --output text)"

REDIS_PORT="$(aws elasticache describe-cache-clusters \
  --region "$REGION" \
  --cache-cluster-id "$CLUSTER_ID" \
  --show-cache-node-info \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint.Port' \
  --output text)"

echo "    Endpoint: ${REDIS_HOST}:${REDIS_PORT}"

SECRET_FILE="${SCRIPT_DIR}/redis-secret.json"
cat > "$SECRET_FILE" <<EOF
{
  "REDIS_HOST": "${REDIS_HOST}",
  "REDIS_PORT": "${REDIS_PORT}",
  "REDIS_SSL": "false",
  "REDIS_PASSWORD": "",
  "REDIS_CACHE_TTL_SECONDS": "300"
}
EOF

echo "==> Step 4: Secrets Manager ($SECRET_ID)"
if aws secretsmanager describe-secret --secret-id "$SECRET_ID" --region "$REGION" >/dev/null 2>&1; then
  aws secretsmanager put-secret-value \
    --region "$REGION" \
    --secret-id "$SECRET_ID" \
    --secret-string "file://${SECRET_FILE}"
  echo "    Updated secret $SECRET_ID"
else
  aws secretsmanager create-secret \
    --region "$REGION" \
    --name "$SECRET_ID" \
    --description "ElastiCache Redis connection settings for OrderMS" \
    --secret-string "file://${SECRET_FILE}"
  echo "    Created secret $SECRET_ID"
fi

rm -f "$SECRET_FILE"

echo ""
echo "==> Done."
echo "    Redis:  ${REDIS_HOST}:${REDIS_PORT}"
echo "    Secret: $SECRET_ID"
echo ""
echo "Next: redeploy OrderMS on EC2 (~/redeploy-orderms.sh)"
