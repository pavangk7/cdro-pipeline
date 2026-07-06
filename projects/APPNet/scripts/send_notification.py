import sys
import json
import os
import subprocess
from datetime import datetime

def format_body(result_json, action_upper, environment, timestamp, pipeline_status, pipeline_run_id):
    lines = []
    lines.append("=" * 60)
    lines.append(f"  APPNet {action_upper} Notification")
    lines.append("=" * 60)
    lines.append("")
    lines.append(f"  Environment  : {environment}")
    lines.append(f"  Action       : {action_upper}")
    lines.append(f"  Timestamp    : {timestamp}")
    lines.append(f"  Status       : {pipeline_status}")
    lines.append("")
    lines.append("-" * 60)
    lines.append("  DETAILED RESULTS")
    lines.append("-" * 60)
    lines.append("")

    try:
        results = json.loads(result_json)
    except (json.JSONDecodeError, TypeError):
        lines.append("  [ERROR] Unable to parse result summary JSON.")
        lines.append(f"  Raw input: {result_json[:500]}")
        results = {}

    for app_name, app_data in sorted(results.items()):
        lines.append(f"  Application: {app_name}")
        lines.append(f"  {'─' * 50}")

        if isinstance(app_data, dict) and "instances" in app_data:
            instances = app_data.get("instances", {})
            for inst_name, inst_data in sorted(instances.items()):
                lines.append(f"    Instance: {inst_name}")
                components = inst_data.get("components", inst_data)
                for comp_name, comp_data in sorted(components.items()):
                    if comp_name == "components":
                        continue
                    status  = comp_data.get("status", "UNKNOWN") if isinstance(comp_data, dict) else str(comp_data)
                    message = comp_data.get("message", "")       if isinstance(comp_data, dict) else ""
                    icon = "✓" if status in ("STOPPED", "STARTED", "SUCCESS") else "✗"
                    line = f"      {icon} {comp_name:<25} {status:<10}"
                    if message:
                        line += f"  ({message})"
                    lines.append(line)
                lines.append("")
        elif isinstance(app_data, dict):
            for comp_name, comp_data in sorted(app_data.items()):
                status  = comp_data.get("status", "UNKNOWN") if isinstance(comp_data, dict) else str(comp_data)
                message = comp_data.get("message", "")       if isinstance(comp_data, dict) else ""
                icon = "✓" if status in ("STOPPED", "STARTED", "SUCCESS") else "✗"
                line = f"    {icon} {comp_name:<25} {status:<10}"
                if message:
                    line += f"  ({message})"
                lines.append(line)
            lines.append("")
        else:
            lines.append(f"    {app_data}")
            lines.append("")

    lines.append("-" * 60)
    lines.append(f"  Pipeline Run ID : {pipeline_run_id}")
    lines.append(f"  Generated       : {timestamp}")
    lines.append("=" * 60)
    return "\n".join(lines)

def main():
    distribution_list = os.environ.get("DISTRIBUTION_LIST_VAR", "")
    action = os.environ.get("ACTION_VAR", "")
    environment = os.environ.get("ENVIRONMENT_VAR", "")
    result_summary_json = os.environ.get("RESULT_SUMMARY_JSON_VAR", "{}")
    pipeline_status = os.environ.get("PIPELINE_STATUS_VAR", "SUCCESS")

    action_upper = action.upper()
    timestamp = datetime.utcnow().strftime('%Y-%m-%d %H:%M UTC')
    pipeline_run_id = os.environ.get("COMMANDER_PIPELINE_RUNTIME_ID",
                      os.environ.get("COMMANDER_JOBID", "N/A"))

    subject = f"[APPNet] {action_upper} {pipeline_status} - {environment} - {timestamp}"
    body = format_body(result_summary_json, action_upper, environment, timestamp, pipeline_status, pipeline_run_id)

    print("======================================================")
    print(f"Subject         : {subject}")
    print(f"Distribution    : {distribution_list}")
    print("======================================================")
    print("")
    print("── Email Body Preview ─────────────────────────────────")
    print(body)
    print("───────────────────────────────────────────────────────")
    print("")

    # 1. Attempt using ectool sendEmail
    print("[Email] Attempting to send email via ectool sendEmail...")
    cmd_send = [
        "ectool", "sendEmail",
        "--configName", "APPNet-Email-Config",
        "--to", distribution_list,
        "--subject", subject,
        "--body", body
    ]
    
    res = subprocess.run(cmd_send, capture_output=True, text=True)
    if res.returncode == 0:
        print("[Email] ✓ Email sent successfully via ectool sendEmail")
        sys.exit(0)

    print(f"[Email] ectool sendEmail failed: {res.stderr.strip() or 'Command failed'} — falling back to EC-SendEmail plugin")

    # 2. Fallback using EC-SendEmail plugin
    cmd_plugin = [
        "ectool", "runProcedure", "/plugins/EC-SendEmail/project",
        "--procedureName", "sendEmail",
        "--actualParameter", "configName=APPNet-Email-Config",
        "--actualParameter", f"to={distribution_list}",
        "--actualParameter", f"subject={subject}",
        "--actualParameter", f"text={body}"
    ]
    res_plugin = subprocess.run(cmd_plugin, capture_output=True, text=True)
    if res_plugin.returncode == 0:
        print("[Email] ✓ Email sent successfully via EC-SendEmail plugin")
        sys.exit(0)

    print("[Email] ✗ ERROR: Both email methods failed. Please check email configuration.")
    print("  Config name: APPNet-Email-Config")
    print(f"  Distribution list: {distribution_list}")
    # Do not exit with non-zero to keep notifications from failing the pipeline

if __name__ == '__main__':
    main()
