/**
 * Procedure: Parse-Config
 * Project:   APPNet
 *
 * Reads the environments.json configuration file and resolves the full
 * configuration for a selected environment. Supports both job-based (SIT01-style)
 * and application_sequence-based environment configurations.
 *
 * For sequence-based environments, global app_defaults are merged with
 * environment-specific overrides (env overrides win for path, user; env
 * provides sid, instances, components).
 *
 * Outputs the resolved configuration, app sequence, vault URLs, and
 * notification list as formal output parameters for downstream consumption.
 */

procedure 'Parse-Config', {
    projectName = 'APPNet'
    workspaceName = 'default'
    description = 'Reads environments.json and resolves the full configuration for a selected environment.'

    // -----------------------------------------------------------------------
    // Formal Input Parameters
    // -----------------------------------------------------------------------

    formalParameter 'configFilePath', {
        type = 'entry'
        required = '1'
        defaultValue = 'config/environments.json'
        description = 'Path to the environments.json configuration file.'
    }

    formalParameter 'environment', {
        type = 'entry'
        required = '1'
        description = 'Environment key to resolve (e.g. SIT01, SIT02).'
    }

    // -----------------------------------------------------------------------
    // Formal Output Parameters
    // -----------------------------------------------------------------------

    formalOutputParameter 'appSequenceJson', {
        description = 'JSON array of application names in execution order.'
    }

    formalOutputParameter 'envConfigJson', {
        description = 'Full resolved JSON config for the environment (with defaults merged).'
    }

    formalOutputParameter 'isJobBased', {
        description = "'true' if environment uses jobs-style config (SIT01), 'false' if it uses application_sequence."
    }

    formalOutputParameter 'notificationList', {
        description = 'Resolved email distribution list for notifications.'
    }

    formalOutputParameter 'vaultTokenAuthUrl', {
        description = 'Delinea token auth URL from global config.'
    }

    formalOutputParameter 'vaultSecretFetchUrlBase', {
        description = 'Delinea secret fetch base URL from global config.'
    }

    // -----------------------------------------------------------------------
    // Step: Parse and Resolve Configuration
    // -----------------------------------------------------------------------

    step 'Parse and Resolve Config', {
        description = 'Reads the JSON config file, validates the environment, merges defaults, and sets all output parameters.'
        shell = '/bin/bash'
        command = '''\
#!/bin/bash
set -euo pipefail

export CONFIG_FILE_VAR="$[configFilePath]"
export ENVIRONMENT_VAR="$[environment]"

python3 projects/APPNet/scripts/parse_config.py
'''
    }
}
