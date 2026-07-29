import os
import sys
import json
import glob
import subprocess
import boto3
from botocore.client import Config

# R2 Configuration from secrets
R2_ACCESS_KEY = os.environ.get('R2_ACCESS_KEY')
R2_SECRET_KEY = os.environ.get('R2_SECRET_KEY')
R2_ENDPOINT = os.environ.get('R2_ENDPOINT')
R2_BUCKET = os.environ.get('R2_BUCKET')
R2_PUBLIC_URL = os.environ.get('R2_PUBLIC_URL', 'https://pub-xxxx.r2.dev')

# CI environment (GitLab)
EVENT = os.environ.get('CI_PIPELINE_SOURCE', 'push')
TAG = os.environ.get('CI_COMMIT_TAG')
COMMIT_SHA = os.environ.get('CI_COMMIT_SHA', 'unknown')[:8]
REPO_URL = os.environ.get('CI_REPOSITORY_URL')
# Use provided token or fall back to automatic CI_JOB_TOKEN
GITLAB_TOKEN = os.environ.get('GITLAB_TOKEN') or os.environ.get('CI_JOB_TOKEN')

# Project branches
WEBSITE_BRANCH = "website"

def get_git_output(command):
    try:
        return subprocess.check_output(command, shell=True).decode().strip()
    except:
        return ""

def upload_to_r2(file_path, target_dir):
    print(f"Uploading {file_path} to R2 folder {target_dir}...")
    s3 = boto3.resource('s3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='auto'
    )
    bucket = s3.Bucket(R2_BUCKET)

    file_name = os.path.basename(file_path)
    key = f"{target_dir}/{file_name}"
    bucket.upload_file(file_path, key)

    return f"{R2_PUBLIC_URL}/{key}"

def clear_r2_dir(target_dir):
    print(f"Cleaning R2 directory: {target_dir}")
    s3 = boto3.resource('s3',
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version='s3v4'),
        region_name='auto'
    )
    bucket = s3.Bucket(R2_BUCKET)
    bucket.objects.filter(Prefix=f"{target_dir}/").delete()

def main():
    if not all([R2_ACCESS_KEY, R2_SECRET_KEY, R2_ENDPOINT, R2_BUCKET]):
        print("Error: Missing R2 configuration secrets!")
        return

    is_tag = TAG is not None
    channel = "stable" if is_tag else "nightly"
    target_dir = "VPN-Next" if is_tag else "VPN-Next-TEST"
    build_types = ["release"] if is_tag else ["debug", "release"]
    # Only publish official builds to OTA
    flavor = "stableStandard" if is_tag else "nightlyStandard"

    # 1. Clear target R2 directory
    clear_r2_dir(target_dir)

    # 2. Prepare metadata
    commit_count = int(get_git_output("git rev-list --count HEAD") or "0")
    version_code = 605159512 + commit_count
    version_name_base = TAG if is_tag else get_git_output("git describe --tags --always")
    changelog = get_git_output("git log -1 --pretty=%B")

    apk_info = []
    for bt in build_types:
        apk_pattern = f"app/build/outputs/apk/{flavor}/{bt}/*.apk"
        apk_files = glob.glob(apk_pattern)
        if not apk_files:
            print(f"Warning: No APK found for {flavor}/{bt} at {apk_pattern}")
            continue

        apk_path = apk_files[0]
        version_name = version_name_base + ("-nightly" if not is_tag else "")

        # Upload to R2
        public_apk_url = upload_to_r2(apk_path, target_dir)
        apk_info.append((bt, public_apk_url, version_name))

    if not apk_info:
        print("No APKs to publish. Exiting.")
        return

    # 3. Update website JSON
    print("Updating update.json on website branch...")
    if os.path.exists("website_repo"):
        subprocess.run("rm -rf website_repo", shell=True)

    if GITLAB_TOKEN and REPO_URL and "://" in REPO_URL:
        proto, rest = REPO_URL.split("://", 1)
        # Handle cases where REPO_URL already has a token/user
        if "@" in rest:
            _, host_path = rest.split("@", 1)
        else:
            host_path = rest

        # Use gitlab-ci-token as username for CI_JOB_TOKEN compatibility
        user = "gitlab-ci-token" if not os.environ.get('GITLAB_TOKEN') else "oauth2"
        auth_repo_url = f"{proto}://{user}:{GITLAB_TOKEN}@{host_path}"
    else:
        auth_repo_url = REPO_URL

    if not auth_repo_url:
        print("Error: Could not determine repository URL for website update.")
        return

    clone_res = subprocess.run(f"git clone --branch {WEBSITE_BRANCH} {auth_repo_url} website_repo", shell=True)
    if clone_res.returncode != 0:
        print(f"Error: Failed to clone {WEBSITE_BRANCH} branch. Ensure it exists.")
        return

    json_path = "website_repo/public/update.json"
    os.makedirs(os.path.dirname(json_path), exist_ok=True)

    data = {}
    if os.path.exists(json_path):
        with open(json_path, 'r') as f:
            try:
                data = json.load(f)
            except:
                data = {}

    # Clean root from any non-channel keys (like ghost 'debug' blocks)
    data = {k: v for k, v in data.items() if k in ["stable", "nightly"]}

    # Reset current channel data to prevent stale build types
    data[channel] = {}

    for bt, apk_url, vn in apk_info:
        data[channel][bt] = {
            "versionCode": int(version_code),
            "versionName": vn,
            "url": apk_url,
            "changelog": changelog,
            "force": False
        }

    with open(json_path, 'w') as f:
        json.dump(data, f, indent=2)

    subprocess.run("git config --global user.email 'ci@protonmod.next'", shell=True)
    subprocess.run("git config --global user.name 'CI Bot'", shell=True)
    subprocess.run(f"cd website_repo && git add . && git commit -m 'chore: update ota metadata for {channel} channel' && git push origin {WEBSITE_BRANCH}", shell=True)

    github_token = os.environ.get('GITHUB_TOKEN')
    if github_token:
        print("Pushing to GitHub mirror...")
        # Use 'git' as username for PAT, it's more stable
        mirror_url = f"https://git:{github_token}@github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR.git"

        # Add mirror remote
        subprocess.run(f"cd website_repo && git remote add mirror {mirror_url}", shell=True)

        # Try to push, but don't fail the whole script if it fails
        result = subprocess.run(f"cd website_repo && git push mirror {WEBSITE_BRANCH} --force",
                                shell=True, capture_output=True, text=True)

        if result.returncode == 0:
            print("Successfully pushed to GitHub mirror.")
        else:
            print("Failed to push to GitHub mirror.")
            # Mask token in error output
            clean_error = result.stderr.replace(github_token, "***")
            print(f"Error: {clean_error}")
            print("Check if the GITHUB_TOKEN has 'Contents: Read and Write' permissions.")

if __name__ == '__main__':
    main()
