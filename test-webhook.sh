#!/usr/bin/env bash
# Simulate a Sonatype Nexus Repository 3 webhook POST against your CloudBees CD/RO EC-Webhook URL.
#
# Usage:
#   ./test-webhook.sh "https://<cdro-host>/rest/v1.0/webhooks?token=<your-token>" [component|asset]

set -euo pipefail

WEBHOOK_URL="${1:-}"
EVENT_TYPE="${2:-component}"

if [[ -z "$WEBHOOK_URL" ]]; then
  echo "Usage: $0 \"<CLOUDBEES_CDRO_WEBHOOK_URL>\" [component|asset]" >&2
  exit 1
fi

TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%S.000+0000")"

if [[ "$EVENT_TYPE" == "asset" ]]; then
  WEBHOOK_ID="rm:repository:asset"
  PAYLOAD=$(cat <<EOF
{
  "timestamp": "${TIMESTAMP}",
  "nodeId": "52905B51-085CCABB-CEBBEAAD-16795588-FC927D93",
  "initiator": "ci-publisher/10.0.0.12",
  "repositoryName": "maven-releases",
  "action": "CREATED",
  "asset": {
    "id": "31c950c8eeeab78336308177ae9c441c",
    "assetId": "bWF2ZW4tcmVsZWFzZXM6MzFjOTUwYzhlZWVhYjc4MzM2MzA4MTc3YWU5YzQ0MWM",
    "format": "maven2",
    "name": "com/example/my-service/1.0.0/my-service-1.0.0.jar"
  }
}
EOF
)
else
  WEBHOOK_ID="rm:repository:component"
  PAYLOAD=$(cat <<EOF
{
  "timestamp": "${TIMESTAMP}",
  "nodeId": "52905B51-085CCABB-CEBBEAAD-16795588-FC927D93",
  "initiator": "ci-publisher/10.0.0.12",
  "repositoryName": "maven-releases",
  "action": "CREATED",
  "component": {
    "id": "8f8c23a1078b46e89abc12345678abcd",
    "componentId": "bWF2ZW4tcmVsZWFzZXM6OGY4YzIzYTEwNzhiNDZlODlhYmMxMjM0NTY3OGFiY2Q",
    "format": "maven2",
    "group": "com.example",
    "name": "my-service",
    "version": "1.0.0"
  }
}
EOF
)
fi

echo "Sending simulated Nexus '${WEBHOOK_ID}' webhook to CloudBees CD/RO..."
curl -i -sS -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "X-Nexus-Webhook-Id: ${WEBHOOK_ID}" \
  -H "X-Nexus-Webhook-Delivery: test-delivery-$(date +%s)" \
  -d "$PAYLOAD"
echo ""
