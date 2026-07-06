# APPNet Start/Stop Pipeline — CloudBees CD/RO DSL

Orchestrates stopping and starting of CLS middleware services across environments using CloudBees CD/RO Groovy DSL, with Delinea SSH key retrieval and sequenced component management.

## Overview

This project provides a CloudBees CD/RO pipeline that:

1. **Reads JSON configuration** to determine environment-specific applications, instances, and components
2. **Fetches SSH private keys** from Delinea Secret Server Cloud using OAuth2 `client_credentials` authentication
3. **Connects via SSH** to remote servers to check service status
4. **Stops or starts services** in the correct application sequence (UI → Middle-end → Backend for stop; reverse for start)
5. **Sends email notifications** with detailed results

## Project Structure

```
cls-cdro-pipeline/
├── config/
│   └── environments.json                    # Environment/app configuration
├── projects/
│   └── APPNet/
│       ├── project.groovy                   # Project definition + credentials
│       ├── pipelines/
│       │   └── start_stop_pipeline.groovy   # Main orchestration pipeline
│       ├── procedures/
│       │   ├── parse_config.groovy           # Parse JSON, resolve env config
│       │   ├── delinea_fetch_ssh_key.groovy  # Delinea OAuth2 + SSH key retrieval
│       │   ├── ssh_check_status.groovy       # Check component status via SSH
│       │   ├── ssh_stop_services.groovy      # Stop components via SSH
│       │   ├── ssh_start_services.groovy     # Start components via SSH
│       │   └── send_notification.groovy      # Email notification
│       └── scripts/
│           ├── fetch_delinea_ssh_key.py      # Authenticates and fetches SSH key from Delinea Secret Server
│           ├── orchestrate_services.py       # Main pipeline Python orchestrator
│           ├── parse_config.py               # JSON parsing configuration logic
│           ├── pipeline_helper.sh            # Pipeline validate/verify/summary script
│           ├── send_notification.py          # Email formatter and sender script
│           └── ssh_service_action.sh         # Reusable SSH status/start/stop operations script
└── README.md
```

## Prerequisites

1. **CloudBees CD/RO Server** — v10.x or later with DSL evaluation enabled
2. **Delinea Secret Server Cloud** — Service account with API access to fetch SSH secrets
3. **Python 3** — Available on the CD/RO agent running the pipeline
4. **SSH Access** — Network connectivity from CD/RO agent to target servers
5. **Email Configuration** — SMTP configuration in CD/RO named `APPNet-Email-Config`

## Setup

### 1. Evaluate the Project DSL

```bash
# Create the project and credentials
ectool evalDsl --dslFile projects/APPNet/project.groovy

# Evaluate all procedures
for proc in projects/APPNet/procedures/*.groovy; do
    echo "Evaluating: $proc"
    ectool evalDsl --dslFile "$proc"
done

# Evaluate the pipeline
ectool evalDsl --dslFile projects/APPNet/pipelines/start_stop_pipeline.groovy
```

### 2. Configure Delinea Credentials

After evaluating the project DSL, set the actual Delinea service account credentials via the CD/RO UI:

1. Navigate to **Projects → APPNet → Credentials**
2. Edit `delinea-svc-account`
3. Set **User Name** = your Delinea Platform `client_id`
4. Set **Password** = your Delinea Platform `client_secret`

> ⚠️ **Never commit real credentials to source control.**

### 3. Configure Email

Set up an email configuration in CD/RO:

1. Navigate to **Administration → Configurations → Email Configurations**
2. Create a configuration named `APPNet-Email-Config`
3. Configure your SMTP server details

## Usage

### Running the Pipeline

1. Navigate to **Projects → APPNet → Pipelines → APPNet-Start-Stop-Pipeline**
2. Click **Run**
3. Select parameters:
   - **Target Environment**: SIT01, SIT02, etc.
   - **Action**: Stop Services, Start Services, or Check Status Only
   - **Send Email Notification**: Enable/disable email notifications
4. Click **Run Pipeline**

### Application Sequence

For **stop** operations, services are stopped in the configured `application_sequence` order:

```
APP1 → APP2 → APP3 → APP4
(UI)   (Gateway)  (API)   (Backend)
```

For **start** operations, the sequence is automatically **reversed**:

```
APP4 → APP3 → APP2 → APP1
(Backend)   (API)   (Gateway)  (UI)
```

### Environment Types

| Type | Example | Behavior |
|------|---------|----------|
| **Sequence-based** | SIT02 | Full orchestration: SSH key fetch → status check → stop/start → verify |
| **Job-based** | SIT01 | Delegates to pre-existing CD/RO jobs (e.g., `Deploy-MQ`, `APPNet_Start_Stop_Pipeline`) |

## Configuration

The `config/environments.json` file defines:

- **`global_config`** — Delinea vault URLs, default notification list, app defaults (paths, users)
- **Per-environment blocks** (SIT01, SIT02, etc.) — Application sequences, instances, components, secret IDs

### Adding a New Environment

Add a new top-level key to `environments.json`:

```json
{
  "SIT03": {
    "enabled": true,
    "application_sequence": ["APP1", "APP2", "APP3", "APP4"],
    "notifications": {
      "distribution_list": "team@cls-services.com"
    },
    "app": {
      "APP4": {
        "sid": "12345",
        "instances": ["server01"],
        "components": ["OGW01", "CHP01"]
      },
      "APP1": {
        "sid": "12346",
        "instances": ["server02"],
        "components": ["bank"]
      }
    }
  }
}
```

### App Configuration Merging

Environment-specific app configs are **merged** with `global_config.app_defaults`:

- `path` and `user` from env override global defaults
- `sid`, `instances`, and `components` come from the environment config
- If an app has no env-specific `path`/`user`, the global default is used

## Components

| File | Purpose |
|------|---------|
| `project.groovy` | Project definition and Delinea credential |
| `start_stop_pipeline.groovy` | Main 3-stage pipeline (Initialize → Execute → Notify) |
| `parse_config.groovy` | JSON config parsing and default merging |
| `delinea_fetch_ssh_key.groovy` | Delinea OAuth2 auth + SSH key retrieval |
| `ssh_check_status.groovy` | Remote component status check via SSH |
| `ssh_stop_services.groovy` | Sequential component stop with verification |
| `ssh_start_services.groovy` | Reverse-order component start with verification |
| `send_notification.groovy` | Email notification with detailed results |
| `orchestrate_services.py` | Python script driving main sequential/job procedure execution |
| `fetch_delinea_ssh_key.py` | Python script authenticating and retrieving SSH credentials from Secret Server |
| `parse_config.py` | Python script parsing JSON configuration and merging global defaults |
| `send_notification.py` | Python script formatting and delivering summary notification emails |
| `ssh_service_action.sh` | Bash script managing SSH process status check, start, and stop operations |
| `pipeline_helper.sh` | Bash script performing pipeline validation, verification, and summary logs |

## Security

- SSH private keys are **never logged** — they are passed via `ectool setOutputParameter` and temp files with `chmod 600`
- Delinea credentials are stored in CD/RO's credential store and retrieved at runtime via `ectool getFullCredential`
- Temp files are cleaned up in `trap` handlers to ensure removal even on errors
- SSH connections use `BatchMode=yes` to prevent password prompts
# cls-cdro-pipeline
# cls-cdro-pipeline
# cls-cdro-pipeline
# cls-cdro-pipeline
