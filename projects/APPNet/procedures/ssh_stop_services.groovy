/**
 * Procedure: SSH-Stop-Services
 * Project:   APPNet
 *
 * Connects to a remote server via SSH and stops application components.
 */

project 'APPNet', {

  procedure 'SSH-Stop-Services', {
    workspaceName = 'default'
    description = 'Stop application components on a remote server via SSH'

    // ── Input Parameters ──────────────────────────────────────────────

    formalParameter 'targetHost', {
      type = 'entry'
      required = '1'
      description = 'Hostname or IP address of the remote server to SSH into'
    }

    formalParameter 'sshUser', {
      type = 'entry'
      required = '1'
      description = 'SSH username for the remote connection'
    }

    formalParameter 'sshPrivateKey', {
      type = 'textarea'
      required = '1'
      description = 'SSH private key in PEM format (full content, not a file path)'
    }

    formalParameter 'appPath', {
      type = 'entry'
      required = '1'
      description = 'Application path on the remote server'
    }

    formalParameter 'environment', {
      type = 'entry'
      required = '1'
      description = 'Target environment name'
    }

    formalParameter 'appName', {
      type = 'entry'
      required = '1'
      description = 'Application name — one of: APP4, APP1, APP2, APP3, SupportUI'
    }

    formalParameter 'componentsJson', {
      type = 'entry'
      required = '1'
      description = 'JSON array of component names to stop IN ORDER'
    }

    // ── Output Parameters ─────────────────────────────────────────────

    formalOutputParameter 'stopResultJson', {
      description = 'JSON object mapping each component to its stop result'
    }

    formalOutputParameter 'allStopped', {
      description = '"true" if every component stopped successfully, "false" otherwise'
    }

    // ── Step: Stop Components ─────────────────────────────────────────

    step 'Stop Components', {
      description = 'SSH into the target host and stop each component in sequence'
      shell = '/bin/bash'
      command = '''\
#!/bin/bash
set -euo pipefail

export OPERATION_VAR="stop"
export TARGET_HOST_VAR="$[targetHost]"
export SSH_USER_VAR="$[sshUser]"
export SSH_PRIVATE_KEY_VAR="$[sshPrivateKey]"
export APP_PATH_VAR="$[appPath]"
export APP_NAME_VAR="$[appName]"
export COMPONENTS_JSON_VAR='$[componentsJson]'
export ENVIRONMENT_VAR="$[environment]"

bash projects/APPNet/scripts/ssh_service_action.sh
'''
    }
  }
}
