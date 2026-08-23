#!/usr/bin/env python3
"""Write and read the config.yml of the F-Droid repository.

The entrypoint script uses two modes:

    configure.py apply        Copy the .env values into config.yml.
    configure.py get KEY      Print one value from config.yml.

The "apply" mode reads its input from the environment. The entrypoint
exports every value before it calls this file.

FDROID_CONFIG sets a different path for config.yml. The default is the
path that the container uses.
"""

import os
import pathlib
import sys

import yaml

CONFIG = pathlib.Path(os.environ.get("FDROID_CONFIG", "/data/config.yml"))


def load():
    return yaml.safe_load(CONFIG.read_text(encoding="utf-8")) or {}


def apply():
    cfg = load()

    # Set on the first start only, because fdroid init does not write the
    # passwords when the keystore already exists.
    new_pass = os.environ.get("NEW_KEYSTORE_PASS", "")
    if new_pass:
        cfg["keystorepass"] = new_pass
        cfg["keypass"] = new_pass

    cfg["keystore"] = "/data/keystore.p12"
    cfg["repo_url"] = os.environ["REPO_URL"]
    cfg["repo_name"] = os.environ["FDROID_REPO_NAME"]
    cfg["repo_description"] = os.environ["FDROID_REPO_DESCRIPTION"]

    icon = os.environ.get("FDROID_REPO_ICON", "").strip()
    if icon:
        cfg["repo_icon"] = icon

    # archive_older = 0 turns the archive off. fdroid then keeps every version
    # in the main repository.
    older = int(os.environ.get("FDROID_ARCHIVE_OLDER", "0") or 0)
    if older > 0:
        cfg["archive_older"] = older
        cfg["archive_url"] = os.environ["ARCHIVE_URL"]
        cfg["archive_name"] = os.environ["FDROID_REPO_NAME"] + " (Archive)"
        cfg["archive_description"] = "Older versions of the apps in this repository."
    else:
        for key in ("archive_older", "archive_url", "archive_name", "archive_description"):
            cfg.pop(key, None)

    mirrors = [m.strip() for m in os.environ.get("FDROID_MIRRORS", "").split(",") if m.strip()]
    if mirrors:
        cfg["mirrors"] = mirrors
    else:
        cfg.pop("mirrors", None)

    # S3. fdroid deploy sends the files to <bucket>/fdroid/repo with rclone.
    bucket = os.environ.get("FDROID_S3_BUCKET", "").strip()
    remote = os.environ.get("FDROID_RCLONE_REMOTE", "").strip()
    if bucket:
        cfg["awsbucket"] = bucket
        if remote:
            cfg["rclone_config"] = [remote]
        else:
            cfg.pop("rclone_config", None)
    else:
        cfg.pop("awsbucket", None)
        cfg.pop("rclone_config", None)

    CONFIG.write_text(
        yaml.safe_dump(cfg, default_flow_style=False, sort_keys=True), encoding="utf-8"
    )
    print("[fdroid] config.yml updated from the .env file.")


def get(key):
    cfg = load()
    if key not in cfg:
        sys.exit(f'[fdroid] ERROR: config.yml has no key "{key}".')
    print(cfg[key])


def main(argv):
    if argv[1:] == ["apply"]:
        apply()
    elif len(argv) == 3 and argv[1] == "get":
        get(argv[2])
    else:
        sys.exit("[fdroid] ERROR: use \"configure.py apply\" or \"configure.py get KEY\".")


if __name__ == "__main__":
    main(sys.argv)
