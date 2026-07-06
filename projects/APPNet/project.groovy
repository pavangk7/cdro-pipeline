/*
 * APPNet Project Definition
 * =========================
 * Defines the APPNet project and registers credentials for Delinea Platform
 * OAuth2 service account authentication.
 *
 * Usage:
 *   ectool evalDsl --dslFile projects/APPNet/project.groovy
 *
 * IMPORTANT: After evaluating this DSL, update the 'delinea-svc-account'
 * credential with actual client_id and client_secret values via the CD/RO UI.
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
    // Email Configuration Reference
    // -------------------------------------------------------------------------
    // The pipeline uses 'APPNet-Email-Config' as the email configuration name.
    // Ensure this is configured under:
    //   Administration > Configurations > Email Configurations
    // with your SMTP server details.
    // -------------------------------------------------------------------------
    property 'emailConfigName', value: 'APPNet-Email-Config'
}
