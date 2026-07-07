/*
 * APPNet Project Definition
 * =========================
 * Defines the APPNet project and registers credentials for Delinea Platform
 * OAuth2 service account authentication.
 *
 * Usage:
 *   ectool evalDsl --dslFile projects/APPNet/project.groovy
 *
 * IMPORTANT: After evaluating this DSL, update placeholder credential values
 * with actual secrets via the CD/RO UI.
 * Never commit real credentials to source control.
 */

project 'APPNet', {
    description = 'CLS Network Middleware Service Start/Stop Orchestration Pipeline'

    // -------------------------------------------------------------------------
    // Delinea Platform Service Account Credential
    // -------------------------------------------------------------------------
    // Used for OAuth2 client_credentials flow to fetch SSH keys from
    // Delinea Secret Server Cloud.
    //
    // userName  = Delinea Platform client_id
    // password  = Delinea Platform client_secret
    // -------------------------------------------------------------------------
    credential 'delinea-svc-account', {
        description = 'Delinea Platform OAuth2 service account for SSH key retrieval'
        userName    = 'PLACEHOLDER_CLIENT_ID'
        password    = 'PLACEHOLDER_CLIENT_SECRET'
    }

    // -------------------------------------------------------------------------
    // GitHub SSH Credential
    // -------------------------------------------------------------------------
    // Used by the EC-Git plugin to clone the cdro-pipeline repository.
    //
    // userName  = git (the standard SSH username for GitHub)
    // password  = SSH private key content (PEM format, begins with -----BEGIN...)
    //
    // IMPORTANT: After evaluating this DSL, update 'cdro-git-ssh' in the
    // CD/RO UI with your actual SSH private key. Never commit real keys.
    // -------------------------------------------------------------------------
    credential 'cdro-git-ssh', {
        description = 'SSH private key for cloning the cdro-pipeline Git repository'
        userName    = 'git'
        password    = 'PLACEHOLDER_SSH_PRIVATE_KEY'
    }


    // Email Configuration Reference
    // -------------------------------------------------------------------------
    // The pipeline uses 'APPNet-Email-Config' as the email configuration name.
    // Ensure this is configured under:
    //   Administration > Configurations > Email Configurations
    // with your SMTP server details.
    // -------------------------------------------------------------------------
    property 'emailConfigName', value: 'APPNet-Email-Config'

}
