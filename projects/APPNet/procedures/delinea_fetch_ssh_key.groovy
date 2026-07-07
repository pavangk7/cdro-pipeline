/**
 * Procedure: Delinea-Fetch-SSH-Key
 * Project:   APPNet
 *
 * Authenticates with the Delinea Platform using OAuth2 client_credentials
 * grant and fetches an SSH private key by secret ID.
 *
 * The procedure uses a CD/RO credential ('delinea-svc-account') to obtain
 * client_id and client_secret at runtime — no secrets are hardcoded.
 *
 * SECURITY NOTES:
 *   - The sshPrivateKey is NEVER echoed to logs; all sensitive output is
 *     handled via ectool setOutputParameter only.
 */

procedure 'Delinea-Fetch-SSH-Key', {
    projectName = 'APPNet'
    workspaceName = 'default'
    description = 'Authenticates with Delinea and fetches an SSH private key by secret ID.'

    // Attach the credential so it is available to steps via ectool getFullCredential
    credentialName = 'delinea-svc-account'

    // -----------------------------------------------------------------------
    // Formal Input Parameters
    // -----------------------------------------------------------------------

    formalParameter 'secretId', {
        type = 'entry'
        required = '1'
        description = 'The Delinea Secret Server Secret ID (e.g. 22074)'
    }

    formalParameter 'tokenAuthUrl', {
        type = 'entry'
        required = '1'
        description = 'OAuth2 Token Auth Endpoint URL'
    }

    formalParameter 'secretFetchUrlBase', {
        type = 'entry'
        required = '1'
        description = 'Delinea Secret Fetch Endpoint Base URL'
    }

    // -----------------------------------------------------------------------
    // Formal Output Parameters
    // -----------------------------------------------------------------------

    formalOutputParameter 'sshPrivateKey', {
        description = 'The retrieved SSH private key (full content, not a path).'
    }

    formalOutputParameter 'sshUsername', {
        description = 'The SSH username associated with the secret.'
    }

    // -----------------------------------------------------------------------
    // Step: Fetch SSH Key from Delinea
    // -----------------------------------------------------------------------

    step 'Fetch SSH Key from Delinea', {
        description = 'Connects to Delinea Vault, retrieves access token, and fetches SSH credentials.'
        shell = '/bin/bash'
        command = '''\
#!/bin/bash
set -euo pipefail

export TOKEN_AUTH_URL_VAR="$[tokenAuthUrl]"
export SECRET_FETCH_URL_BASE_VAR="$[secretFetchUrlBase]"
export SECRET_ID_VAR="$[secretId]"

python3 projects/APPNet/scripts/fetch_delinea_ssh_key.py
'''
    }
}
