import sys
import os
import subprocess
import json
import urllib.request
import urllib.parse
import ssl

def run_ectool(param_name, value):
    """Set a CD/RO output parameter via ectool."""
    subprocess.run(
        ["ectool", "setOutputParameter", param_name, value],
        check=True
    )

def main():
    print("=== Delinea SSH Key Retrieval ===")

    token_auth_url = os.environ.get("TOKEN_AUTH_URL_VAR", "")
    secret_fetch_url_base = os.environ.get("SECRET_FETCH_URL_BASE_VAR", "")
    secret_id = os.environ.get("SECRET_ID_VAR", "")

    if not token_auth_url or not secret_fetch_url_base or not secret_id:
        print("ERROR: Missing one of the required environment variables: TOKEN_AUTH_URL_VAR, SECRET_FETCH_URL_BASE_VAR, SECRET_ID_VAR", file=sys.stderr)
        sys.exit(1)

    # 1. Retrieve client credentials from the CD/RO credential store
    try:
        res_id = subprocess.run(["ectool", "getFullCredential", "delinea-svc-account", "--value", "userName"], capture_output=True, text=True, check=True)
        client_id = res_id.stdout.strip()
        
        res_sec = subprocess.run(["ectool", "getFullCredential", "delinea-svc-account", "--value", "password"], capture_output=True, text=True, check=True)
        client_secret = res_sec.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"ERROR: Failed to retrieve Delinea service account credentials: {e}", file=sys.stderr)
        sys.exit(1)

    if not client_id or not client_secret:
        print("ERROR: Retrieved Delinea service account credentials are empty.", file=sys.stderr)
        sys.exit(1)

    print("Client credentials retrieved successfully.")
    print("Requesting access token...")

    # Configure SSL context to ignore certification verification (aligns with -k curl flag)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    # 2. Get the OAuth2 access token
    post_data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "scope": "xpmheadless",
        "client_id": client_id,
        "client_secret": client_secret
    }).encode("utf-8")

    req_auth = urllib.request.Request(
        token_auth_url,
        data=post_data,
        headers={"Content-Type": "application/x-www-form-urlencoded"}
    )

    try:
        with urllib.request.urlopen(req_auth, context=ctx) as response:
            resp_body = response.read().decode("utf-8")
            data = json.loads(resp_body)
            token = data.get("access_token", "")
    except Exception as e:
        print(f"ERROR: Failed to authenticate with Delinea: {e}", file=sys.stderr)
        sys.exit(1)

    if not token:
        print("ERROR: Access token is empty in response.", file=sys.stderr)
        sys.exit(1)

    print("Access token obtained successfully. Fetching secret details...")

    # 3. Fetch the secret payload using the access token
    fetch_url = f"{secret_fetch_url_base}/{secret_id}"
    req_secret = urllib.request.Request(
        fetch_url,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }
    )

    try:
        with urllib.request.urlopen(req_secret, context=ctx) as response:
            resp_body = response.read().decode("utf-8")
            secret_data = json.loads(resp_body)
    except Exception as e:
        print(f"ERROR: Failed to fetch secret from Delinea: {e}", file=sys.stderr)
        sys.exit(1)

    # 4. Parse the secret payload to extract the SSH private key and username
    items = secret_data.get("items", [])
    if not items:
        print("ERROR: No 'items' array found in secret response JSON.", file=sys.stderr)
        sys.exit(1)

    ssh_private_key = ""
    ssh_username = ""

    for item in items:
        slug = item.get("slug", "").lower()
        field_name = item.get("fieldName", "")
        item_value = item.get("itemValue", "")

        # Match the private key field
        if slug == "private-key" or "Private Key" in field_name:
            ssh_private_key = item_value

        # Match the username field
        if slug == "username" or field_name == "Username":
            ssh_username = item_value

    if not ssh_private_key:
        print("ERROR: SSH private key not found in secret items.", file=sys.stderr)
        print(f"Available slugs: {[i.get('slug','') for i in items]}", file=sys.stderr)
        sys.exit(1)

    print(f"SSH Username: {ssh_username if ssh_username else '(not found)'}")
    print("SSH key and username parsed successfully.")

    # 5. Set output parameters in CD/RO
    run_ectool("sshPrivateKey", ssh_private_key)
    run_ectool("sshUsername", ssh_username)

    print("=== Delinea SSH Key Retrieval complete ===")

if __name__ == "__main__":
    main()
