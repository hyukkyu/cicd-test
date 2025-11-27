#!/usr/bin/env bash
set -euo pipefail

pushd infra >/dev/null
terraform init
terraform apply "$@"
popd >/dev/null
