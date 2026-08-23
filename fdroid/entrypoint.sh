#!/bin/sh
# Entrypoint for the self-hosted F-Droid repository.
#
#   1. First start: make the signing key and config.yml.
#   2. Every start: apply the values from .env to config.yml.
#   3. Every start: index and sign the APK files found in repo/.
#   4. Optional: pull new APK files from an S3 prefix that CI writes to.
#   5. Optional: send the repository to an S3 bucket.
#   6. Then watch repo/ and repeat the steps when the APK files change.
set -eu

DATA=/data
CONFIG="$DATA/config.yml"
KEYSTORE="$DATA/keystore.p12"
INFO="$DATA/repo-info.txt"
CONFIGURE=/usr/local/lib/fdroid-configure.py

: "${FDROID_BASE_URL:?FDROID_BASE_URL is not set. Set it in the .env file.}"

# export is necessary: configure.py reads these from the environment.
export FDROID_REPO_NAME="${FDROID_REPO_NAME:-My F-Droid Repo}"
export FDROID_REPO_DESCRIPTION="${FDROID_REPO_DESCRIPTION:-A privately hosted F-Droid repository.}"
export FDROID_REPO_ICON="${FDROID_REPO_ICON:-}"
export FDROID_ARCHIVE_OLDER="${FDROID_ARCHIVE_OLDER:-0}"
export FDROID_MIRRORS="${FDROID_MIRRORS:-}"
export FDROID_S3_BUCKET="${FDROID_S3_BUCKET:-}"
export FDROID_RCLONE_REMOTE="${FDROID_RCLONE_REMOTE:-}"
export FDROID_S3_PULL_PREFIX="${FDROID_S3_PULL_PREFIX:-}"
FDROID_KEY_ALIAS="${FDROID_KEY_ALIAS:-myrepo}"
FDROID_KEY_DNAME="${FDROID_KEY_DNAME:-}"
FDROID_SCAN_INTERVAL="${FDROID_SCAN_INTERVAL:-60}"

# The repository is at <base>/fdroid/repo. This is true for the local web
# server, and also for an S3 bucket: rclone sends the files to
# <bucket>/fdroid/repo.
BASE="${FDROID_BASE_URL%/}"
export REPO_URL="$BASE/fdroid/repo"
export ARCHIVE_URL="$BASE/fdroid/archive"

log() { echo "[fdroid] $*"; }

cd "$DATA"

# ---------------------------------------------------------------------------
# Step 1 - first start only: make the signing key and config.yml.
# ---------------------------------------------------------------------------
if [ -f "$CONFIG" ] && [ ! -f "$KEYSTORE" ]; then
    log "ERROR: config.yml is present, but keystore.p12 is missing."
    log "ERROR: Put keystore.p12 back into data/ from your backup."
    log "ERROR: A new key makes all the installed clients fail."
    exit 1
fi

export NEW_KEYSTORE_PASS=""

if [ ! -f "$CONFIG" ]; then
    if [ ! -f "$KEYSTORE" ]; then
        log "No signing key found. Making a new 4096-bit RSA key."
        NEW_KEYSTORE_PASS=$(head -c 45 /dev/urandom | base64 | tr -d '\n/+=' | cut -c1-32)
        DNAME="${FDROID_KEY_DNAME:-CN=$FDROID_KEY_ALIAS, OU=F-Droid}"
        # keytool makes the key here, and not "fdroid init", because init
        # replaces the distinguished name with a default value. The options
        # are the same as the options that fdroidserver uses.
        FDROID_KEY_STORE_PASS="$NEW_KEYSTORE_PASS" \
        FDROID_KEY_PASS="$NEW_KEYSTORE_PASS" \
        keytool -genkey \
            -keystore "$KEYSTORE" \
            -alias "$FDROID_KEY_ALIAS" \
            -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
            -validity 10000 -storetype pkcs12 \
            -storepass:env FDROID_KEY_STORE_PASS \
            -keypass:env FDROID_KEY_PASS \
            -dname "$DNAME" \
            -J-Duser.language=en
        chmod 0600 "$KEYSTORE"
        log "Key made with this name: $DNAME"
    fi

    log "Making config.yml."
    # init finds the key that the step above made. It shows a warning about
    # the passwords. The Python step below sets those passwords.
    fdroid init --no-prompt \
        --keystore "$KEYSTORE" \
        --repo-keyalias "$FDROID_KEY_ALIAS"
    log "The password of the keystore goes into config.yml. Back up both files."
else
    log "Existing repository found. The signing key does not change."
fi

# ---------------------------------------------------------------------------
# Step 2 - every start: copy the .env values into config.yml.
# The key alias and the passwords stay unchanged.
# ---------------------------------------------------------------------------
python3 "$CONFIGURE" apply

# fdroid stops if other users can read the secrets.
chmod 0600 "$CONFIG"
if [ -f "$KEYSTORE" ]; then
    chmod 0600 "$KEYSTORE"
fi
mkdir -p "$DATA/repo" "$DATA/metadata" "$DATA/archive"

# ---------------------------------------------------------------------------
# Check the S3 settings before the first upload.
# ---------------------------------------------------------------------------
if [ -z "$FDROID_S3_BUCKET" ] && [ -n "$FDROID_S3_PULL_PREFIX" ]; then
    log "ERROR: FDROID_S3_PULL_PREFIX is set, but FDROID_S3_BUCKET is empty."
    log "ERROR: The pull step needs the name of the bucket."
    exit 1
fi

if [ -n "$FDROID_S3_BUCKET" ]; then
    if [ -f "$DATA/rclone.conf" ] && [ -z "$FDROID_RCLONE_REMOTE" ]; then
        log "ERROR: data/rclone.conf is present, but FDROID_RCLONE_REMOTE is empty."
        log "ERROR: Set FDROID_RCLONE_REMOTE to a section name from that file."
        exit 1
    fi
    if [ ! -f "$DATA/rclone.conf" ] && [ -z "$FDROID_RCLONE_REMOTE" ]; then
        if [ -z "${AWS_ACCESS_KEY_ID:-}" ] || [ -z "${AWS_SECRET_ACCESS_KEY:-}" ]; then
            log "ERROR: S3 is on, but there are no credentials."
            log "ERROR: Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY in .env,"
            log "ERROR: or supply data/rclone.conf and FDROID_RCLONE_REMOTE."
            exit 1
        fi
        log "S3 target: bucket \"$FDROID_S3_BUCKET\" on AWS, region us-east-1."
        log "For a different region or a different S3 service, use data/rclone.conf."
    else
        log "S3 target: bucket \"$FDROID_S3_BUCKET\" through rclone remote \"$FDROID_RCLONE_REMOTE\"."
    fi
fi

# ---------------------------------------------------------------------------
# Step 3 - read the key fingerprint. Users need it to add the repository.
# ---------------------------------------------------------------------------
KEY_ALIAS=$(python3 "$CONFIGURE" get repo_keyalias)
KEY_PASS=$(python3 "$CONFIGURE" get keystorepass)

FINGERPRINT=$(FDROID_KEY_STORE_PASS="$KEY_PASS" keytool -list -v \
    -keystore "$KEYSTORE" -storetype pkcs12 \
    -storepass:env FDROID_KEY_STORE_PASS -alias "$KEY_ALIAS" \
    -J-Duser.language=en 2>/dev/null \
    | awk '/SHA256:/ {print $2; exit}' | tr -d ':')

if [ -z "$FINGERPRINT" ]; then
    log "WARNING: The fingerprint could not be read from the keystore."
    ADD_URL="$REPO_URL"
else
    ADD_URL="$REPO_URL?fingerprint=$FINGERPRINT"
fi

{
    echo "Repository URL : $REPO_URL"
    echo "Fingerprint    : $FINGERPRINT"
    echo ""
    echo "Give this link to the users of the F-Droid client app:"
    echo "$ADD_URL"
} > "$INFO"

# ---------------------------------------------------------------------------
# Step 4 and 5 - index, sign, send to S3, then watch for new files.
# ---------------------------------------------------------------------------
snapshot() {
    find "$DATA/repo" -maxdepth 1 -name '*.apk' -printf '%p %s %T@\n' 2>/dev/null \
        | sort | sha256sum
}

# Build the rclone source, and the extra arguments, for the bucket.
rclone_source() {
    if [ -n "$FDROID_RCLONE_REMOTE" ]; then
        echo "${FDROID_RCLONE_REMOTE}:${FDROID_S3_BUCKET}/${1}"
    else
        # An rclone connection string. It reads the keys from the environment,
        # and it uses the same region as "fdroid deploy" uses.
        echo ":s3,provider=AWS,env_auth=true,region=us-east-1:${FDROID_S3_BUCKET}/${1}"
    fi
}

# Copy the APK files that the CI job put in the bucket into repo/.
# This is "copy", not "sync": it never deletes a local file.
pull_from_s3() {
    [ -n "$FDROID_S3_PULL_PREFIX" ] || return 0

    set -- copy
    if [ -n "$FDROID_RCLONE_REMOTE" ]; then
        set -- "$@" --config "$DATA/rclone.conf"
    fi
    # rclone reads the filters in order, and the first match wins. Therefore
    # the exclude must come first. latest.apk is a copy of a file that already
    # has a version in its name, and a copy would be a duplicate here.
    set -- "$@" \
        --exclude 'latest.apk' \
        --include '*.apk' \
        --no-traverse \
        "$(rclone_source "$FDROID_S3_PULL_PREFIX")" \
        "$DATA/repo"

    log "Looking for new APK files in \"$FDROID_S3_BUCKET/$FDROID_S3_PULL_PREFIX\"."
    if rclone "$@"; then
        return 0
    fi
    log "WARNING: The pull from S3 failed. The local files stay in use."
    return 1
}

deploy_to_s3() {
    [ -n "$FDROID_S3_BUCKET" ] || return 0
    log "Sending the repository to the S3 bucket \"$FDROID_S3_BUCKET\"."
    # Caution: this is a mirror operation. rclone removes the files in the
    # bucket that are not in data/repo.
    if fdroid deploy; then
        log "The S3 bucket is up to date."
        return 0
    fi
    log "ERROR: fdroid deploy failed. The files in the bucket did not change."
    return 1
}

run_update() {
    log "Indexing and signing the repository."
    if ! fdroid update --create-metadata --pretty; then
        log "ERROR: fdroid update failed. The previous index stays in use."
        return 1
    fi
    log "The repository is ready."
    log "Add URL: $ADD_URL"
    deploy_to_s3 || return 1
    return 0
}

pull_from_s3 || true
run_update || true

if [ "$FDROID_SCAN_INTERVAL" -le 0 ]; then
    log "FDROID_SCAN_INTERVAL is 0. The container stops now."
    exit 0
fi

log "Watching $DATA/repo every ${FDROID_SCAN_INTERVAL}s for new APK files."
LAST=$(snapshot)
while true; do
    sleep "$FDROID_SCAN_INTERVAL"
    pull_from_s3 || true
    CURRENT=$(snapshot)
    if [ "$CURRENT" != "$LAST" ]; then
        log "A change was found in repo/."
        if run_update; then
            LAST="$CURRENT"
        fi
    fi
done
