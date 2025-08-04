#!/bin/bash

set -e

STACK_NAME="tradestream"
TIMESTAMP=$(date +%s)

SERVICE_KEYS=("auth-service" "user-registration-service" "market-data-consumer")
SERVICE_DIRS=("authentication-service" "user-registration-service" "market-data-consumer")

echo "🔁 Starting multi-service build & deploy..."
for i in "${!SERVICE_KEYS[@]}"; do
  SERVICE_NAME="${SERVICE_KEYS[$i]}"
  CONTEXT_DIR="${SERVICE_DIRS[$i]}"
  IMAGE_NAME="tradestream-$SERVICE_NAME:$TIMESTAMP"

  echo ""
  echo "🔨 Building $SERVICE_NAME from $CONTEXT_DIR → $IMAGE_NAME"
  docker build -t $IMAGE_NAME ./$CONTEXT_DIR

  echo "🚢 Updating $SERVICE_NAME in stack '$STACK_NAME'"
  docker service update \
    --image $IMAGE_NAME \
    --force \
    ${STACK_NAME}_${SERVICE_NAME}

  echo "✅ $SERVICE_NAME updated"
done

echo ""
echo "🎉 All selected services updated successfully with tag $TIMESTAMP"
