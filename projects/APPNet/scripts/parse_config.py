import json
import sys
import subprocess
import os
import copy

def run_ectool(param_name, value):
    """Set a CD/RO output parameter via ectool."""
    subprocess.run(
        ["ectool", "setOutputParameter", param_name, value],
        check=True
    )

def main():
    config_file = os.environ.get("CONFIG_FILE_VAR", "config/environments.json")
    environment = os.environ.get("ENVIRONMENT_VAR", "")

    if not environment:
        print("ERROR: ENVIRONMENT_VAR environment variable not set.", file=sys.stderr)
        sys.exit(1)

    print("=== Parse-Config ===")
    print(f"Config file : {config_file}")
    print(f"Environment : {environment}")
    print("")

    # 1. Read the JSON config file
    try:
        with open(config_file, "r") as f:
            config = json.load(f)
    except FileNotFoundError:
        print(f"ERROR: Config file not found: {config_file}", file=sys.stderr)
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in config file: {e}", file=sys.stderr)
        sys.exit(1)

    # 2. Check the environment exists and is enabled
    available_envs = [k for k in config.keys() if k != 'global_config']

    if environment not in config:
        print(f"ERROR: Environment '{environment}' not found in config.", file=sys.stderr)
        print(f"Available environments: {available_envs}", file=sys.stderr)
        sys.exit(1)

    env_config = config[environment]

    # Check if environment is enabled (default to True if not specified)
    if not env_config.get("enabled", True):
        print(f"ERROR: Environment '{environment}' is disabled.", file=sys.stderr)
        sys.exit(1)

    print(f"Environment '{environment}' found and enabled.")

    # 3. Determine if job-based or sequence-based
    global_config = config.get("global_config", {})
    app_defaults = global_config.get("app_defaults", {})

    is_job_based = "jobs" in env_config
    is_sequence_based = "application_sequence" in env_config

    if is_job_based:
        print("Config style: job-based")
    elif is_sequence_based:
        print("Config style: sequence-based (application_sequence)")
    else:
        print("WARNING: Environment has neither 'jobs' nor 'application_sequence' key.", file=sys.stderr)

    # 4. If sequence-based, merge global defaults with env-specific overrides
    app_sequence = []
    resolved_env_config = copy.deepcopy(env_config)

    if is_sequence_based:
        app_sequence = env_config.get("application_sequence", [])
        env_apps = env_config.get("app", {})
        resolved_apps = {}

        for app_name in app_sequence:
            # Start with global defaults for this app
            defaults = copy.deepcopy(app_defaults.get(app_name, {}))
            # Get environment-specific overrides
            env_overrides = copy.deepcopy(env_apps.get(app_name, {}))

            # Merge: env overrides win for path, user; env provides sid, instances, components
            merged = defaults
            merged.update(env_overrides)

            resolved_apps[app_name] = merged

        resolved_env_config["app"] = resolved_apps
        print(f"Application sequence: {app_sequence}")
        print(f"Resolved {len(resolved_apps)} application configurations.")

    elif is_job_based:
        # For job-based configs, the app sequence is derived from jobs ordering
        jobs = env_config.get("jobs", {})
        if isinstance(jobs, dict):
            app_sequence = list(jobs.keys())
        elif isinstance(jobs, list):
            for job in jobs:
                if isinstance(job, dict):
                    name = job.get("app", job.get("name", ""))
                    if name:
                        app_sequence.append(name)
                elif isinstance(job, str):
                    app_sequence.append(job)
        print(f"Job-based sequence: {app_sequence}")

    # 5. Extract vault URLs from global_config.vault_config
    vault_config = global_config.get("vault_config", {})
    vault_token_auth_url = vault_config.get("token_auth_url", "")
    vault_secret_fetch_url_base = vault_config.get("secret_fetch_url_base", "")

    print(f"Vault token auth URL: {vault_token_auth_url}")
    print(f"Vault secret fetch URL base: {vault_secret_fetch_url_base}")

    # 6. Resolve notification list: env-specific > global fallback
    env_notifications = env_config.get("notifications", {})
    env_notification_list = env_notifications.get("distribution_list", "")

    global_notifications = global_config.get("notifications", {})
    global_notification_list = global_notifications.get("default_distribution_list", "")

    notification_list = env_notification_list if env_notification_list else global_notification_list

    print(f"Notification list: {notification_list}")

    # 7. Set all output parameters via ectool
    run_ectool("appSequenceJson", json.dumps(app_sequence))
    run_ectool("envConfigJson", json.dumps(resolved_env_config, indent=2))
    run_ectool("isJobBased", "true" if is_job_based else "false")
    run_ectool("notificationList", notification_list)
    run_ectool("vaultTokenAuthUrl", vault_token_auth_url)
    run_ectool("vaultSecretFetchUrlBase", vault_secret_fetch_url_base)

    print("")
    print("=== All output parameters set successfully ===")

if __name__ == '__main__':
    main()
