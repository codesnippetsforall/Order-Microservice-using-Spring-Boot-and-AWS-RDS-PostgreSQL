# OrderMS — AWS Infrastructure Setup Guide

This document is a **standalone reference** for provisioning and operating the AWS resources that host **OrderMS** (Spring Boot order microservice). It explains **what each service does**, **how components connect**, and **AWS CLI commands** to create or verify resources.

**No secrets, passwords, or account-specific IDs appear here.** Replace every `<placeholder>` with your own values. Store credentials in a password manager or AWS Secrets Manager — never in source control.

---

## Table of contents

1. [Architecture overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Recommended setup order](#recommended-setup-order)
4. [Networking (VPC, subnets, security groups)](#networking-vpc-subnets-security-groups)
5. [IAM roles and policies](#iam-roles-and-policies)
6. [Amazon RDS (PostgreSQL)](#amazon-rds-postgresql)
7. [AWS Secrets Manager](#aws-secrets-manager)
8. [Amazon EC2 (application host)](#amazon-ec2-application-host)
9. [Amazon Cognito (authentication)](#amazon-cognito-authentication)
10. [AWS Lambda (Cognito Pre Token Generation)](#aws-lambda-cognito-pre-token-generation)
11. [Amazon SQS (order events)](#amazon-sqs-order-events)
12. [Amazon ElastiCache (Redis cache)](#amazon-elasticache-redis-cache)
13. [Amazon API Gateway (optional front door)](#amazon-api-gateway-optional-front-door)
14. [Deploy OrderMS on EC2](#deploy-orderms-on-ec2)
15. [Verification checklist](#verification-checklist)
16. [Troubleshooting](#troubleshooting)

---

## Architecture overview

OrderMS runs as a Spring Boot application on **EC2**. It connects to **RDS PostgreSQL** for persistence, optionally **ElastiCache Redis** for caching, and publishes domain events to **SQS**. Configuration and credentials are loaded from **Secrets Manager** at startup. **Cognito** issues JWT access tokens; OrderMS validates them as an OAuth2 resource server. A **Lambda** function enriches tokens with custom scopes before they reach the API.

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                     AWS (e.g. ap-south-2)                  │
                    │                                                          │
  Client / Postman  │   ┌──────────────┐         ┌─────────────────────────┐  │
        │           │   │ API Gateway  │────────▶│ EC2 (OrderMS :8020)     │  │
        │           │   │ (optional)   │         │  Spring Boot + systemd  │  │
        └───────────┼──▶│              │         └───────┬────────┬────────┘  │
                    │   └──────────────┘                 │        │           │
                    │                                    │        │           │
                    │   ┌──────────────┐    JWT/JWKS     │        │           │
                    │   │   Cognito    │◀───────────────┘        │           │
                    │   │  User Pool   │                         │           │
                    │   └──────┬───────┘                         │           │
                    │          │ Pre Token                       │           │
                    │          ▼                                 │           │
                    │   ┌──────────────┐                       │           │
                    │   │   Lambda     │                       │           │
                    │   └──────────────┘                       │           │
                    │                                          │           │
                    │   ┌──────────────┐   ┌──────────┐  ┌─────▼─────┐     │
                    │   │   Secrets    │   │   RDS    │  │    SQS    │     │
                    │   │   Manager    │   │ Postgres │  │ FIFO queue│     │
                    │   └──────────────┘   └──────────┘  └───────────┘     │
                    │          ▲                                            │
                    │          │                                            │
                    │   ┌──────┴───────┐   ┌──────────┐                    │
                    │   │ ElastiCache  │   │   IAM    │                    │
                    │   │ Redis (opt.) │   │ instance │                    │
                    │   └──────────────┘   │   role   │                    │
                    │                      └──────────┘                    │
                    └─────────────────────────────────────────────────────────┘
```

| Service | Purpose in OrderMS |
|---------|-------------------|
| **EC2** | Runs the JAR; exposes HTTP on port `8020` (`/api` context path) |
| **RDS PostgreSQL** | Primary database (`orders`, `customers`, etc.) |
| **Secrets Manager** | Database, SQS, Cognito, Redis settings — no hardcoded secrets in code |
| **Cognito** | Identity provider; issues signed JWT access tokens |
| **Lambda** | Adds `orderms/read`, `orderms/write`, `orderms/admin` scopes to tokens (by group) |
| **SQS (FIFO)** | Async order events (`ORDER_CREATED`, `ORDER_STATUS_CHANGED`, …) |
| **ElastiCache Redis** | Optional response/cache layer (TTL configurable) |
| **API Gateway** | Optional HTTPS entry point, rate limiting, Cognito JWT authorizer |
| **IAM** | EC2 instance role grants least-privilege access to Secrets Manager, SQS, etc. |

**Default region used in project config:** `ap-south-2` (override with `AWS_REGION`).

---

## Prerequisites

### Tools

| Tool | Purpose |
|------|---------|
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | All commands in this guide |
| `jq` | Parse JSON from CLI output |
| `zip` | Package Cognito Pre Token Lambda |
| Git, Java 17, Maven 3.9+ | Build OrderMS on EC2 |
| `curl` | Health checks and token tests |

### AWS CLI configuration

```bash
aws configure
# AWS Access Key ID:     <your-access-key>   (or use SSO / instance role)
# AWS Secret Access Key: <your-secret-key>
# Default region:        ap-south-2
# Default output format: json
```

Verify:

```bash
export AWS_REGION=ap-south-2
aws sts get-caller-identity
```

Capture your account ID for later commands:

```bash
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account: $AWS_ACCOUNT_ID"
```

### Naming convention (recommended)

| Resource | Example name |
|----------|--------------|
| User pool | `winsoon-orderms-pool` |
| Cognito domain prefix | `winsoon-orderms-dev` |
| App client | `orderms-dev-client` |
| RDS instance | `winsoon-orderms-db` |
| EC2 instance | `Winsoon-OrderMS` |
| SQS queue | `orderms-events.fifo` |
| Lambda | `winsoon-cognito-pretoken-orderms` |
| Secrets | `winsoon/orderms/postgresql`, `winsoon/orderms/sqs`, `winsoon/orderms/cognito`, `winsoon/orderms/redis` |

---

## Recommended setup order

Provision resources in this sequence to avoid dependency errors:

| Step | Service | Why first |
|------|---------|-----------|
| 1 | VPC, subnets, security groups | RDS, EC2, Redis need network placement |
| 2 | IAM roles | EC2 instance profile before launch |
| 3 | RDS PostgreSQL | Database must exist before app starts |
| 4 | Secrets Manager (postgresql) | App reads DB credentials at startup |
| 5 | SQS + secret | Event publishing configuration |
| 6 | Cognito (user pool → domain → scopes → client → user) | Auth foundation |
| 7 | Cognito secret in Secrets Manager | Spring OAuth2 issuer config |
| 8 | Lambda + Cognito trigger | Scope enrichment (Phase 3 auth) |
| 9 | ElastiCache Redis (optional) + secret | Caching |
| 10 | EC2 + Elastic IP | Application host |
| 11 | Deploy OrderMS | `systemd` service |
| 12 | API Gateway (optional) | Public HTTPS API |

---

## Networking (VPC, subnets, security groups)

### Purpose

OrderMS EC2, RDS, and ElastiCache should live in the **same VPC**. Security groups control which ports are reachable.

### Discover default VPC (quick start)

```bash
export AWS_REGION=ap-south-2

export VPC_ID=$(aws ec2 describe-vpcs \
  --region "$AWS_REGION" \
  --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)

echo "VPC_ID=$VPC_ID"

aws ec2 describe-subnets \
  --region "$AWS_REGION" \
  --filters Name=vpc-id,Values="$VPC_ID" \
  --query 'Subnets[*].[SubnetId,AvailabilityZone,CidrBlock]' \
  --output table
```

Pick at least two subnet IDs for RDS (multi-AZ subnet group).

### Create security groups

**EC2 application security group** — allows inbound HTTP/API and SSH from your IP only:

```bash
export EC2_SG_NAME=winsoon-orderms-ec2-sg

EC2_SG_ID=$(aws ec2 create-security-group \
  --region "$AWS_REGION" \
  --group-name "$EC2_SG_NAME" \
  --description "OrderMS EC2 - HTTP and SSH" \
  --vpc-id "$VPC_ID" \
  --query GroupId --output text)

# SSH from your office/home IP (replace CIDR)
aws ec2 authorize-security-group-ingress \
  --region "$AWS_REGION" \
  --group-id "$EC2_SG_ID" \
  --protocol tcp --port 22 --cidr <YOUR_IP>/32

# OrderMS API port (direct access; restrict in production)
aws ec2 authorize-security-group-ingress \
  --region "$AWS_REGION" \
  --group-id "$EC2_SG_ID" \
  --protocol tcp --port 8020 --cidr 0.0.0.0/0

echo "EC2_SG_ID=$EC2_SG_ID"
```

**RDS security group** — PostgreSQL only from EC2:

```bash
export RDS_SG_NAME=winsoon-orderms-rds-sg

RDS_SG_ID=$(aws ec2 create-security-group \
  --region "$AWS_REGION" \
  --group-name "$RDS_SG_NAME" \
  --description "OrderMS RDS PostgreSQL" \
  --vpc-id "$VPC_ID" \
  --query GroupId --output text)

aws ec2 authorize-security-group-ingress \
  --region "$AWS_REGION" \
  --group-id "$RDS_SG_ID" \
  --protocol tcp --port 5432 \
  --source-group "$EC2_SG_ID"

echo "RDS_SG_ID=$RDS_SG_ID"
```

> **Production tip:** If using API Gateway only, remove public `8020` ingress and allow traffic from API Gateway VPC Link or keep the app private.

---

## IAM roles and policies

### Purpose

The EC2 instance uses an **instance profile** so OrderMS can read Secrets Manager and send SQS messages **without** embedding access keys on the server.

### Create EC2 instance role

**Trust policy** (allows EC2 to assume the role):

```bash
cat > /tmp/orderms-ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ec2.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role \
  --role-name winsoon-orderms-ec2-role \
  --assume-role-policy-document file:///tmp/orderms-ec2-trust.json \
  --description "EC2 role for OrderMS microservice"
```

**Inline policy** — least privilege for this project:

```bash
cat > /tmp/orderms-ec2-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SecretsManagerRead",
      "Effect": "Allow",
      "Action": ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"],
      "Resource": [
        "arn:aws:secretsmanager:${AWS_REGION}:${AWS_ACCOUNT_ID}:secret:winsoon/orderms/*"
      ]
    },
    {
      "Sid": "SqsPublish",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:GetQueueUrl",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:${AWS_REGION}:${AWS_ACCOUNT_ID}:orderms-events.fifo"
    }
  ]
}
EOF

aws iam put-role-policy \
  --role-name winsoon-orderms-ec2-role \
  --policy-name orderms-runtime-policy \
  --policy-document file:///tmp/orderms-ec2-policy.json
```

**Instance profile:**

```bash
aws iam create-instance-profile --instance-profile-name winsoon-orderms-ec2-profile

aws iam add-role-to-instance-profile \
  --instance-profile-name winsoon-orderms-ec2-profile \
  --role-name winsoon-orderms-ec2-role
```

### Lambda execution role (Cognito Pre Token)

Created automatically by `deploy/setup-pretoken-lambda.sh`, or manually:

```bash
aws iam create-role \
  --role-name winsoon-cognito-pretoken-orderms-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }]
  }'

aws iam attach-role-policy \
  --role-name winsoon-cognito-pretoken-orderms-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
```

---

## Amazon RDS (PostgreSQL)

### Purpose

Persistent storage for orders, customers, and order items. OrderMS uses Hibernate/JPA with `ddl-auto: update` in development; use migrations for production.

### Create DB subnet group

```bash
export SUBNET_A=<subnet-id-az-a>
export SUBNET_B=<subnet-id-az-b>

aws rds create-db-subnet-group \
  --region "$AWS_REGION" \
  --db-subnet-group-name winsoon-orderms-db-subnet \
  --db-subnet-group-description "Subnets for OrderMS RDS" \
  --subnet-ids "$SUBNET_A" "$SUBNET_B"
```

### Create PostgreSQL instance

```bash
# Generate a strong master password and store it securely — do NOT commit
export DB_MASTER_PASSWORD='<generate-strong-password>'

aws rds create-db-instance \
  --region "$AWS_REGION" \
  --db-instance-identifier winsoon-orderms-db \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version 16.4 \
  --master-username orderms_admin \
  --master-user-password "$DB_MASTER_PASSWORD" \
  --allocated-storage 20 \
  --storage-type gp3 \
  --db-name orderms_db \
  --vpc-security-group-ids "$RDS_SG_ID" \
  --db-subnet-group-name winsoon-orderms-db-subnet \
  --backup-retention-period 7 \
  --no-publicly-accessible \
  --tags Key=Name,Value=winsoon-orderms-db Key=Service,Value=orderms
```

Wait until available:

```bash
aws rds wait db-instance-available \
  --region "$AWS_REGION" \
  --db-instance-identifier winsoon-orderms-db
```

Get endpoint:

```bash
export RDS_ENDPOINT=$(aws rds describe-db-instances \
  --region "$AWS_REGION" \
  --db-instance-identifier winsoon-orderms-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)

echo "RDS endpoint: $RDS_ENDPOINT"
```

### Create application database user (optional)

Connect from EC2 (after EC2 is running) with `psql` and create a dedicated app user with limited privileges, or use the master user in dev only.

### Verify connectivity from EC2

```bash
# On EC2, after Secrets Manager is configured:
psql -h "$DATABASE_HOST" -U "$DATABASE_USERNAME" -d orderms_db -c 'SELECT 1;'
```

---

## AWS Secrets Manager

### Purpose

OrderMS loads configuration at startup via Spring Cloud AWS:

```yaml
spring.config.import: optional:aws-secretsmanager:winsoon/orderms/postgresql,...
```

Secret JSON keys become Spring properties (e.g. `DATABASE_HOST`, `COGNITO_ISSUER_URI`).

### Secret: PostgreSQL (`winsoon/orderms/postgresql`)

Create a local JSON file **outside git**:

```bash
cat > /tmp/postgresql-secret.json <<EOF
{
  "DATABASE_HOST": "${RDS_ENDPOINT}",
  "DATABASE_PORT": "5432",
  "DATABASE_NAME": "orderms_db",
  "DATABASE_USERNAME": "orderms_admin",
  "DATABASE_PASSWORD": "<your-db-password>"
}
EOF

aws secretsmanager create-secret \
  --region "$AWS_REGION" \
  --name winsoon/orderms/postgresql \
  --description "RDS PostgreSQL connection for OrderMS" \
  --secret-string file:///tmp/postgresql-secret.json

rm -f /tmp/postgresql-secret.json
```

### Secret: SQS (`winsoon/orderms/sqs`)

Created after the queue exists (see [SQS section](#amazon-sqs-order-events)).

### Secret: Cognito (`winsoon/orderms/cognito`)

Created after Cognito user pool setup (see [Cognito section](#amazon-cognito-authentication)). Template: `deploy/cognito-secret.json.example`.

```bash
cat > /tmp/cognito-secret.json <<EOF
{
  "COGNITO_ISSUER_URI": "https://cognito-idp.${AWS_REGION}.amazonaws.com/<USER_POOL_ID>",
  "COGNITO_USER_POOL_ID": "<USER_POOL_ID>",
  "COGNITO_REGION": "${AWS_REGION}",
  "COGNITO_CLIENT_ID": "<APP_CLIENT_ID>",
  "COGNITO_DOMAIN": "<DOMAIN_PREFIX>.auth.${AWS_REGION}.amazoncognito.com"
}
EOF

aws secretsmanager create-secret \
  --region "$AWS_REGION" \
  --name winsoon/orderms/cognito \
  --description "Cognito OAuth2 / JWT issuer settings for OrderMS" \
  --secret-string file:///tmp/cognito-secret.json

rm -f /tmp/cognito-secret.json
```

### Secret: Redis (`winsoon/orderms/redis`)

Created by `deploy/setup-elasticache-redis.sh` or manually. Template: `deploy/redis-secret.json.example`.

### Verify secrets (no values printed)

```bash
for s in postgresql sqs cognito redis; do
  aws secretsmanager describe-secret \
    --region "$AWS_REGION" \
    --secret-id "winsoon/orderms/$s" \
    --query '[Name,ARN]' --output text 2>/dev/null || echo "Missing: winsoon/orderms/$s"
done
```

---

## Amazon EC2 (application host)

### Purpose

Hosts the OrderMS Spring Boot JAR, managed by **systemd**. Uses the IAM instance profile for AWS API access.

### Launch Ubuntu instance

```bash
# Find latest Ubuntu 22.04 LTS AMI in your region
export AMI_ID=$(aws ec2 describe-images \
  --region "$AWS_REGION" \
  --owners 099720109477 \
  --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
            "Name=state,Values=available" \
  --query 'sort_by(Images,&CreationDate)[-1].ImageId' --output text)

aws ec2 run-instances \
  --region "$AWS_REGION" \
  --image-id "$AMI_ID" \
  --instance-type t3.small \
  --key-name <YOUR_KEY_PAIR_NAME> \
  --security-group-ids "$EC2_SG_ID" \
  --subnet-id "$SUBNET_A" \
  --iam-instance-profile Name=winsoon-orderms-ec2-profile \
  --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":20,"VolumeType":"gp3"}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=Winsoon-OrderMS}]' \
  --query 'Instances[0].InstanceId' --output text
```

Wait for running state:

```bash
export INSTANCE_ID=<instance-id-from-above>

aws ec2 wait instance-running --region "$AWS_REGION" --instance-ids "$INSTANCE_ID"
```

### Allocate Elastic IP (stable public address)

```bash
export ALLOCATION_ID=$(aws ec2 allocate-address \
  --region "$AWS_REGION" \
  --domain vpc \
  --query AllocationId --output text)

aws ec2 associate-address \
  --region "$AWS_REGION" \
  --instance-id "$INSTANCE_ID" \
  --allocation-id "$ALLOCATION_ID"

export ELASTIC_IP=$(aws ec2 describe-addresses \
  --region "$AWS_REGION" \
  --allocation-ids "$ALLOCATION_ID" \
  --query 'Addresses[0].PublicIp' --output text)

echo "Elastic IP: $ELASTIC_IP"
```

### Bootstrap EC2 (first login)

```bash
ssh -i <your-key.pem> ubuntu@$ELASTIC_IP

sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk maven git postgresql-client

# Clone repository (use your fork URL)
mkdir -p ~/microservice
cd ~/microservice
git clone <your-repo-url> Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL
```

### EC2 outbound requirements

| Destination | Port | Why |
|-------------|------|-----|
| RDS endpoint | 5432 | Database |
| `cognito-idp.<region>.amazonaws.com` | 443 | JWKS / OIDC |
| `secretsmanager.<region>.amazonaws.com` | 443 | Config at startup |
| `sqs.<region>.amazonaws.com` | 443 | Event publishing |
| ElastiCache endpoint | 6379 | Redis (if enabled) |
| GitHub / Maven Central | 443 | Build and deploy |

Default VPC security groups usually allow outbound HTTPS; confirm if you use restrictive NACLs.

---

## Amazon Cognito (authentication)

Cognito is the **identity provider (IdP)**. It authenticates users and issues **signed JWT access tokens**. OrderMS does **not** perform login — it only **validates** tokens (OAuth2 Resource Server).

### Auth phases (what each layer adds)

| Phase | AWS / app change | API behaviour |
|-------|------------------|---------------|
| **1** | User pool, domain, resource server, app client, test user | APIs still open; you can obtain a JWT |
| **2** | Cognito settings in Secrets Manager; Spring Security enabled | No token → **401**; valid JWT → **200** |
| **3** | App client scopes + Pre Token Lambda | Missing scope → **403**; correct scope → **200** |

```
Client ──login──▶ Cognito ──▶ JWT access token
                                    │
Client ──API + Bearer JWT──────────▶ OrderMS
                                      ├─ Validate signature (JWKS)
                                      ├─ Check issuer + expiry
                                      └─ Check scope (Phase 3)
```

---

### Step 1 — Create User Pool

**Purpose:** User directory, password policy, and token signing keys.

```bash
aws cognito-idp create-user-pool \
  --region "$AWS_REGION" \
  --pool-name winsoon-orderms-pool \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}" \
  --auto-verified-attributes email \
  --username-attributes email
```

Save the User Pool ID from the response:

```bash
export USER_POOL_ID=<UserPool.Id from output>
echo "USER_POOL_ID=$USER_POOL_ID"
```

**Verify:**

```bash
aws cognito-idp describe-user-pool \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --query 'UserPool.[Name,Id,Status]' --output table
```

---

### Step 2 — Create Cognito hosted domain

**Purpose:** Public hostname for OAuth endpoints (`/oauth2/authorize`, `/oauth2/token`).

```bash
export DOMAIN_PREFIX=winsoon-orderms-dev

aws cognito-idp create-user-pool-domain \
  --region "$AWS_REGION" \
  --domain "$DOMAIN_PREFIX" \
  --user-pool-id "$USER_POOL_ID"
```

Hosted domain URL: `https://${DOMAIN_PREFIX}.auth.${AWS_REGION}.amazoncognito.com`

**Verify:** Open the domain URL in a browser (login page may appear).

---

### Step 3 — Create resource server and custom scopes

**Purpose:** Define API permission names that OrderMS enforces in Phase 3.

Scopes file in repository: `deploy/cognito-scopes.json`

```bash
aws cognito-idp create-resource-server \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --identifier orderms \
  --name "OrderMS API" \
  --scopes file://deploy/cognito-scopes.json
```

| Scope name | Full claim in JWT | Used for |
|------------|-------------------|----------|
| `read` | `orderms/read` | `GET` orders and customers |
| `write` | `orderms/write` | `POST`, `PUT` |
| `admin` | `orderms/admin` | `DELETE` |

**Verify:**

```bash
aws cognito-idp describe-resource-server \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --identifier orderms
```

---

### Step 4 — Create app client

**Purpose:** Register the application allowed to request tokens.

```bash
aws cognito-idp create-user-pool-client \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --client-name orderms-dev-client \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --prevent-user-existence-errors ENABLED
```

Save **Client ID** from response (no client secret for this public dev client).

```bash
export CLIENT_ID=<ClientId from output>
```

> `USER_PASSWORD_AUTH` is for **development and CLI testing only**. Production UIs should use **Authorization Code + PKCE** via the hosted UI.

**Verify:**

```bash
aws cognito-idp list-user-pool-clients \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID"
```

---

### Step 5 — Create test user

**Purpose:** Human account for obtaining JWTs in dev/test.

```bash
export TEST_USERNAME=admin@your-domain.example

aws cognito-idp admin-create-user \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --username "$TEST_USERNAME" \
  --user-attributes Name=email,Value="$TEST_USERNAME" Name=email_verified,Value=true \
  --message-action SUPPRESS

aws cognito-idp admin-set-user-password \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --username "$TEST_USERNAME" \
  --password "<your-secure-password>" \
  --permanent
```

**Verify:**

```bash
aws cognito-idp admin-get-user \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --username "$TEST_USERNAME" \
  --query 'UserStatus' --output text
# Expected: CONFIRMED
```

---

### Step 6 — Store Cognito config in Secrets Manager

After steps 1–4, create the Cognito secret (see [Secrets Manager](#secret-cognito-winsoonordermscognito)).

Issuer URI format:

```
https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>
```

---

### Step 7 — Obtain JWT access token (Phase 1 verification)

```bash
aws cognito-idp initiate-auth \
  --region "$AWS_REGION" \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "$CLIENT_ID" \
  --auth-parameters USERNAME="$TEST_USERNAME",PASSWORD=<your-secure-password>
```

Copy `AuthenticationResult.AccessToken` from the response (expires in ~1 hour).

**OIDC discovery (issuer + JWKS):**

```bash
curl -s "https://cognito-idp.${AWS_REGION}.amazonaws.com/${USER_POOL_ID}/.well-known/openid-configuration" \
  | jq .issuer,.jwks_uri
```

---

### Phase 2 — Resource server (application-side)

Phase 2 requires **no new AWS resources**. OrderMS reads `COGNITO_ISSUER_URI` from Secrets Manager and enables Spring OAuth2 Resource Server.

| Path | Authentication |
|------|----------------|
| `/actuator/health`, `/swagger-ui/**`, `/api-docs/**` | Public |
| `/orders/**`, `/customers/**` | Valid Cognito JWT required |

**Expected behaviour after Phase 2 deploy:**

| Request | HTTP status |
|---------|-------------|
| No `Authorization` header | **401** |
| Invalid JWT | **401** |
| Valid JWT | **200** |
| `GET /actuator/health` (no token) | **200** |

EC2 must allow **outbound HTTPS** to Cognito for JWKS fetch.

---

### Phase 3 — Scope-based authorization

Phase 3 adds **403 Forbidden** when the JWT is valid but lacks the required scope.

| Cognito scope | Spring authority | HTTP / paths |
|---------------|------------------|--------------|
| `orderms/read` | `SCOPE_orderms/read` | `GET` `/orders/**`, `/customers/**` |
| `orderms/write` | `SCOPE_orderms/write` | `POST`, `PUT` |
| `orderms/admin` | `SCOPE_orderms/admin` | `DELETE` |

`write` and `admin` scopes also satisfy `GET` requests (hierarchical access).

#### Problem

Tokens from `USER_PASSWORD_AUTH` include `aws.cognito.signin.user.admin` by default — **not** `orderms/*` scopes. Two Cognito changes fix this:

1. Authorize custom scopes on the app client.
2. Attach a **Pre Token Generation Lambda** to inject scopes (and groups).

#### Step 3a — Authorize scopes on app client

Run from the project root:

```bash
chmod +x deploy/update-app-client-scopes.sh
./deploy/update-app-client-scopes.sh
```

Or manually:

```bash
aws cognito-idp update-user-pool-client \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --client-id "$CLIENT_ID" \
  --allowed-o-auth-scopes "orderms/read" "orderms/write" "orderms/admin" \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH \
  --prevent-user-existence-errors ENABLED \
  --enable-token-revocation \
  --access-token-validity 5 \
  --id-token-validity 5 \
  --refresh-token-validity 30 \
  --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days \
  --auth-session-validity 3
```

#### Step 3b — Pre Token Lambda

Run the project script (creates IAM role, Lambda, permissions, and Cognito trigger):

```bash
chmod +x deploy/setup-pretoken-lambda.sh
./deploy/setup-pretoken-lambda.sh
```

Lambda source: `deploy/cognito-pretoken/index.mjs`

**Group → scope mapping (in Lambda):**

| Cognito group | Scopes added to access token |
|---------------|------------------------------|
| `ADMIN` | `orderms/read`, `orderms/write`, `orderms/admin` |
| `SALES` | `orderms/read`, `orderms/write` |
| `CUSTOMER` | `orderms/read` only |
| No group (dev user) | No custom scopes until user is added to a group |

For dev testing without groups, add the user to `ADMIN` group:

```bash
aws cognito-idp admin-add-user-to-group \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --username "$TEST_USERNAME" \
  --group-name ADMIN
```

Create group first if needed:

```bash
aws cognito-idp create-group \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --group-name ADMIN \
  --description "Full OrderMS access"
```

#### Verify Phase 3 token scopes

Obtain a **new** token after Lambda setup, then decode the `scope` claim:

```bash
# Linux/macOS — paste token between quotes
echo "<ACCESS_TOKEN>" | cut -d. -f2 | base64 -d 2>/dev/null | jq .scope
# Expected: "orderms/read orderms/write orderms/admin" (for ADMIN group)
```

---

## AWS Lambda (Cognito Pre Token Generation)

### Purpose

Runs on every token issuance **before** Cognito signs the JWT. Adds `cognito:groups` and `orderms/*` scopes to the access token based on group membership.

### Manual CLI steps (equivalent to `deploy/setup-pretoken-lambda.sh`)

**1. Package function:**

```bash
cd deploy/cognito-pretoken
zip function.zip index.mjs
```

**2. Create function:**

```bash
export LAMBDA_ROLE_ARN=$(aws iam get-role \
  --role-name winsoon-cognito-pretoken-orderms-role \
  --query Role.Arn --output text)

aws lambda create-function \
  --region "$AWS_REGION" \
  --function-name winsoon-cognito-pretoken-orderms \
  --runtime nodejs20.x \
  --role "$LAMBDA_ROLE_ARN" \
  --handler index.handler \
  --zip-file fileb://function.zip \
  --timeout 3 \
  --memory-size 128 \
  --description "OrderMS Cognito Pre Token: groups + scopes in access token"
```

**3. Allow Cognito to invoke Lambda:**

```bash
aws lambda add-permission \
  --region "$AWS_REGION" \
  --function-name winsoon-cognito-pretoken-orderms \
  --statement-id CognitoPreTokenInvoke \
  --action lambda:InvokeFunction \
  --principal cognito-idp.amazonaws.com \
  --source-arn "arn:aws:cognito-idp:${AWS_REGION}:${AWS_ACCOUNT_ID}:userpool/${USER_POOL_ID}"
```

**4. Attach trigger to user pool (V2_0):**

```bash
export LAMBDA_ARN=$(aws lambda get-function \
  --region "$AWS_REGION" \
  --function-name winsoon-cognito-pretoken-orderms \
  --query 'Configuration.FunctionArn' --output text)

cat > /tmp/update-user-pool.json <<EOF
{
  "UserPoolId": "${USER_POOL_ID}",
  "LambdaConfig": {
    "PreTokenGeneration": "${LAMBDA_ARN}",
    "PreTokenGenerationConfig": {
      "LambdaVersion": "V2_0",
      "LambdaArn": "${LAMBDA_ARN}"
    }
  },
  "AutoVerifiedAttributes": ["email"]
}
EOF

aws cognito-idp update-user-pool \
  --region "$AWS_REGION" \
  --cli-input-json file:///tmp/update-user-pool.json
```

**Verify:**

```bash
aws cognito-idp describe-user-pool \
  --region "$AWS_REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --query 'UserPool.LambdaConfig'
```

---

## Amazon SQS (order events)

### Purpose

OrderMS publishes asynchronous events when orders are created, updated, or deleted. Downstream services (inventory, notifications, analytics) consume from the queue without blocking the HTTP request.

Uses a **FIFO queue** for ordering per customer (`messageGroupId = order-customer-{customerId}`).

### Create FIFO queue

```bash
aws sqs create-queue \
  --region "$AWS_REGION" \
  --queue-name orderms-events.fifo \
  --attributes '{
    "FifoQueue": "true",
    "ContentBasedDeduplication": "false",
    "MessageRetentionPeriod": "1209600",
    "VisibilityTimeout": "30"
  }'
```

Get queue URL:

```bash
export SQS_QUEUE_URL=$(aws sqs get-queue-url \
  --region "$AWS_REGION" \
  --queue-name orderms-events.fifo \
  --query QueueUrl --output text)

echo "SQS_QUEUE_URL=$SQS_QUEUE_URL"
```

### Store in Secrets Manager

```bash
cat > /tmp/sqs-secret.json <<EOF
{
  "ORDERMS_AWS_SQS_QUEUE_URL": "${SQS_QUEUE_URL}",
  "ORDERMS_AWS_SQS_QUEUE_NAME": "orderms-events.fifo"
}
EOF

aws secretsmanager create-secret \
  --region "$AWS_REGION" \
  --name winsoon/orderms/sqs \
  --description "SQS queue settings for OrderMS events" \
  --secret-string file:///tmp/sqs-secret.json

rm -f /tmp/sqs-secret.json
```

### Verify

```bash
aws sqs get-queue-attributes \
  --region "$AWS_REGION" \
  --queue-url "$SQS_QUEUE_URL" \
  --attribute-names ApproximateNumberOfMessages QueueArn
```

After OrderMS is running, create an order via API and confirm message count increases.

---

## Amazon ElastiCache (Redis cache)

### Purpose

Optional distributed cache for OrderMS (TTL default 300 seconds). Disabled locally unless Redis secret is present; Spring auto-config excludes Redis when not configured.

### Automated setup

From project root (set `VPC_ID`, `EC2_SG_ID`, subnet IDs as environment variables):

```bash
chmod +x deploy/setup-elasticache-redis.sh

export VPC_ID=<your-vpc-id>
export EC2_SG_ID=<your-ec2-security-group-id>
# Optionally override SUBNETS inside the script or pass subnet IDs

./deploy/setup-elasticache-redis.sh
```

The script creates: cache subnet group, Redis security group (port 6379 from EC2 SG), `cache.t4g.micro` cluster, and `winsoon/orderms/redis` secret.

### Manual CLI summary

```bash
# 1. Cache subnet group
aws elasticache create-cache-subnet-group \
  --region "$AWS_REGION" \
  --cache-subnet-group-name winsoon-redis-subnet-group \
  --cache-subnet-group-description "OrderMS ElastiCache subnets" \
  --subnet-ids <subnet-1> <subnet-2> <subnet-3>

# 2. Redis security group (6379 from EC2 SG only)
# ... (see deploy/setup-elasticache-redis.sh)

# 3. Redis cluster
aws elasticache create-cache-cluster \
  --region "$AWS_REGION" \
  --cache-cluster-id winsoon-orderms-redis \
  --engine redis \
  --engine-version "7.1" \
  --cache-node-type cache.t4g.micro \
  --num-cache-nodes 1 \
  --cache-subnet-group-name winsoon-redis-subnet-group \
  --security-group-ids <redis-sg-id>
```

**Verify:** `redis-cli -h <endpoint> ping` from EC2 → `PONG`

---

## Amazon API Gateway (optional front door)

### Purpose

API Gateway is **not required** for OrderMS to run — the app is reachable directly on EC2 port `8020`. Use API Gateway when you need:

- HTTPS with AWS-managed certificate
- Custom domain (`api.your-domain.com`)
- Cognito JWT validation at the edge
- Throttling and usage plans
- Hiding EC2 from the public internet

### HTTP API with JWT authorizer (Cognito)

**1. Create HTTP API:**

```bash
export API_ID=$(aws apigatewayv2 create-api \
  --region "$AWS_REGION" \
  --name winsoon-orderms-api \
  --protocol-type HTTP \
  --query ApiId --output text)

echo "API_ID=$API_ID"
```

**2. Create JWT authorizer:**

```bash
export ISSUER_URI="https://cognito-idp.${AWS_REGION}.amazonaws.com/${USER_POOL_ID}"

aws apigatewayv2 create-authorizer \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --authorizer-type JWT \
  --name cognito-jwt-authorizer \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration Audience="$CLIENT_ID",Issuer="$ISSUER_URI"
```

**3. Create integration to EC2:**

```bash
export INTEGRATION_ID=$(aws apigatewayv2 create-integration \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --integration-type HTTP_PROXY \
  --integration-method ANY \
  --integration-uri "http://${ELASTIC_IP}:8020/api/{proxy}" \
  --payload-format-version 1.0 \
  --query IntegrationId --output text)
```

**4. Create catch-all route with authorizer:**

```bash
export AUTHORIZER_ID=$(aws apigatewayv2 get-authorizers \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --query 'Items[0].AuthorizerId' --output text)

aws apigatewayv2 create-route \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --route-key 'ANY /{proxy+}' \
  --target "integrations/$INTEGRATION_ID" \
  --authorizer-id "$AUTHORIZER_ID" \
  --authorization-type JWT

# Public health route (no JWT)
aws apigatewayv2 create-route \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --route-key 'GET /actuator/health' \
  --target "integrations/$INTEGRATION_ID" \
  --authorization-type NONE
```

**5. Deploy to stage:**

```bash
aws apigatewayv2 create-stage \
  --region "$AWS_REGION" \
  --api-id "$API_ID" \
  --stage-name dev \
  --auto-deploy

export API_ENDPOINT=$(aws apigatewayv2 get-apis \
  --region "$AWS_REGION" \
  --query "Items[?ApiId=='$API_ID'].ApiEndpoint" --output text)

echo "Invoke URL: ${API_ENDPOINT}/dev/"
```

**Verify:**

```bash
# Without token → 401
curl -s -o /dev/null -w "%{http_code}\n" "${API_ENDPOINT}/dev/orders"

# With token → 200
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  "${API_ENDPOINT}/dev/orders"
```

> For production, place EC2 in a private subnet and use VPC Link instead of public HTTP integration.

---

## Deploy OrderMS on EC2

### Build and install systemd service

On the EC2 instance:

```bash
cd ~/microservice/Order-Microservice-using-Spring-Boot-and-AWS-RDS-PostgreSQL
git pull
chmod +x deploy/install-systemd.sh
./deploy/install-systemd.sh
```

This builds the JAR, installs `deploy/orderms.service`, and starts the `orderms` systemd unit.

### Redeploy after code changes

```bash
# One-time: symlink redeploy script to home directory
./deploy/setup-home-symlink.sh

# Subsequent deploys
~/redeploy-orderms.sh
```

### systemd unit summary

| Setting | Value |
|---------|-------|
| User | `ubuntu` |
| Port | `8020` |
| Context path | `/api` |
| AWS region | `ap-south-2` (via `Environment=AWS_REGION`) |
| Credentials | EC2 instance profile (no access keys in unit file) |

### View logs

```bash
sudo journalctl -u orderms -f
```

### Local development without AWS

Copy `deploy/cognito-dev.env.example` to `deploy/cognito-dev.env` (gitignored) and export:

```bash
export DATABASE_HOST=localhost
export DATABASE_USERNAME=...
export DATABASE_PASSWORD=...
export COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<pool-id>
```

---

## Verification checklist

Run after full setup.

### Infrastructure

| # | Check | Command / action | Pass |
|---|--------|------------------|------|
| 1 | RDS available | `aws rds describe-db-instances --db-instance-identifier winsoon-orderms-db` | `available` |
| 2 | Secrets exist | Loop `describe-secret` for `winsoon/orderms/*` | All present |
| 3 | EC2 running | `aws ec2 describe-instances --instance-ids $INSTANCE_ID` | `running` |
| 4 | Elastic IP attached | `aws ec2 describe-addresses` | Associated |
| 5 | SQS queue | `aws sqs get-queue-url --queue-name orderms-events.fifo` | URL returned |
| 6 | Cognito pool | `aws cognito-idp describe-user-pool` | Active |
| 7 | Lambda trigger | `describe-user-pool` → `LambdaConfig` | PreTokenGeneration set |

### Application health

```bash
curl -s http://<ELASTIC_IP>:8020/api/actuator/health | jq .status
# Expected: "UP"
```

### Authentication (Phase 2)

| Test | Expected |
|------|----------|
| `GET /api/orders` (no header) | **401** |
| `GET /api/orders` (invalid Bearer) | **401** |
| `GET /api/orders` (valid JWT) | **200** |
| `GET /api/actuator/health` (no token) | **200** |

### Authorization (Phase 3)

| Test | Expected |
|------|----------|
| JWT without `orderms/read` on `GET /orders` | **403** |
| JWT with `orderms/read` on `GET /orders` | **200** |
| JWT without `orderms/admin` on `DELETE /orders/{id}` | **403** |
| JWT with `orderms/admin` on `DELETE` | **204** or **200** |

### Cognito token flow

```bash
# 1. Get token
aws cognito-idp initiate-auth \
  --region "$AWS_REGION" \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "$CLIENT_ID" \
  --auth-parameters USERNAME="$TEST_USERNAME",PASSWORD=<password>

# 2. OIDC discovery
curl -s "https://cognito-idp.${AWS_REGION}.amazonaws.com/${USER_POOL_ID}/.well-known/openid-configuration" | jq .issuer

# 3. Call API
curl -H "Authorization: Bearer <token>" http://<ELASTIC_IP>:8020/api/orders
```

### Automated tests (local CI)

```bash
mvn test
# Includes SecurityIntegrationTest and ScopeSecurityIntegrationTest
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| App fails at startup — database | Wrong secret or RDS SG | Verify `winsoon/orderms/postgresql`; EC2 SG allowed on RDS SG port 5432 |
| **401** with valid token | Expired token (~1 hour) or wrong issuer | Re-run `initiate-auth`; check `COGNITO_ISSUER_URI` in secret |
| **401** on startup | Cannot reach Cognito JWKS | EC2 outbound HTTPS to `cognito-idp.<region>.amazonaws.com` |
| **403** with valid JWT | Missing `orderms/*` scopes | Run Pre Token Lambda setup; add user to Cognito group; get **new** token |
| SQS events not published | Empty queue URL | Check `winsoon/orderms/sqs` secret; IAM `sqs:SendMessage` on EC2 role |
| Redis connection errors | SG or wrong endpoint | Redis SG allows 6379 from EC2 SG; verify `winsoon/orderms/redis` |
| `AccessDenied` on Secrets Manager | IAM policy too narrow | EC2 role needs `GetSecretValue` on `winsoon/orderms/*` |
| API Gateway **502** | EC2 down or wrong integration URI | Check `systemctl status orderms`; verify `ELASTIC_IP:8020` |
| Maven build fails on EC2 | Java version | `java -version` → 17; `sudo apt install openjdk-17-jdk` |

**EC2 application logs:**

```bash
sudo journalctl -u orderms -n 100 --no-pager
```

---

## Security best practices

1. **Never commit** passwords, client secrets, or `.env` files with real values.
2. Use **IAM instance roles** on EC2 — not long-lived access keys.
3. Restrict **SSH** to your IP; use **Session Manager** where possible.
4. Keep RDS **`publicly-accessible: false`**; access only from EC2/VPC.
5. Replace `USER_PASSWORD_AUTH` with **Authorization Code + PKCE** before production.
6. Enable **RDS automated backups** and test restore.
7. Rotate database passwords via Secrets Manager rotation (optional).
8. Use **API Gateway + private EC2** instead of exposing port 8020 globally.
9. Enable **CloudWatch alarms** on EC2 CPU, RDS connections, and SQS DLQ (if added).
10. Review Cognito **M2M client credentials** separately when adding service-to-service callers (client secret required).

---

## Quick reference — secret keys

| Secret path | Keys consumed by OrderMS |
|-------------|--------------------------|
| `winsoon/orderms/postgresql` | `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |
| `winsoon/orderms/sqs` | `ORDERMS_AWS_SQS_QUEUE_URL`, `ORDERMS_AWS_SQS_QUEUE_NAME` |
| `winsoon/orderms/cognito` | `COGNITO_ISSUER_URI`, `COGNITO_USER_POOL_ID`, `COGNITO_REGION`, `COGNITO_CLIENT_ID`, `COGNITO_DOMAIN` |
| `winsoon/orderms/redis` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL`, `REDIS_PASSWORD`, `REDIS_CACHE_TTL_SECONDS` |

---

## Project deploy scripts

| Script | Purpose |
|--------|---------|
| `deploy/install-systemd.sh` | First-time EC2 install (build JAR + systemd) |
| `deploy/redeploy-orderms.sh` | Git pull, rebuild, restart |
| `deploy/setup-home-symlink.sh` | Symlink redeploy script to `~/redeploy-orderms.sh` |
| `deploy/update-app-client-scopes.sh` | Cognito app client OAuth scopes (Phase 3) |
| `deploy/setup-pretoken-lambda.sh` | Lambda + Cognito Pre Token trigger (Phase 3) |
| `deploy/setup-elasticache-redis.sh` | ElastiCache Redis + secret |
| `deploy/load-cognito-env.sh` | Load Cognito IDs from env, local file, or Secrets Manager |

---

*Document version: 2026-06-24 — OrderMS AWS infrastructure setup (self-contained).*
