#!/bin/bash
set -euo pipefail

# ── Retrieve parameters from CloudBees CD/RO ────────────────
OPERATION="${OPERATION_VAR}"
TARGET_HOST="${TARGET_HOST_VAR}"
SSH_USER="${SSH_USER_VAR}"
SSH_PRIVATE_KEY="${SSH_PRIVATE_KEY_VAR}"
APP_PATH="${APP_PATH_VAR}"
APP_NAME="${APP_NAME_VAR}"
COMPONENTS_JSON="${COMPONENTS_JSON_VAR}"
ENVIRONMENT="${ENVIRONMENT_VAR:-}"

echo "=== SSH-Service-Action [Operation: ${OPERATION}] ==="
echo "Target Host   : ${TARGET_HOST}"
echo "SSH User      : ${SSH_USER}"
echo "App Path      : ${APP_PATH}"
echo "App Name      : ${APP_NAME}"
echo "Components    : ${COMPONENTS_JSON}"
if [[ -n "${ENVIRONMENT}" ]]; then
    echo "Environment   : ${ENVIRONMENT}"
fi
echo ""

# ── Write the SSH private key to a secure temp file ─────────
SSH_KEY_FILE=$(mktemp /tmp/ssh_key_XXXXXX)
cleanup() {
    if [[ -f "${SSH_KEY_FILE}" ]]; then
        rm -f "${SSH_KEY_FILE}"
        echo "Cleaned up temp SSH key file."
    fi
}
trap cleanup EXIT

echo "${SSH_PRIVATE_KEY}" > "${SSH_KEY_FILE}"
chmod 600 "${SSH_KEY_FILE}"

# ── Define reusable SSH command ─────────────────────────────
SSH_CMD="ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -i ${SSH_KEY_FILE} ${SSH_USER}@${TARGET_HOST}"
SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o LogLevel=ERROR"

# ── Parse components array ──────────────────────────────────
COMPONENTS_STOP_ORDER=()
while IFS= read -r comp; do
    COMPONENTS_STOP_ORDER+=("${comp}")
done < <(echo "${COMPONENTS_JSON}" | python3 -c "
import sys, json
components = json.load(sys.stdin)
for c in components:
    print(c)
")

if [[ ${#COMPONENTS_STOP_ORDER[@]} -eq 0 ]]; then
    echo "ERROR: No components parsed from componentsJson." >&2
    exit 1
fi

# Define Tomcat apps for status pattern detection
TOMCAT_APPS="APP1 APP2 APP3 SupportUI"

# ============================================================
# OPERATION: status
# ============================================================
if [[ "${OPERATION}" == "status" ]]; then
    declare -A STATUS_MAP
    RUNNING_COUNT=0
    STOPPED_COUNT=0
    TOTAL_COUNT=0

    for COMPONENT in "${COMPONENTS_STOP_ORDER[@]}"; do
        [[ -z "${COMPONENT}" ]] && continue
        TOTAL_COUNT=$((TOTAL_COUNT + 1))
        echo -n "Checking ${COMPONENT}... "

        IS_TOMCAT=false
        for TA in ${TOMCAT_APPS}; do
            if [[ "${APP_NAME}" == "${TA}" ]]; then
                IS_TOMCAT=true
                break
            fi
        done

        if [[ "${IS_TOMCAT}" == "true" ]]; then
            REMOTE_CMD="ps -ef | grep -w tomcat | grep -w '${COMPONENT}' | grep -v grep"
        else
            REMOTE_CMD="ps -ef | grep -w '${COMPONENT}' | grep -v grep"
        fi

        if ssh ${SSH_OPTS} -i "${SSH_KEY_FILE}" "${SSH_USER}@${TARGET_HOST}" "${REMOTE_CMD}" > /dev/null 2>&1; then
            STATUS_MAP["${COMPONENT}"]="RUNNING"
            RUNNING_COUNT=$((RUNNING_COUNT + 1))
            echo "RUNNING"
        else
            STATUS_MAP["${COMPONENT}"]="STOPPED"
            STOPPED_COUNT=$((STOPPED_COUNT + 1))
            echo "STOPPED"
        fi
    done

    # Build JSON output
    STATUS_JSON="{"
    FIRST=true
    for COMPONENT in "${!STATUS_MAP[@]}"; do
        if [[ "${FIRST}" == "true" ]]; then
            FIRST=false
        else
            STATUS_JSON+=","
        fi
        STATUS_JSON+="\"${COMPONENT}\":\"${STATUS_MAP[${COMPONENT}]}\""
    done
    STATUS_JSON+="}"

    if [[ ${RUNNING_COUNT} -eq ${TOTAL_COUNT} ]]; then
        OVERALL_STATUS="ALL_RUNNING"
    elif [[ ${STOPPED_COUNT} -eq ${TOTAL_COUNT} ]]; then
        OVERALL_STATUS="ALL_STOPPED"
    else
        OVERALL_STATUS="MIXED"
    fi

    ectool setOutputParameter 'statusResultJson' "${STATUS_JSON}"
    ectool setOutputParameter 'overallStatus' "${OVERALL_STATUS}"
    echo "Status JSON: ${STATUS_JSON}"
    echo "Overall Status: ${OVERALL_STATUS}"

# ============================================================
# OPERATION: stop
# ============================================================
elif [[ "${OPERATION}" == "stop" ]]; then
    ALL_STOPPED="true"
    RESULT_JSON="{"
    FIRST_ENTRY="true"

    for COMPONENT in "${COMPONENTS_STOP_ORDER[@]}"; do
        [[ -z "${COMPONENT}" ]] && continue
        echo -n "Stopping ${COMPONENT}... "
        STATUS="STOPPED"
        MESSAGE=""

        if [[ "${APP_NAME}" == "APP4" ]]; then
            CMD=". /home/netapp/.bash_profile; ${APP_PATH}/bin/clsnetRun.py --action stop --component ${COMPONENT} --force"
            if $SSH_CMD "${CMD}" 2>&1; then
                STOPPED="false"
                for i in $(seq 1 6); do
                    sleep 5
                    if ! $SSH_CMD "ps -ef | grep -w ${COMPONENT} | grep -v grep" 2>/dev/null | grep -q "${COMPONENT}"; then
                        STOPPED="true"
                        break
                    fi
                done
                if [[ "${STOPPED}" == "false" ]]; then
                    STATUS="FAILED"
                    MESSAGE="Process still running after 30s"
                    ALL_STOPPED="false"
                else
                    MESSAGE="Component stopped successfully"
                fi
            else
                STATUS="FAILED"
                MESSAGE="clsnetRun.py failed"
                ALL_STOPPED="false"
            fi
        else
            if [[ "${ENVIRONMENT}" == "SIT02" || "${ENVIRONMENT}" == "CIT02" || "${ENVIRONMENT}" == "ST" || "${ENVIRONMENT}" == "ST04" || "${ENVIRONMENT}" == "ST05" ]]; then
                CMD=". /home/${SSH_USER}/.bash_profile; ${APP_PATH}/startStop.py --action stop --component bank"
            else
                CMD=". /home/${SSH_USER}/.bash_profile; ${APP_PATH}/startStop.py --action stop --component bank; ${APP_PATH}/start"
            fi
            if $SSH_CMD "${CMD}" 2>&1; then
                STOPPED="false"
                for i in $(seq 1 12); do
                    sleep 5
                    if ! $SSH_CMD "ps -ef | grep -w ${COMPONENT} | grep -v grep" 2>/dev/null | grep -q "${COMPONENT}"; then
                        STOPPED="true"
                        break
                    fi
                done
                if [[ "${STOPPED}" == "false" ]]; then
                    STATUS="FAILED"
                    MESSAGE="Tomcat still running after 60s"
                    ALL_STOPPED="false"
                else
                    MESSAGE="Tomcat stopped successfully"
                fi
            else
                STATUS="FAILED"
                MESSAGE="startStop.py failed"
                ALL_STOPPED="false"
            fi
        fi

        if [[ "${FIRST_ENTRY}" == "true" ]]; then
            FIRST_ENTRY="false"
        else
            RESULT_JSON+=","
        fi
        SAFE_MESSAGE=$(echo "${MESSAGE}" | sed 's/"/\\"/g')
        RESULT_JSON+="\"${COMPONENT}\": {\"status\": \"${STATUS}\", \"message\": \"${SAFE_MESSAGE}\"}"
        echo "${STATUS} (${MESSAGE})"
    done
    RESULT_JSON+="}"

    ectool setOutputParameter stopResultJson "${RESULT_JSON}"
    ectool setOutputParameter allStopped "${ALL_STOPPED}"
    echo "Stop JSON: ${RESULT_JSON}"

# ============================================================
# OPERATION: start
# ============================================================
elif [[ "${OPERATION}" == "start" ]]; then
    # Reverse component list for startup
    COMPONENTS_START_ORDER=()
    for ((i=${#COMPONENTS_STOP_ORDER[@]}-1; i>=0; i--)); do
        COMPONENTS_START_ORDER+=("${COMPONENTS_STOP_ORDER[i]}")
    done

    ALL_STARTED="true"
    RESULT_JSON="{"
    FIRST_ENTRY="true"

    for COMPONENT in "${COMPONENTS_START_ORDER[@]}"; do
        [[ -z "${COMPONENT}" ]] && continue
        echo -n "Starting ${COMPONENT}... "
        STATUS="STARTED"
        MESSAGE=""

        if [[ "${APP_NAME}" == "APP4" ]]; then
            CMD=". /home/netapp/.bash_profile; ${APP_PATH}/bin/clsnetRun.py --action start --component ${COMPONENT} --reset"
            if $SSH_CMD "${CMD}" 2>&1; then
                STARTED="false"
                for i in $(seq 1 12); do
                    sleep 5
                    if $SSH_CMD "ps -ef | grep -w ${COMPONENT} | grep -v grep" 2>/dev/null | grep -q "${COMPONENT}"; then
                        STARTED="true"
                        break
                    fi
                done
                if [[ "${STARTED}" == "false" ]]; then
                    STATUS="FAILED"
                    MESSAGE="Process not found after 60s"
                    ALL_STARTED="false"
                else
                    MESSAGE="Component started successfully"
                fi
            else
                STATUS="FAILED"
                MESSAGE="clsnetRun.py failed"
                ALL_STARTED="false"
            fi
        else
            if [[ "${ENVIRONMENT}" == "SIT02" || "${ENVIRONMENT}" == "CIT02" || "${ENVIRONMENT}" == "ST" || "${ENVIRONMENT}" == "ST04" || "${ENVIRONMENT}" == "ST05" ]]; then
                CMD=". /home/${SSH_USER}/.bash_profile; ${APP_PATH}/startStop.py --action start --component bank"
            else
                CMD=". /home/${SSH_USER}/.bash_profile; ${APP_PATH}/startStop.py --action start --component bank; ${APP_PATH}/start"
            fi
            if $SSH_CMD "${CMD}" 2>&1; then
                STARTED="false"
                for i in $(seq 1 12); do
                    sleep 5
                    if $SSH_CMD "ps -ef | grep -w ${COMPONENT} | grep -v grep" 2>/dev/null | grep -q "${COMPONENT}"; then
                        STARTED="true"
                        break
                    fi
                done
                if [[ "${STARTED}" == "false" ]]; then
                    STATUS="FAILED"
                    MESSAGE="Tomcat process not found after 60s"
                    ALL_STARTED="false"
                else
                    MESSAGE="Tomcat started successfully"
                fi
            else
                STATUS="FAILED"
                MESSAGE="startStop.py failed"
                ALL_STARTED="false"
            fi
        fi

        if [[ "${FIRST_ENTRY}" == "true" ]]; then
            FIRST_ENTRY="false"
        else
            RESULT_JSON+=","
        fi
        SAFE_MESSAGE=$(echo "${MESSAGE}" | sed 's/"/\\"/g')
        RESULT_JSON+="\"${COMPONENT}\": {\"status\": \"${STATUS}\", \"message\": \"${SAFE_MESSAGE}\"}"
        echo "${STATUS} (${MESSAGE})"
    done
    RESULT_JSON+="}"

    ectool setOutputParameter startResultJson "${RESULT_JSON}"
    ectool setOutputParameter allStarted "${ALL_STARTED}"
    echo "Start JSON: ${RESULT_JSON}"
fi

echo "=== SSH-Service-Action complete ==="
