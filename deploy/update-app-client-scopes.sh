#!/usr/bin/env bash
# Authorize OrderMS resource-server scopes on the Cognito app client.
# Required before Pre Token Lambda can add orderms/* scopes to access tokens.
#
# Usage: ./deploy/update-app-client-scopes.sh
# Cognito IDs are loaded from env, deploy/cognito-dev.env, or AWS Secrets Manager.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/load-cognito-env.sh"

REGION="${AWS_REGION:-${COGNITO_REGION:-ap-south-2}}"

echo "==> Updating app client scopes (pool=$COGNITO_USER_POOL_ID client=$COGNITO_CLIENT_ID region=$REGION)"

aws cognito-idp update-user-pool-client \
  --region "$REGION" \
  --user-pool-id "$COGNITO_USER_POOL_ID" \
  --client-id "$COGNITO_CLIENT_ID" \
  --allowed-o-auth-scopes "orderms/read" "orderms/write" "orderms/admin" \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH \
  --prevent-user-existence-errors ENABLED \
  --enable-token-revocation \
  --access-token-validity 5 \
  --id-token-validity 5 \
  --refresh-token-validity 30 \
  --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days \
  --auth-session-validity 3

echo "==> App client scopes updated."

aws cognito-idp describe-user-pool-client \
  --region "$REGION" \
  --user-pool-id "$COGNITO_USER_POOL_ID" \
  --client-id "$COGNITO_CLIENT_ID" \
  --query 'UserPoolClient.{ClientName:ClientName,AllowedOAuthScopes:AllowedOAuthScopes,ExplicitAuthFlows:ExplicitAuthFlows}' \
  --output json
