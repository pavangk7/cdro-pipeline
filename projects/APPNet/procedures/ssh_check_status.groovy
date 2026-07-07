/**
 * Procedure: SSH-Check-Status
 * Project:   APPNet
 *
 * Connects to a remote server via SSH and checks whether application
 * components are running or stopped.
 */

procedure 'SSH-Check-Status', {
    projectName = 'APPNet'
    workspaceName = 'default'
    description = 'Connects to a remote server via SSH and checks the status of application components.'

    // -----------------------------------------------------------------------
    // Formal Input Parameters
    // -----------------------------------------------------------------------

    formalParameter 'targetHost', {
        type = 'entry'
        required = '1'
        description = 'Hostname or IP address to SSH into.'
    }

    formalParameter 'sshUser', {
        type = 'entry'
        required = '1'
        description = 'SSH username for the remote connection.'
    }

    formalParameter 'sshPrivateKey', {
        type = 'textarea'
        required = '1'
        description = 'SSH private key PEM content for authentication.'
    }

    formalParameter 'appPath', {
        type = 'entry'
        required = '1'
        description = 'Application path on the remote server.'
    }

    formalParameter 'appName', {
        type = 'entry'
        required = '1'
        description = 'Application name (e.g. APP4, APP1, APP2, APP3, SupportUI).'
    }

    formalParameter 'componentsJson', {
        type = 'entry'
        required = '1'
        description = 'JSON array of component names to check (e.g. ["comp1","comp2"]).'
    }

    // -----------------------------------------------------------------------
    // Formal Output Parameters
    // -----------------------------------------------------------------------

    formalOutputParameter 'statusResultJson', {
        description = 'JSON object mapping component names to status (RUNNING or STOPPED).'
    }

    formalOutputParameter 'overallStatus', {
        description = "Overall status: 'ALL_RUNNING', 'ALL_STOPPED', or 'MIXED'."
    }

    // -----------------------------------------------------------------------
    // Step: Check Component Status via SSH
    // -----------------------------------------------------------------------

    step 'Check Component Status', {
        description = 'SSHs into the target host and checks each component process status.'
        shell = '/bin/bash'
        command = '''\
#!/bin/bash
set -euo pipefail

export OPERATION_VAR="status"
export TARGET_HOST_VAR="$[targetHost]"
export SSH_USER_VAR="$[sshUser]"
export SSH_PRIVATE_KEY_VAR="$[sshPrivateKey]"
export APP_PATH_VAR="$[appPath]"
export APP_NAME_VAR="$[appName]"
export COMPONENTS_JSON_VAR='$[componentsJson]'

bash projects/APPNet/scripts/ssh_service_action.sh
'''
    }
}
