/**
 * Procedure: Send-Notification
 * Project:   APPNet
 *
 * Sends an email notification summarising the results of a pipeline
 * stop/start operation. The email includes:
 *   - Environment, action, timestamp, and overall status in the header
 *   - A table-like breakdown: app → instance → component → status
 *   - A footer with the pipeline run ID
 *
 * Email delivery is attempted via `ectool sendEmail` first. If that
 * command is unavailable (older CD/RO versions), the procedure falls
 * back to invoking the EC-SendEmail plugin's sendEmail procedure.
 */

project 'APPNet', {

  procedure 'Send-Notification', {
    description = 'Send an email notification with pipeline stop/start results'

    // ── Input Parameters ──────────────────────────────────────────────

    formalParameter 'distributionList', {
      type = 'entry'
      required = '1'
      description = 'Comma-separated list of email addresses to notify'
    }

    formalParameter 'action', {
      type = 'entry'
      required = '1'
      description = 'The action performed — "stop" or "start"'
    }

    formalParameter 'environment', {
      type = 'entry'
      required = '1'
      description = 'Target environment name (e.g. SIT01, SIT02, UAT01)'
    }

    formalParameter 'resultSummaryJson', {
      type = 'textarea'
      required = '1'
      description = 'JSON containing per-app results. Expected structure: {"appName": {"instances": {"instanceName": {"components": {"compName": {"status": "...", "message": "..."}}}}}}'
    }

    formalParameter 'pipelineStatus', {
      type = 'entry'
      required = '1'
      defaultValue = 'SUCCESS'
      description = 'Overall pipeline status — typically SUCCESS, FAILURE, or PARTIAL'
    }

    // ── Step: Send Email Notification ─────────────────────────────────

    step 'Send Email Notification', {
      description = 'Parse results, construct email, and send via ectool or EC-SendEmail plugin'
      shell = '/bin/bash'
      command = '''\
      #!/bin/bash
      set -euo pipefail

      export DISTRIBUTION_LIST_VAR="$[distributionList]"
      export ACTION_VAR="$[action]"
      export ENVIRONMENT_VAR="$[environment]"
      export RESULT_SUMMARY_JSON_VAR="$[resultSummaryJson]"
      export PIPELINE_STATUS_VAR="$[pipelineStatus]"

      bash projects/APPNet/scripts/send_notification.sh
      '''
    }
  }
}
