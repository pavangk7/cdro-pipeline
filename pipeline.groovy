// CloudBees CD/RO Groovy DSL
// Pipeline + EC-Webhook trigger to receive Sonatype Nexus Repository webhook events
// and print/inspect the incoming webhook payload and headers.
//
// Import using:
//   ectool evalDsl --dslFile pipeline.groovy
// Or paste into CloudBees CD/RO UI -> DevOps Essentials -> DSL IDE.

def targetProjectName  = 'Nexus-Webhook-Demo'
def targetPipelineName = 'Nexus-Artifact-Webhook-Pipeline'
def targetTriggerName  = 'Nexus-Artifact-Webhook-Trigger'
def webhookServiceAcct = 'nexus-webhook-sa' // Ensure this Service Account exists and has Execute access on the pipeline

project targetProjectName, {
    description = 'Project containing Nexus Repository EC-Webhook triggered pipeline.'

    pipeline targetPipelineName, {
        description = 'Triggered by Nexus Repository via EC-Webhook. Initially prints the full incoming webhook payload and headers.'

        stage 'Inspect-Webhook-Payload', {
            colorCode = '#289ce1'

            task 'Print-Webhook-Payload', {
                description = 'Outputs raw and pretty-printed EC-Webhook payload (webhookData) and headers (webhookHeaders).'
                taskType = 'COMMAND'
                subpluginKey = 'EC-Core'
                subprocedure = 'RunCommand'

                actualParameter = [
                    'shellToUse': 'sh',
                    'commandToRun': '''
set -eu

WEBHOOK_DATA_FILE="$(mktemp)"
WEBHOOK_HEADERS_FILE="$(mktemp)"
trap 'rm -f "$WEBHOOK_DATA_FILE" "$WEBHOOK_HEADERS_FILE"' EXIT

# Guard against empty/missing properties when running the pipeline manually
cat <<'CLOUDBEES_WEBHOOK_DATA_EOF' > "$WEBHOOK_DATA_FILE"
$[/javascript myPipelineRuntime.webhookData ? myPipelineRuntime.webhookData : '{}']
CLOUDBEES_WEBHOOK_DATA_EOF

cat <<'CLOUDBEES_WEBHOOK_HEADERS_EOF' > "$WEBHOOK_HEADERS_FILE"
$[/javascript myPipelineRuntime.webhookHeaders ? myPipelineRuntime.webhookHeaders : '{}']
CLOUDBEES_WEBHOOK_HEADERS_EOF

echo "============================================================"
echo "  CloudBees CD/RO - Incoming EC-Webhook Payload Inspector"
echo "============================================================"
echo ""
echo "--- 1. Incoming HTTP Headers (myPipelineRuntime.webhookHeaders) ---"
if command -v jq >/dev/null 2>&1; then
    jq . "$WEBHOOK_HEADERS_FILE" 2>/dev/null || cat "$WEBHOOK_HEADERS_FILE"
elif command -v python3 >/dev/null 2>&1; then
    python3 -m json.tool "$WEBHOOK_HEADERS_FILE" 2>/dev/null || cat "$WEBHOOK_HEADERS_FILE"
else
    cat "$WEBHOOK_HEADERS_FILE"
fi

echo ""
echo "--- 2. Incoming Payload Body (myPipelineRuntime.webhookData) ---"
if command -v jq >/dev/null 2>&1; then
    jq . "$WEBHOOK_DATA_FILE" 2>/dev/null || cat "$WEBHOOK_DATA_FILE"
elif command -v python3 >/dev/null 2>&1; then
    python3 -m json.tool "$WEBHOOK_DATA_FILE" 2>/dev/null || cat "$WEBHOOK_DATA_FILE"
else
    cat "$WEBHOOK_DATA_FILE"
fi

echo ""
echo "--- 3. Common Nexus Payload Fields (if present) ---"
if command -v python3 >/dev/null 2>&1; then
    python3 - "$WEBHOOK_DATA_FILE" <<'PYEOF'
import json, sys
try:
    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        data = json.load(f)
except Exception as e:
    print(f"Could not parse payload as JSON: {e}")
    sys.exit(0)

if not isinstance(data, dict) or not data:
    print("No JSON fields found (was this pipeline run manually instead of via webhook?).")
    sys.exit(0)

print(f"Action         : {data.get('action', 'N/A')}")
print(f"Repository     : {data.get('repositoryName', 'N/A')}")
print(f"Timestamp      : {data.get('timestamp', 'N/A')}")
print(f"Initiator      : {data.get('initiator', 'N/A')}")

if 'component' in data and isinstance(data['component'], dict):
    c = data['component']
    print(f"Event Object   : component")
    print(f"  Format       : {c.get('format', 'N/A')}")
    print(f"  Group        : {c.get('group', 'N/A')}")
    print(f"  Name         : {c.get('name', 'N/A')}")
    print(f"  Version      : {c.get('version', 'N/A')}")
    print(f"  Component ID : {c.get('componentId', 'N/A')}")

if 'asset' in data and isinstance(data['asset'], dict):
    a = data['asset']
    print(f"Event Object   : asset")
    print(f"  Format       : {a.get('format', 'N/A')}")
    print(f"  Name         : {a.get('name', 'N/A')}")
    print(f"  Asset ID     : {a.get('assetId', 'N/A')}")
PYEOF
fi
echo "============================================================"
'''
                ]
            }
        }

        // EC-Webhook trigger attached to this pipeline
        trigger targetTriggerName, {
            description = 'Triggers the pipeline when Sonatype Nexus Repository sends a webhook event.'
            enabled = '1'
            pluginKey = 'EC-Webhook'
            triggerType = 'webhook'
            serviceAccountName = webhookServiceAcct
            // Optional: set quietTimeMinutes to throttle rapid bursts of Nexus asset events
            // quietTimeMinutes = '0'
        }
    }
}
