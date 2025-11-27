#!/usr/bin/env bash
set -euo pipefail

REGION="${REGION:-ap-northeast-1}"
ROOT_DIR=${ROOT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}
REPO_NAME="cms-community-backend"
IMAGE_TAG=${1:-latest}
CLUSTER="${CLUSTER:-cms-community-cluster}"
SERVICE="${SERVICE:-cms-community-service}"
SCALE_DOWN_DESIRED="${SCALE_DOWN_DESIRED:-0}"
SCALE_UP_DESIRED="${SCALE_UP_DESIRED:-2}"
SKIP_ECS_SCALING="${SKIP_ECS_SCALING:-false}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URL="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$REPO_NAME" #안녕

scaled_down=false
scaled_up=false

restore_service() {
  if [[ "$scaled_down" == "true" && "$scaled_up" == "false" && "$SKIP_ECS_SCALING" != "true" ]]; then
    echo "Restoring ECS service to $SCALE_UP_DESIRED task(s)..."
    aws ecs update-service \
      --cluster "$CLUSTER" \
      --service "$SERVICE" \
      --desired-count "$SCALE_UP_DESIRED" \
      --force-new-deployment \
      --region "$REGION" >/dev/null
  fi
}
trap restore_service EXIT

if [[ "$SKIP_ECS_SCALING" != "true" ]]; then
  echo "Scaling down ECS service $SERVICE to $SCALE_DOWN_DESIRED..."
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$SERVICE" \
    --desired-count "$SCALE_DOWN_DESIRED" \
    --region "$REGION"
  scaled_down=true
fi

# Ensure repository exists
aws ecr describe-repositories --repository-names "$REPO_NAME" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "$REPO_NAME" --region "$REGION"

pushd "$ROOT_DIR/backend" >/dev/null
mvn -q -DskipTests package
popd >/dev/null

sudo docker build -t "$ECR_URL:$IMAGE_TAG" "$ROOT_DIR/backend"
aws ecr get-login-password --region "$REGION" | sudo docker login --username AWS --password-stdin "$ECR_URL"
sudo docker push "$ECR_URL:$IMAGE_TAG"

echo "Pushed image $ECR_URL:$IMAGE_TAG"

if [[ "$SKIP_ECS_SCALING" != "true" ]]; then
  echo "Scaling up ECS service $SERVICE to $SCALE_UP_DESIRED and forcing new deployment..."
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$SERVICE" \
    --desired-count "$SCALE_UP_DESIRED" \
    --force-new-deployment \
    --region "$REGION"
  scaled_up=true
fi
