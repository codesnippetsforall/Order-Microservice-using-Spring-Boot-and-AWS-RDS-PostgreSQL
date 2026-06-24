#!/usr/bin/env bash
# Create or update Cognito Pre Token Generation Lambda (V2_0) for OrderMS.
# Also updates app client scopes and attaches the trigger to the user pool.
#
# Usage:
#   ./deploy/setup-pretoken-lambda.sh
#
# Prerequisites: AWS CLI, zip, IAM permissions for Lambda + Cognito.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/load-cognito-env.sh"

REGION="${AWS_REGION:-${COGNITO_REGION:-ap-south-2}}"
USER_POOL_ID="${COGNITO_USER_POOL_ID}"
FUNCTION_NAME="${COGNITO_PRETOKEN_FUNCTION:-winsoon-cognito-pretoken-orderms}"
ROLE_NAME="${COGNITO_PRETOKEN_ROLE:-winsoon-cognito-pretoken-orderms-role}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:${FUNCTION_NAME}"
USER_POOL_ARN="arn:aws:cognito-idp:${REGION}:${ACCOUNT_ID}:userpool/${USER_POOL_ID}"

echo "==> Region:        $REGION"
echo "==> User pool:     $USER_POOL_ID"
echo "==> Lambda:        $FUNCTION_NAME"
echo "==> Account:       $ACCOUNT_ID"

echo "==> Step 1: Authorize scopes on app client"
chmod +x "${SCRIPT_DIR}/update-app-client-scopes.sh"
"${SCRIPT_DIR}/update-app-client-scopes.sh"

echo "==> Step 2: Ensure Lambda execution role"
TRUST_POLICY='{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "lambda.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}'

if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document "$TRUST_POLICY" \
    --description "Execution role for OrderMS Cognito Pre Token Lambda"
  aws iam attach-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
  echo "    Created role $ROLE_NAME (waiting for propagation)..."
  sleep 10
else
  echo "    Role $ROLE_NAME already exists"
fi

ROLE_ARN="$(aws iam get-role --role-name "$ROLE_NAME" --query Role.Arn --output text)"

echo "==> Step 3: Package Lambda"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
cp "${SCRIPT_DIR}/cognito-pretoken/index.mjs" "${BUILD_DIR}/index.mjs"
(cd "$BUILD_DIR" && zip -q function.zip index.mjs)

if aws lambda get-function --function-name "$FUNCTION_NAME" --region "$REGION" >/dev/null 2>&1; then
  echo "    Updating existing Lambda code"
  aws lambda update-function-code \
    --region "$REGION" \
    --function-name "$FUNCTION_NAME" \
    --zip-file "fileb://${BUILD_DIR}/function.zip" >/dev/null
  aws lambda wait function-updated --region "$REGION" --function-name "$FUNCTION_NAME"
else
  echo "    Creating Lambda function"
  aws lambda create-function \
    --region "$REGION" \
    --function-name "$FUNCTION_NAME" \
    --runtime nodejs20.x \
    --role "$ROLE_ARN" \
    --handler index.handler \
    --zip-file "fileb://${BUILD_DIR}/function.zip" \
    --timeout 3 \
    --memory-size 128 \
    --description "OrderMS Cognito Pre Token: groups + scopes in access token" >/dev/null
  aws lambda wait function-active --region "$REGION" --function-name "$FUNCTION_NAME"
fi

echo "==> Step 4: Allow Cognito to invoke Lambda"
if ! aws lambda get-policy --function-name "$FUNCTION_NAME" --region "$REGION" 2>/dev/null \
  | grep -q "cognito-idp.amazonaws.com"; then
  aws lambda add-permission \
    --region "$REGION" \
    --function-name "$FUNCTION_NAME" \
    --statement-id "CognitoPreTokenInvoke" \
    --action lambda:InvokeFunction \
    --principal cognito-idp.amazonaws.com \
    --source-arn "$USER_POOL_ARN" >/dev/null
  echo "    Added invoke permission"
else
  echo "    Invoke permission already present"
fi

echo "==> Step 5: Attach Pre Token Generation trigger (V2_0) to user pool"
cat > "${SCRIPT_DIR}/cognito-pretoken/update-user-pool.json" <<EOF
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
  --region "$REGION" \
  --cli-input-json "file://${SCRIPT_DIR}/cognito-pretoken/update-user-pool.json" >/dev/null

echo ""
echo "==> Done."
echo "    Lambda ARN:  $LAMBDA_ARN"
echo "    User pool:   $USER_POOL_ID"
echo ""
echo "Next: obtain a NEW access token (InitiateAuth) and verify claims:"
echo "  cognito:groups + scope (orderms/read ...)"
