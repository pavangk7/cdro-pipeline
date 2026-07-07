/*
 * APPNet Start/Stop Pipeline
 * ==========================
 * Main orchestration pipeline for starting and stopping CLS middleware
 * services across environments. Reads environment configuration from JSON,
 * fetches SSH keys from Delinea Secret Server, and executes stop/start
 * operations in the correct application sequence.
 *
 * Pipeline Stages:
 *   1. Initialize     — Parse config, validate environment, resolve app sequence
 *   2. Execute Action  — Fetch SSH keys, SSH to instances, stop/start components
 *   3. Notify          — Send email notification with results
 *
 * Usage:
 *   ectool evalDsl --dslFile projects/APPNet/pipelines/start_stop_pipeline.groovy
 */

project 'APPNet', {

    pipeline 'APPNet-Start-Stop-Pipeline', {
        description = 'Orchestrates stopping and starting of CLS middleware services with sequenced component management and Delinea SSH key integration'

        // =====================================================================
        // Pipeline Triggers
        // =====================================================================
        trigger 'bitbucket-push', {
            description = 'Triggered automatically on push events to the Bitbucket repository'
            pluginKey   = 'EC-Bitbucket'
            triggerType = 'webhook'
        }

        // =====================================================================
        // Pipeline Parameters
        // =====================================================================

        formalParameter 'environment', {
            type        = 'select'
            label       = 'Target Environment'
            required    = '1'
            description = 'Select the target environment (e.g., SIT01, SIT02)'
            options     = [
                [value: 'SIT01', label: 'SIT01'],
                [value: 'SIT02', label: 'SIT02']
            ]
        }

        formalParameter 'action', {
            type        = 'select'
            label       = 'Action'
            required    = '1'
            description = 'Select whether to stop or start all services'
            options     = [
                [value: 'stop',   label: 'Stop Services'],
                [value: 'start',  label: 'Start Services'],
                [value: 'status', label: 'Check Status Only']
            ]
        }

        formalParameter 'sendNotification', {
            type           = 'checkbox'
            label          = 'Send Email Notification'
            description    = 'Send email notification on completion'
            defaultValue   = 'true'
            checkedValue   = 'true'
            uncheckedValue = 'false'
        }

        formalParameter 'configFilePath', {
            type         = 'entry'
            label        = 'Config File Path'
            description  = 'Path to the environments.json configuration file'
            defaultValue = 'config/environments.json'
            required     = '0'
        }

        formalParameter 'agentResource', {
            type         = 'entry'
            label        = 'Agent Resource'
            description  = 'CD/RO Agent Resource to run pipeline steps and scripts'
            defaultValue = 'local'
            required     = '1'
        }

        // =====================================================================
        // Stage 1: Initialize
        // =====================================================================
        stage 'Initialize', {
            description = 'Parse configuration, validate environment, and resolve application sequence'
            colorCode   = '#289ce1'
            resourceName  = '$[agentResource]'
            workspaceName = 'default'

            task 'Checkout Source Code', {
                description = 'Clones or resets the repository to the workspace root'
                taskType    = 'COMMAND'
                command     = '''#!/bin/bash
                    set -euo pipefail

                    REPO_URL="git@github.com:pavangk7/cdro-pipeline.git"

                    echo "Checking git workspace..."
                    if [ -d .git ]; then
                        echo "Repository exists. Performing fetch and clean reset..."
                        git remote set-url origin "$REPO_URL"
                        git fetch origin
                        git reset --hard origin/main
                    else
                        echo "Cloning repository from $REPO_URL..."
                        git clone "$REPO_URL" .
                    fi
                '''
                shell = '/bin/bash'
            }

            task 'Parse Environment Config', {
                description   = 'Read environments.json and resolve the full configuration for the selected environment'
                taskType      = 'PROCEDURE'
                subproject    = 'APPNet'
                subprocedure  = 'Parse-Config'
                actualParameter 'configFilePath', '$[configFilePath]'
                actualParameter 'environment', '$[environment]'
            }

            task 'Validate Environment', {
                description = 'Verify the environment is enabled and has valid configuration'
                taskType    = 'COMMAND'
                command     = '''#!/bin/bash
                    set -euo pipefail

                    export IS_JOB_BASED_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/isJobBased]"
                    export ENVIRONMENT_VAR="$[environment]"
                    export ACTION_VAR="$[action]"
                    export APP_SEQUENCE_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/appSequenceJson]"

                    bash projects/APPNet/scripts/pipeline_helper.sh validate
                '''
                shell = '/bin/bash'
            }
        }

        // =====================================================================
        // Stage 2: Execute Action
        // =====================================================================
        stage 'Execute Action', {
            description = 'Fetch SSH keys from Delinea, connect to instances, and execute stop/start/status operations'
            colorCode   = '#ff7f0e'
            resourceName  = '$[agentResource]'
            workspaceName = 'default'

            gate 'PRE', {
                task 'Check Init Success', {
                    gateType  = 'PRE'
                    taskType  = 'CONDITIONAL'
                    gateCondition = '$[/javascript myPipelineRuntime.stages["Initialize"].outcome == "success"]'
                }
            }

            task 'Orchestrate Service Action', {
                description = 'Main orchestration: iterate apps, fetch keys, SSH, execute action'
                taskType    = 'COMMAND'
                command     = '''#!/bin/bash
                    set -euo pipefail

                    export ACTION_VAR="$[action]"
                    export ENVIRONMENT_VAR="$[environment]"
                    export VAULT_TOKEN_URL_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/vaultTokenAuthUrl]"
                    export VAULT_SECRET_URL_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/vaultSecretFetchUrlBase]"
                    export ENV_CONFIG_JSON_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/envConfigJson]"
                    export APP_SEQUENCE_JSON_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/appSequenceJson]"
                    export IS_JOB_BASED_VAR="$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/isJobBased]"

                    python3 projects/APPNet/scripts/orchestrate_services.py
                '''
                shell = '/bin/bash'
            }

            task 'Verify Results', {
                description = 'Verify the action completed as expected'
                taskType    = 'COMMAND'
                command     = '''#!/bin/bash
                    set -euo pipefail

                    export OVERALL_SUCCESS_VAR="$[/myPipelineRuntime/stages/Execute Action/tasks/Orchestrate Service Action/job/outputParameters/overallSuccess]"
                    export ACTION_VAR="$[action]"

                    bash projects/APPNet/scripts/pipeline_helper.sh verify
                '''
                shell = '/bin/bash'
            }
        }

        // =====================================================================
        // Stage 3: Notify
        // =====================================================================
        stage 'Notify', {
            description = 'Send email notification with pipeline results'
            colorCode   = '#2ca02c'
            resourceName  = '$[agentResource]'
            workspaceName = 'default'

            task 'Send Email Notification', {
                description = 'Send notification email to the distribution list'
                taskType    = 'PROCEDURE'
                subproject  = 'APPNet'
                subprocedure = 'Send-Notification'
                actualParameter 'distributionList', '$[/myPipelineRuntime/stages/Initialize/tasks/Parse Environment Config/job/outputParameters/notificationList]'
                actualParameter 'action', '$[action]'
                actualParameter 'environment', '$[environment]'
                actualParameter 'resultSummaryJson', '$[/myPipelineRuntime/stages/Execute Action/tasks/Orchestrate Service Action/job/outputParameters/resultSummaryJson]'
                actualParameter 'pipelineStatus', '$[/javascript myPipelineRuntime.stages["Execute Action"].tasks["Verify Results"].job.outputParameters.overallSuccess == "true" ? "SUCCESS" : "PARTIAL_FAILURE"]'
                condition = '$[sendNotification]'
            }

            task 'Pipeline Summary', {
                description = 'Print final pipeline summary'
                taskType    = 'COMMAND'
                command     = '''#!/bin/bash
                    set -euo pipefail

                    export ENVIRONMENT_VAR="$[environment]"
                    export ACTION_VAR="$[action]"
                    export SEND_NOTIFICATION_VAR="$[sendNotification]"

                    bash projects/APPNet/scripts/pipeline_helper.sh summary
                '''
                shell = '/bin/bash'
            }
        }
    }
}
