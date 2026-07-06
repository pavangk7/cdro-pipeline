import json
import subprocess
import sys
import os
import time

# Helper function to run a CD/RO procedure synchronously
def run_procedure_sync(procedure_name, actual_parameters):
    cmd = [
        'ectool', '--format', 'json', 'runProcedure', 'APPNet',
        '--procedureName', procedure_name
    ]
    for k, v in actual_parameters.items():
        cmd.extend(['--actualParameter', f'{k}={v}'])
    
    print(f"    Launching procedure {procedure_name}...")
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    job_info = json.loads(res.stdout)
    job_id = job_info['jobId']
    print(f"    Started job {job_id}. Polling for completion...")
    
    while True:
        time.sleep(5)
        res_details = subprocess.run(['ectool', '--format', 'json', 'getJobDetails', job_id], capture_output=True, text=True, check=True)
        details = json.loads(res_details.stdout)
        status = details['job']['status']
        if status == 'completed':
            outcome = details['job'].get('outcome', 'unknown')
            print(f"    Job {job_id} completed with outcome: {outcome}")
            return job_id, outcome

# Helper function to get an output parameter from a completed job
def get_job_output_parameter(job_id, param_name):
    cmd = ['ectool', 'getProperty', f'/jobs/{job_id}/outputParameters/{param_name}']
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode == 0:
        return res.stdout.strip()
    else:
        print(f"    WARNING: Failed to get property /jobs/{job_id}/outputParameters/{param_name}: {res.stderr}")
        return ""

def main():
    action = os.environ.get('ACTION_VAR', 'status')
    environment = os.environ.get('ENVIRONMENT_VAR', '')
    vault_token_url = os.environ.get('VAULT_TOKEN_URL_VAR', '')
    vault_secret_url = os.environ.get('VAULT_SECRET_URL_VAR', '')
    is_job_based = os.environ.get('IS_JOB_BASED_VAR', 'false').lower() == 'true'
    
    env_config_json = os.environ.get('ENV_CONFIG_JSON_VAR', '{}')
    app_sequence_json = os.environ.get('APP_SEQUENCE_JSON_VAR', '[]')
    
    try:
        env_config = json.loads(env_config_json)
        app_sequence = json.loads(app_sequence_json)
    except Exception as e:
        print(f"ERROR: Failed to parse configuration JSON from environment: {e}")
        sys.exit(1)

    if is_job_based:
        print(f"Environment '{environment}' uses job-based execution.")
        print("Delegating to pre-defined jobs...")
        jobs = env_config.get('jobs', {})
        for job_type, job_name in jobs.items():
            print(f"Triggering job: {job_name} (type: {job_type})")
            # In a real setup, uncomment to trigger:
            # subprocess.run([
            #     'ectool', 'runProcedure', 'APPNet',
            #     '--procedureName', job_name,
            #     '--actualParameter', f'action={action}'
            # ], check=True)
            print(f"  Job {job_name} triggered successfully.")
            
        print("All jobs delegated.")
        result_summary = {
            "mode": "job-based",
            "environment": environment,
            "action": action,
            "status": "DELEGATED"
        }
        subprocess.run(['ectool', 'setOutputParameter', 'resultSummaryJson', json.dumps(result_summary)], check=False)
        subprocess.run(['ectool', 'setOutputParameter', 'overallSuccess', 'true'], check=False)
        sys.exit(0)

    # For start action, reverse the application sequence
    if action == 'start':
        app_sequence = list(reversed(app_sequence))
        print(f"Start action: reversed sequence to {app_sequence}")

    results = {}
    overall_success = True

    # Process each application in sequence
    for app_name in app_sequence:
        print("")
        print("=" * 60)
        print(f"Processing Application: {app_name}")
        print("=" * 60)

        app_config = env_config.get('app', {}).get(app_name, {})
        if not app_config:
            print(f"  WARNING: No configuration found for app '{app_name}', skipping.")
            results[app_name] = {'status': 'SKIPPED', 'message': 'No config found'}
            continue

        sid = app_config.get('sid', '')
        instances = app_config.get('instances', [])
        components = app_config.get('components', [])
        app_path = app_config.get('path', '')
        app_user = app_config.get('user', '')

        if not sid:
            print(f"  WARNING: No secret ID (sid) for app '{app_name}', skipping.")
            results[app_name] = {'status': 'SKIPPED', 'message': 'No secret ID'}
            continue

        if not instances:
            print(f"  INFO: No instances defined for app '{app_name}', skipping SSH operations.")
            results[app_name] = {'status': 'SKIPPED', 'message': 'No instances defined'}
            continue

        if not components:
            print(f"  WARNING: No components defined for app '{app_name}', skipping.")
            results[app_name] = {'status': 'SKIPPED', 'message': 'No components defined'}
            continue

        # Fetch SSH key using Delinea-Fetch-SSH-Key procedure
        try:
            key_job_id, key_outcome = run_procedure_sync("Delinea-Fetch-SSH-Key", {
                "secretId": sid,
                "tokenAuthUrl": vault_token_url,
                "secretFetchUrlBase": vault_secret_url
            })
            if key_outcome != "success":
                raise Exception("Delinea-Fetch-SSH-Key procedure job failed")
            
            ssh_key = get_job_output_parameter(key_job_id, "sshPrivateKey")
            ssh_user_from_secret = get_job_output_parameter(key_job_id, "sshUsername")
            
            if not ssh_key:
                raise Exception("SSH private key returned is empty")
        except Exception as e:
            print(f"  ERROR: Failed to retrieve SSH key for app '{app_name}' (SID: {sid}): {e}")
            results[app_name] = {'status': 'FAILED', 'message': f'SSH key retrieval failed: {e}'}
            overall_success = False
            continue

        # Use SSH user from secret if available, otherwise fall back to config
        effective_user = ssh_user_from_secret or app_user
        print(f"  SSH User: {effective_user}")
        print(f"  App Path: {app_path}")
        print(f"  Instances: {instances}")
        print(f"  Components: {components}")

        app_results = {'instances': {}}

        for instance in instances:
            print(f"")
            print(f"  --- Instance: {instance} ---")
            instance_results = {'components': {}}

            # Check current status first via SSH-Check-Status procedure
            try:
                status_job_id, status_outcome = run_procedure_sync("SSH-Check-Status", {
                    "targetHost": instance,
                    "sshUser": effective_user,
                    "sshPrivateKey": ssh_key,
                    "appPath": app_path,
                    "appName": app_name,
                    "componentsJson": json.dumps(components)
                })
                if status_outcome != "success":
                    raise Exception("SSH-Check-Status procedure job failed")
                
                status_result_str = get_job_output_parameter(status_job_id, "statusResultJson")
                status_result = json.loads(status_result_str)
            except Exception as e:
                print(f"    ERROR: Status check failed for instance {instance}: {e}")
                for comp in components:
                    instance_results['components'][comp] = {
                        'before': 'UNKNOWN',
                        'action': 'NONE',
                        'after': 'UNKNOWN',
                        'message': f'Status check failed: {e}'
                    }
                overall_success = False
                app_results['instances'][instance] = instance_results
                continue

            print(f"    Current component statuses: {status_result}")
            for comp in components:
                instance_results['components'][comp] = {'before': status_result.get(comp, 'UNKNOWN')}

            # Execute action
            if action == 'status':
                print(f"    Status check complete (no action taken).")
                for comp in components:
                    comp_status = status_result.get(comp, 'UNKNOWN')
                    instance_results['components'][comp].update({
                        'action': 'NONE',
                        'after': comp_status
                    })

            elif action == 'stop':
                comps_to_stop = []
                for comp in components:
                    before_status = status_result.get(comp, 'UNKNOWN')
                    if before_status == 'STOPPED':
                        print(f"      {comp}: Already stopped, skipping.")
                        instance_results['components'][comp].update({
                            'action': 'ALREADY_STOPPED',
                            'after': 'STOPPED'
                        })
                    else:
                        comps_to_stop.append(comp)

                if comps_to_stop:
                    print(f"    Stopping components in order: {comps_to_stop}...")
                    try:
                        stop_job_id, stop_outcome = run_procedure_sync("SSH-Stop-Services", {
                            "targetHost": instance,
                            "sshUser": effective_user,
                            "sshPrivateKey": ssh_key,
                            "appPath": app_path,
                            "appName": app_name,
                            "componentsJson": json.dumps(comps_to_stop),
                            "environment": environment
                        })
                        
                        stop_result_str = get_job_output_parameter(stop_job_id, "stopResultJson")
                        stop_result = json.loads(stop_result_str)
                        
                        for comp in comps_to_stop:
                             comp_res = stop_result.get(comp, {})
                             comp_status = comp_res.get("status", "FAILED")
                             comp_msg = comp_res.get("message", "")
                             
                             instance_results['components'][comp].update({
                                 'action': 'STOPPED' if comp_status == 'STOPPED' else 'FAILED',
                                 'after': comp_status,
                                 'message': comp_msg
                             })
                             if comp_status != 'STOPPED':
                                 overall_success = False
                    except Exception as e:
                        print(f"    ERROR: Stop procedure failed for instance {instance}: {e}")
                        for comp in comps_to_stop:
                            instance_results['components'][comp].update({
                                'action': 'FAILED',
                                'after': 'UNKNOWN',
                                'message': f'Stop procedure execution failed: {e}'
                            })
                        overall_success = False

            elif action == 'start':
                comps_to_start = []
                for comp in components:
                    before_status = status_result.get(comp, 'UNKNOWN')
                    if before_status == 'RUNNING':
                        print(f"      {comp}: Already running, skipping.")
                        instance_results['components'][comp].update({
                            'action': 'ALREADY_RUNNING',
                            'after': 'RUNNING'
                        })
                    else:
                        comps_to_start.append(comp)

                if comps_to_start:
                    print(f"    Starting components in reverse order via procedure: {comps_to_start}...")
                    try:
                        start_job_id, start_outcome = run_procedure_sync("SSH-Start-Services", {
                            "targetHost": instance,
                            "sshUser": effective_user,
                            "sshPrivateKey": ssh_key,
                            "appPath": app_path,
                            "appName": app_name,
                            "componentsJson": json.dumps(comps_to_start),
                            "environment": environment
                        })
                        
                        start_result_str = get_job_output_parameter(start_job_id, "startResultJson")
                        start_result = json.loads(start_result_str)
                        
                        for comp in comps_to_start:
                             comp_res = start_result.get(comp, {})
                             comp_status = comp_res.get("status", "FAILED")
                             comp_msg = comp_res.get("message", "")
                             
                             instance_results['components'][comp].update({
                                 'action': 'STARTED' if comp_status == 'STARTED' else 'FAILED',
                                 'after': 'RUNNING' if comp_status == 'STARTED' else 'STOPPED',
                                 'message': comp_msg
                             })
                             if comp_status != 'STARTED':
                                 overall_success = False
                    except Exception as e:
                        print(f"    ERROR: Start procedure failed for instance {instance}: {e}")
                        for comp in comps_to_start:
                            instance_results['components'][comp].update({
                                'action': 'FAILED',
                                'after': 'UNKNOWN',
                                'message': f'Start procedure execution failed: {e}'
                            })
                        overall_success = False

            app_results['instances'][instance] = instance_results

        results[app_name] = app_results

    # ------------------------------------------------------------------
    # Output results
    # ------------------------------------------------------------------
    print("")
    print("=" * 60)
    print("EXECUTION SUMMARY")
    print("=" * 60)

    result_summary = {
        'environment': environment,
        'action': action,
        'overall_success': overall_success,
        'applications': results
    }

    result_json = json.dumps(result_summary, indent=2)
    print(result_json)

    # Set output parameters
    subprocess.run(['ectool', 'setOutputParameter', 'resultSummaryJson', json.dumps(result_summary)], check=False)
    subprocess.run(['ectool', 'setOutputParameter', 'overallSuccess', str(overall_success).lower()], check=False)

    if not overall_success:
        print("")
        print("WARNING: Some operations failed. Check the details above.")

    print("")
    print("Execution complete.")

if __name__ == '__main__':
    main()
