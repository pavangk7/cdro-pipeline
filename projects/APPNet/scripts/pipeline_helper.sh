#!/bin/bash
set -euo pipefail

SUB_COMMAND="${1}"

# ============================================================
# SUB-COMMAND: validate
# ============================================================
if [[ "${SUB_COMMAND}" == "validate" ]]; then
    IS_JOB_BASED="${IS_JOB_BASED_VAR}"
    ENV="${ENVIRONMENT_VAR}"
    ACTION="${ACTION_VAR}"
    APP_SEQUENCE="${APP_SEQUENCE_VAR}"

    echo "============================================"
    echo " APPNet Start/Stop Pipeline"
    echo "============================================"
    echo " Environment : ${ENV}"
    echo " Action      : ${ACTION}"
    echo " Job-Based   : ${IS_JOB_BASED}"
    echo " Timestamp   : $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo "============================================"

    if [ "${IS_JOB_BASED}" = "true" ]; then
        echo ""
        echo "INFO: Environment '${ENV}' is job-based."
        echo "      It uses pre-defined jobs instead of application_sequence."
        echo "      The pipeline will delegate to the existing jobs."
    fi

    echo ""
    echo "Application Sequence: ${APP_SEQUENCE}"
    echo ""
    echo "Initialization complete. Proceeding to execution..."

# ============================================================
# SUB-COMMAND: verify
# ============================================================
elif [[ "${SUB_COMMAND}" == "verify" ]]; then
    OVERALL_SUCCESS="${OVERALL_SUCCESS_VAR}"
    ACTION="${ACTION_VAR}"

    echo "============================================"
    echo " Verification"
    echo "============================================"
    echo " Action          : ${ACTION}"
    echo " Overall Success : ${OVERALL_SUCCESS}"
    echo "============================================"

    if [ "${OVERALL_SUCCESS}" = "true" ]; then
        echo "All operations completed successfully."
    else
        echo "WARNING: Some operations failed."
        echo "Review the orchestration task output for details."
        # Set pipeline-level property to indicate partial failure
        ectool setProperty "/myPipelineRuntime/hasFailures" "true"
    fi

# ============================================================
# SUB-COMMAND: summary
# ============================================================
elif [[ "${SUB_COMMAND}" == "summary" ]]; then
    ENV="${ENVIRONMENT_VAR}"
    ACTION="${ACTION_VAR}"
    NOTIFICATION="${SEND_NOTIFICATION_VAR}"

    echo "============================================"
    echo " APPNet Pipeline Complete"
    echo "============================================"
    echo " Environment   : ${ENV}"
    echo " Action        : ${ACTION}"
    echo " Completed At  : $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo " Notification  : ${NOTIFICATION}"
    echo "============================================"
    echo ""
    echo "Pipeline execution finished."
fi
