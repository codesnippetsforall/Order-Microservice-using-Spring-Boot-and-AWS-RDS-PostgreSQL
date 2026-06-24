#!/usr/bin/env bash
# Load Cognito settings from environment or AWS Secrets Manager (winsoon/orderms/cognito).
# Usage: source "$(dirname "$0")/load-cognito-env.sh"

set -euo pipefail

REGION="${AWS_REGION:-ap-south-2}"
SECRET_ID="${COGNITO_SECRET_ID:-winsoon/orderms/cognito}"

if [[ -n "${COGNITO_USER_POOL_ID:-}" && -n "${COGNITO_CLIENT_ID:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi

if [[ -f "${SCRIPT_DIR:-.}/cognito-dev.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  source "${SCRIPT_DIR}/cognito-dev.env"
  set +a
  if [[ -n "${COGNITO_USER_POOL_ID:-}" && -n "${COGNITO_CLIENT_ID:-}" ]]; then
    return 0 2>/dev/null || exit 0
  fi
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: Set COGNITO_USER_POOL_ID and COGNITO_CLIENT_ID, or install AWS CLI." >&2
  exit 1
fi

SECRET_JSON="$(aws secretsmanager get-secret-value \
  --region "$REGION" \
  --secret-id "$SECRET_ID" \
  --query SecretString \
  --output text)"

export COGNITO_REGION="${COGNITO_REGION:-$(echo "$SECRET_JSON" | jq -r '.COGNITO_REGION // empty')}"
export COGNITO_USER_POOL_ID="${COGNITO_USER_POOL_ID:-$(echo "$SECRET_JSON" | jq -r '.COGNITO_USER_POOL_ID // empty')}"
export COGNITO_CLIENT_ID="${COGNITO_CLIENT_ID:-$(echo "$SECRET_JSON" | jq -r '.COGNITO_CLIENT_ID // empty')}"
export COGNITO_ISSUER_URI="${COGNITO_ISSUER_URI:-$(echo "$SECRET_JSON" | jq -r '.COGNITO_ISSUER_URI // empty')}"
export COGNITO_DOMAIN="${COGNITO_DOMAIN:-$(echo "$SECRET_JSON" | jq -r '.COGNITO_DOMAIN // empty')}"

if [[ -z "${COGNITO_USER_POOL_ID}" || -z "${COGNITO_CLIENT_ID}" ]]; then
  echo "ERROR: Cognito secret $SECRET_ID must include COGNITO_USER_POOL_ID and COGNITO_CLIENT_ID." >&2
  exit 1
fi
