#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <frontend-user|frontend-admin|all> [bucket]"
  echo "Defaults: frontend-user -> cms-community-frontend-user, frontend-admin -> cms-community-frontend-admin"
  echo "Optional env: USER_BUCKET, ADMIN_BUCKET, USER_DISTRIBUTION_ID, ADMIN_DISTRIBUTION_ID, PATHS (default \"/*\"), REGION"
  exit 1
}

if [[ $# -lt 1 ]]; then
  usage
fi

ROOT_DIR=${ROOT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}
TARGET=$1
BUCKET_ARG=${2:-}
REGION=${REGION:-ap-northeast-1}
ADMIN_BUCKET=${ADMIN_BUCKET:-cms-community-frontend-admin}
USER_BUCKET=${USER_BUCKET:-cms-community-frontend-user}
USER_DISTRIBUTION_ID=${USER_DISTRIBUTION_ID:-}
ADMIN_DISTRIBUTION_ID=${ADMIN_DISTRIBUTION_ID:-}
INVALIDATE_PATHS=${PATHS:-"/*"}

deploy() {
  local folder=$1
  local bucket=$2
  local distribution_id=$3
  local source_dir="$ROOT_DIR/$folder"
  if [[ ! -d "$source_dir" ]]; then
    echo "Folder $source_dir not found"
    exit 1
  fi
  echo "Syncing $folder to s3://$bucket"
  aws s3 sync "$source_dir" "s3://$bucket" --delete --region "$REGION" --cache-control "max-age=60" --acl private
  if [[ -n "$distribution_id" ]]; then
    echo "Invalidating CloudFront distribution $distribution_id with paths: $INVALIDATE_PATHS"
    aws cloudfront create-invalidation --distribution-id "$distribution_id" --paths "$INVALIDATE_PATHS" >/dev/null
  else
    echo "No CloudFront distribution ID provided for $folder; skip invalidation."
  fi
}

case "$TARGET" in
  frontend-user)
    deploy "frontend-user" "${BUCKET_ARG:-$USER_BUCKET}" "$USER_DISTRIBUTION_ID"
    ;;
  frontend-admin)
    deploy "frontend-admin" "${BUCKET_ARG:-$ADMIN_BUCKET}" "$ADMIN_DISTRIBUTION_ID"
    ;;
  all)
    deploy "frontend-user" "${BUCKET_ARG:-$USER_BUCKET}" "$USER_DISTRIBUTION_ID"
    deploy "frontend-admin" "$ADMIN_BUCKET" "$ADMIN_DISTRIBUTION_ID"
    ;;
  *)
    usage
    ;;
esac

echo "CloudFront invalidation still required after upload."
