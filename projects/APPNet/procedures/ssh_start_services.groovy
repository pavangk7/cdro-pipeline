/**
 * Procedure: SSH-Start-Services
 * Project:   APPNet
 *
 * Connects to a remote server via SSH and starts application components
 * in REVERSE order (componentsJson arrives in stop-order, so we reverse
 * it for startup). Supports two application types:
 *   - APP4: uses startComponent.sh script
 *   - Tomcat (APP1, APP2, APP3, SupportUI): uses startup.sh from the
 *     Tomcat bin directory (two levels up from appPath)
 *
 * Each component is verified to be running before proceeding to the next.
 * If a component fails to start, the procedure continues with the remaining
 * components but marks the failed one as FAILED in the output JSON.
 */

project 'APPNet', {

  procedure 'SSH-Start-Services', {
    description = 'Start application components on a remote server via SSH (in reverse stop-order)'

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
      description = 'Application path on the remote server (e.g. /cls/appl/core/bin or /cls/appl/ucp/tomcat_bank/tomcat_conf/conf)'
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
      description = 'JSON array of component names in STOP order; they will be started in REVERSE, e.g. ["comp1","comp2"] → starts comp2 first'
    }

    // ── Output Parameters ─────────────────────────────────────────────

    formalOutputParameter 'startResultJson', {
      description = 'JSON object mapping each component to its start result: {"component": {"status": "STARTED"|"FAILED", "message": "..."}}'
    }

    formalOutputParameter 'allStarted', {
      description = '"true" if every component started successfully, "false" otherwise'
    }

    // ── Step: Start Components ────────────────────────────────────────

    step 'Start Components', {
      description = 'SSH into the target host and start each component in reverse stop-order'
      shell = '/bin/bash'
      command = '''\
      #!/bin/bash
      set -euo pipefail

      export TARGET_HOST_VAR="$[targetHost]"
      export SSH_USER_VAR="$[sshUser]"
      export SSH_PRIVATE_KEY_VAR="$[sshPrivateKey]"
      export APP_PATH_VAR="$[appPath]"
      export APP_NAME_VAR="$[appName]"
      export COMPONENTS_JSON_VAR='$[componentsJson]'
      export ENVIRONMENT_VAR="$[environment]"

      bash projects/APPNet/scripts/ssh_start_services.sh
      '''   
    }
  }
}
