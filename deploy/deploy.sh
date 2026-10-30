#!/usr/bin/env bash
# EC2에서 실행되는 배포 스크립트. GitHub Actions가 SSM Run Command로 실행한다.
# 손으로 실행할 때:  sudo bash /opt/fintris/deploy.sh <이미지 태그(커밋 SHA)>
#
# 하는 일
#   1. ECR 로그인 → 새 이미지 받기
#   2. 컨테이너 교체
#   3. Swagger 문서(/v3/api-docs)가 응답할 때까지 기다리기 (최대 약 3분)
#   4. 응답이 없으면 이전 이미지로 되돌리고 실패 처리
set -euo pipefail

NEW_TAG="${1:?사용법: deploy.sh <이미지 태그>}"
APP_DIR=/opt/fintris
cd "$APP_DIR"

if [[ ! -f .env ]]; then
  echo "[deploy] $APP_DIR/.env 가 없습니다. deploy/README.md 의 키 목록을 보고 먼저 만들어 주세요." >&2
  exit 1
fi

# .env 에서 배포에 필요한 값만 읽는다.
env_value() { grep -E "^$1=" .env | tail -n1 | cut -d= -f2- || true; }
AWS_REGION="$(env_value AWS_REGION)"; AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ECR_REGISTRY="$(env_value ECR_REGISTRY)"
SITE_ADDRESS="$(env_value SITE_ADDRESS)"

# 도메인이 있으면 Caddy(HTTPS)까지 띄운다.
if [[ -n "$SITE_ADDRESS" ]]; then export COMPOSE_PROFILES=https; fi

PREV_TAG="$(cat .current_tag 2>/dev/null || true)"
echo "[deploy] 이전 태그: ${PREV_TAG:-없음} → 새 태그: $NEW_TAG"

echo "[deploy] ECR 로그인"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY" > /dev/null

compose() { IMAGE_TAG="$1" docker compose "${@:2}"; }

echo "[deploy] 이미지 받기"
compose "$NEW_TAG" pull main

echo "[deploy] 컨테이너 교체"
compose "$NEW_TAG" up -d --remove-orphans

echo "[deploy] 서버 응답 확인 중..."
healthy=false
for _ in $(seq 1 36); do
  if curl -fsS -o /dev/null http://127.0.0.1:8080/v3/api-docs; then healthy=true; break; fi
  sleep 5
done

if $healthy; then
  echo "$NEW_TAG" > .current_tag
  docker image prune -f > /dev/null
  echo "[deploy] 성공: $NEW_TAG"
  exit 0
fi

echo "[deploy] 실패: 서버가 응답하지 않습니다. 최근 로그:" >&2
docker compose logs --tail 80 main >&2 || true

if [[ -n "$PREV_TAG" ]]; then
  echo "[deploy] 이전 버전($PREV_TAG)으로 되돌립니다." >&2
  compose "$PREV_TAG" up -d --remove-orphans
fi
exit 1
