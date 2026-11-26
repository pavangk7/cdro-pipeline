# CloudBees CD/RO Pipeline with EC-Webhook for Nexus Repository

This project provides a CloudBees CD/RO Groovy DSL pipeline triggered by **Sonatype Nexus Repository** using the **EC-Webhook** plugin. The initial stage inspects and prints the raw and parsed incoming webhook payload (`webhookData`) and HTTP headers (`webhookHeaders`).

## Files

- `pipeline.groovy` — CloudBees CD/RO DSL defining project `Nexus-Webhook-Demo`, pipeline `Nexus-Artifact-Webhook-Pipeline`, stage `Inspect-Webhook-Payload`, and the `EC-Webhook` trigger.
- `test-webhook.sh` — `curl` helper to send a sample Nexus Repository 3 `component` or `asset` webhook payload directly to your CloudBees CD/RO webhook URL.

---

## Step 1: CloudBees CD/RO Prerequisites

1. **Install & Promote `EC-Webhook`**:
   - Go to **Administration → Plugins** in CloudBees CD/RO.
   - Verify `EC-Webhook` is installed and **Promoted**.
2. **Create a Service Account**:
   - Go to **Administration → Security → Service Accounts** (or **Access Control → Service Accounts**).
   - Create a service account named `nexus-webhook-sa` (or update `webhookServiceAcct` in `pipeline.groovy`).
   - Grant this service account **Read** and **Execute** permissions on the `Nexus-Webhook-Demo` project and `Nexus-Artifact-Webhook-Pipeline` pipeline.

---

## Step 2: Import the Pipeline DSL

Run via `ectool`:

```bash
ectool evalDsl --dslFile pipeline.groovy
```

Or open **DevOps Essentials → DSL IDE** in the CloudBees CD/RO UI, paste the contents of `pipeline.groovy`, and click **Run**.

> **Note:** If you prefer creating the trigger through the UI instead of DSL, open the pipeline → **Triggers** (lightning icon) → **+ New Trigger** → choose **EC-Webhook** as the plugin and select `nexus-webhook-sa` as the service account.

---

## Step 3: Copy the Generated Webhook URL

1. Open `Nexus-Artifact-Webhook-Pipeline` in CloudBees CD/RO.
2. Click **Triggers** → select `Nexus-Artifact-Webhook-Trigger`.
3. Copy the generated **Webhook URL** (typically formatted as `https://<your-cdro-server>/rest/v1.0/webhooks?token=<access_token>`).

---

## Step 4: Configure the Webhook in Sonatype Nexus Repository 3

1. Log in to **Nexus Repository 3** as an administrator.
2. Go to **Administration (gear icon) → System → Capabilities**.
3. Click **Create capability** and choose:
   - **Webhook: Repository** (recommended — scoped to a single repository like `maven-releases`), or
   - **Webhook: Global** (all repositories).
4. Configure the capability:
   - **Repository**: Select your target repository (if using Repository webhook).
   - **Event Types**: Select **`component`** (recommended for artifacts — fires once per logical artifact/version) or **`asset`** (fires for every individual file such as `.jar`, `.pom`, `.sha1`).
   - **URL**: Paste the CloudBees CD/RO **Webhook URL** from Step 3.
   - **Secret Key**: Optional (leave blank initially for testing).
5. Ensure **Enable this capability** is checked and save.

---

## Step 5: Test and Inspect the Payload

### Option A — Quick `curl` Test (before uploading to Nexus)

```bash
chmod +x test-webhook.sh
./test-webhook.sh "https://<your-cdro-server>/rest/v1.0/webhooks?token=<access_token>" component
```

### Option B — Upload an Artifact to Nexus

Publish or upload an artifact to the configured Nexus repository.

### View the Payload in CloudBees CD/RO

1. Go to **Pipelines → Pipeline Runs** and open the triggered run of `Nexus-Artifact-Webhook-Pipeline`.
2. Open the **Inspect-Webhook-Payload → Print-Webhook-Payload** task log.
3. You will see:
   - `myPipelineRuntime.webhookHeaders` (pretty-printed HTTP headers including `X-Nexus-Webhook-Id`)
   - `myPipelineRuntime.webhookData` (pretty-printed JSON payload from Nexus)
   - Extracted Nexus fields (`action`, `repositoryName`, `component.group`, `component.name`, `component.version`, or `asset.name`)

---

## Next Step: Accessing Payload Fields in Downstream Tasks

Once you verify the payload structure in the logs, you can reference fields directly in any downstream task parameter, condition, or gate using CloudBees CD/RO JavaScript property evaluation:

```javascript
// Repository name
$[/javascript JSON.parse(myPipelineRuntime.webhookData).repositoryName]

// Action (CREATED, UPDATED, DELETED)
$[/javascript JSON.parse(myPipelineRuntime.webhookData).action]

// Component coordinates (for rm:repository:component events)
$[/javascript JSON.parse(myPipelineRuntime.webhookData).component.group]
$[/javascript JSON.parse(myPipelineRuntime.webhookData).component.name]
$[/javascript JSON.parse(myPipelineRuntime.webhookData).component.version]
```

To run downstream deployment stages only when an artifact is newly uploaded (`CREATED`), add a stage/task **Run Condition**:

```javascript
$[/javascript JSON.parse(myPipelineRuntime.webhookData).action == 'CREATED']
```
