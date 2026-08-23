# FDROID.md

How to operate the self-hosted F-Droid repository.

---

## 1. Purpose

This setup holds a **binary repository**. You supply the APK files. The server
does not build apps from source code. Therefore the server does not need the
full Android SDK, and the image stays small.

There is no long-running server. The GitHub Actions workflow builds and signs
the app, then runs the indexer once to sign and update the repository, then
publishes the result to an S3-compatible bucket. The bucket serves the
repository directly. An optional Cloudflare Worker sits in front of the
bucket to give it a custom domain and edge caching.

All the files are in the `fdroid/` directory:

| File | Function |
|---|---|
| `compose.yml` | Runs the indexer locally, for testing before a push. |
| `Dockerfile` | The Alpine image that holds the `fdroidserver` tools. CI builds and runs this same image. |
| `entrypoint.sh` | The steps the indexer runs: make/read the key, sign, write the index. |
| `.env.example` | The configuration template for local testing. Copy this file. |
| `metadata/` | Per-app metadata YAML, tracked in git. See section 5. |
| `worker/` | The optional Cloudflare Worker that proxies the bucket. See section 9. |
| `data/` | Local-only working directory: the signing key, the APK files and the index. Never committed. |

---

## 2. Before you start

You must have these items:

1. An S3-compatible bucket with public read access, and access keys that can
   write to it.
2. Docker, to run `docker compose` once locally and bootstrap the signing key
   (see section 4). You do not need Docker anywhere after that; CI builds and
   runs the same image.
3. Optionally, a Cloudflare account and a domain, if you want the repository
   on your own domain instead of the bucket's raw URL. See section 9.

---

## 3. How it works

On every push to `main`, the `.github/workflows/android-s3-release.yml`
workflow does these steps, in the same job that builds the app:

1. It builds and signs the release APK, as before.
2. It restores the repository's signing key from two GitHub secrets (section
   4), and the previous `repo/` and `archive/` content from the bucket
   (section 10), into a scratch `fdroid/data/` directory.
3. It restores `fdroid/metadata/` (tracked in git) into `fdroid/data/metadata/`.
4. It copies the APK it just built straight into `fdroid/data/repo/`.
5. It builds the `fdroid/Dockerfile` image and runs it once
   (`FDROID_SCAN_INTERVAL=0`): the indexer signs the APK files in
   `data/repo/` and writes the index. It does not talk to S3 itself; the
   workflow does that in the next step, so any S3-compatible endpoint works
   without extra `rclone` configuration.
6. It publishes `data/repo/` and `data/archive/` to the bucket with
   `aws s3 sync --delete`, mirroring what the indexer just produced.

The signing key never leaves the two GitHub secrets and the ephemeral CI
runner. It is not part of `data/repo/`, so it is never uploaded to the
bucket.

The index is signed. Therefore the bucket can be fully public - a client
rejects an index with the wrong signature.

---

## 4. One-time setup

**Caution: Step 1 makes the signing key. Back it up as soon as it exists -
see section 7. If you lose it, you cannot update the repository; you must
make a new one, and every user must remove the old repository and add the
new one.**

1. Bootstrap the signing key locally.

   ```bash
   cd fdroid
   cp .env.example .env
   ```

   Open `.env` and set `FDROID_BASE_URL` (use your future Worker domain if
   you're setting one up, otherwise the bucket's public URL) and
   `FDROID_REPO_NAME`. Leave the S3 values empty for this step.

   ```bash
   docker compose up -d
   docker compose logs fdroid
   docker compose down
   ```

   This creates `fdroid/data/keystore.p12` and `fdroid/data/config.yml`.

2. Turn those two files into GitHub repository secrets.

   ```bash
   base64 -w0 data/keystore.p12   # -> FDROID_KEYSTORE_BASE64
   base64 -w0 data/config.yml     # -> FDROID_CONFIG_YML_BASE64
   ```

   Add both as **secrets** (Settings > Secrets and variables > Actions >
   Secrets) on the GitHub repository, alongside the existing
   `ANDROID_KEYSTORE_BASE64` and the AWS keys.

3. Set the GitHub Actions **variables** (same page, Variables tab):

   | Variable | Function |
   |---|---|
   | `FDROID_BASE_URL` | The public address users add to their F-Droid client. Your Worker's custom domain, or the bucket's public URL if you skip the Worker. |
   | `FDROID_REPO_NAME` | The name the client app shows. Optional. |
   | `FDROID_REPO_DESCRIPTION` | A short text about the repository. Optional. |
   | `FDROID_MIRRORS` | Comma-separated fallback addresses. If `FDROID_BASE_URL` is your Worker domain, put the bucket's raw public URL here. Optional. |
   | `FDROID_ARCHIVE_OLDER` | Versions to keep in the main repo before moving older ones to an archive. `0` (the default if unset) keeps every version. Optional. |
   | `S3_BUCKET`, `S3_ENDPOINT_URL`, `AWS_REGION` | Already set for the APK upload step; reused here. |

   `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` are the existing secrets;
   the key needs write access to the `fdroid/` prefix in the bucket too.

4. Push to `main`. The workflow builds the APK, then signs and publishes the
   repository. Check the run's summary for the `Add URL` and fingerprint.

5. Optionally, set up the Cloudflare Worker (section 9) so `FDROID_BASE_URL`
   can be your own domain instead of the bucket's URL.

---

## 5. Add an app

Nothing to do for a normal release: pushing to `main` builds, signs and
publishes automatically.

To set the name, description, icon and screenshots the F-Droid client shows:

1. The first time CI indexes a new package, it creates a blank metadata file
   inside its own throwaway `data/metadata/`, but that copy is discarded
   with the rest of the CI runner - it is not committed anywhere. Run the
   indexer locally once to get a starting copy:

   ```bash
   cd fdroid
   docker compose up -d
   docker compose logs fdroid
   docker compose down
   ```

2. Copy the generated file into the git-tracked directory and edit it:

   ```bash
   cp data/metadata/org.hikyaku.mobile.yml metadata/org.hikyaku.mobile.yml
   ```

   ```yaml
   AuthorName: Example Org
   Name: My Application
   Summary: A short line of text.
   Description: |-
       A longer text. This text can have more than one paragraph.
   License: Apache-2.0
   WebSite: https://example.com
   ```

3. Commit `fdroid/metadata/org.hikyaku.mobile.yml`. From then on, every CI
   run restores this file before indexing, so your edits stick.

4. To add an icon and screenshots, add these files locally under
   `fdroid/data/repo/`, then let a normal push carry them to the bucket:

   ```
   fdroid/data/repo/org.hikyaku.mobile/en-US/icon.png
   fdroid/data/repo/org.hikyaku.mobile/en-US/phoneScreenshots/1.png
   ```

   Unlike the metadata YAML, these do not need git tracking: they live under
   `data/repo/`, which CI already restores from and publishes back to the
   bucket on every run (the same round-trip that keeps old APKs from being
   deleted, section 3 step 2). Add them once - locally with `docker compose
   up -d`, or by uploading them straight to `s3://<bucket>/fdroid/repo/...`
   - and every future CI run keeps them.

**Note: to remove an app, delete its APK naming convention from future
releases and delete its metadata file. The already-published APK stays in
the bucket until you manually delete it there.**

---

## 6. Give the repository to the users

The users need the address and the fingerprint. The fingerprint prevents an
attack from a different server.

Find the two values in the latest workflow run's summary (Actions tab), or
fetch them directly:

```bash
curl https://<your-fdroid-base-url>/fdroid/repo-info.txt
```

The file shows a link with this format:

```
https://apps.example.com/fdroid/repo?fingerprint=A1B2C3...
```

Give this link to the users. The users then do these three steps:

1. Install the F-Droid client from `https://f-droid.org`.
2. Open the link on the Android device.
3. Touch **Add** in the F-Droid client.

The users can also use the menu **Settings > Repositories > Add**. But the
link is safer, because the link contains the fingerprint.

---

## 7. Back up the signing key

**Warning: If you lose the signing key, you cannot update the repository. You
must then make a new repository. All the users must remove the old repository
and add the new one.**

The signing key lives in two places, and both matter:

1. The GitHub repository secrets `FDROID_KEYSTORE_BASE64` and
   `FDROID_CONFIG_YML_BASE64` (section 4). These are what CI uses. They are
   **write-only** - GitHub never lets you read a secret's value back.
2. Your own offline copy of `fdroid/data/keystore.p12` and
   `fdroid/data/config.yml` from when you ran the bootstrap in section 4.
   Keep the two files together, in a safe place (a password manager or an
   encrypted archive). Do not put them in Git; `fdroid/.gitignore` prevents
   this.

If you only have (1) and GitHub's secrets are ever lost (repository deleted,
secret rotated by mistake), you cannot recover the key - keep (2) regardless
of how confident you are in GitHub's durability.

To restore after a local disaster, put the two files back into
`fdroid/data/`, or re-add the two GitHub secrets from your offline copy - CI
picks them up on the next push.

---

## 8. The configuration values

### GitHub Actions (used by every push)

See section 4 for the full list of secrets and variables.

### Local testing (`fdroid/.env`, used by `docker compose`)

| Value | Function |
|---|---|
| `FDROID_BASE_URL` | The public address of the repository. Do not add a path. The repository becomes `<value>/fdroid/repo`. |
| `FDROID_S3_BUCKET` | The name of the bucket. Leave empty to index locally only, without publishing. |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | The access keys, for AWS S3 in `us-east-1`. |
| `FDROID_RCLONE_REMOTE` | The section name in `data/rclone.conf`, for a non-AWS S3-compatible host. See section 10. |
| `FDROID_S3_PULL_PREFIX` | Optional. A bucket prefix to copy APK files from before indexing. Unused by CI, which copies the freshly-built APK in directly. |
| `FDROID_REPO_NAME`, `FDROID_REPO_DESCRIPTION`, `FDROID_REPO_ICON` | Repository details shown in the client app. |
| `FDROID_KEY_ALIAS`, `FDROID_KEY_DNAME` | Used once, when the key is made on the first start. A change after that has no effect. |
| `FDROID_ARCHIVE_OLDER` | Recent versions to keep in the main repository; `0` keeps every version. |
| `FDROID_SCAN_INTERVAL` | Seconds between checks of `data/repo/`. `0` runs one time and stops - this is what CI uses. |
| `FDROID_MIRRORS` | Other addresses that hold a copy. Comma-separated. |

---

## 9. The Cloudflare Worker proxy

`fdroid/worker/` holds a small Worker that reverse-proxies the S3-compatible
bucket. It exists only to give the repository a custom domain and edge
caching; the bucket stays the source of truth and keeps serving the files
directly. Because of that, you only deploy the Worker when you set it up or
change its logic - never as part of a normal release.

1. Set the bucket's public base URL in `fdroid/worker/wrangler.jsonc`:

   ```jsonc
   "vars": {
     "ORIGIN_BASE_URL": "https://my-bucket.s3.eu-west-1.example.com"
   }
   ```

2. Deploy it:

   ```bash
   cd fdroid/worker
   npm install
   npx wrangler login
   npx wrangler deploy
   ```

3. In the Cloudflare dashboard, add your domain as a custom domain on the
   Worker (Workers & Pages > the Worker > Settings > Domains & Routes).

4. Set `FDROID_BASE_URL` (GitHub Actions variable, section 4) to that domain,
   and add the bucket's raw URL to `FDROID_MIRRORS` as a fallback. Push to
   `main` to re-sign the index with the new URL.

The Worker only forwards `GET`/`HEAD`, sets the same cache lifetimes the
repository needs (short for the index, which changes every release; long and
immutable for APK files, which never change under the same name), and
refuses to forward a request for the signing key or its config even though
CI never uploads those to the bucket in the first place. It holds no files
itself and needs no secrets, since the bucket is public read.

---

## 10. Publish to S3

The bucket must permit public read access for the `fdroid/` prefix. The index
is signed, so public read access is safe.

### A. AWS S3, or a generic S3-compatible host, from CI

This is what the GitHub Actions workflow uses (section 3). It publishes with
the AWS CLI's `--endpoint-url`, so any S3-compatible host works - set
`S3_ENDPOINT_URL` and `AWS_REGION` as GitHub Actions variables. No `rclone`
configuration is needed for this path.

### B. From the indexer directly, for local testing

`docker compose` (section 4, 5, 11) uses the indexer's own S3 support
instead, since there is no separate publish step locally.

For AWS S3 in `us-east-1`, two keys in `.env` are sufficient. For a different
region or a different S3-compatible host (MinIO, Cloudflare R2, Backblaze B2,
Wasabi, ...), make `fdroid/data/rclone.conf`:

```ini
[myhost]
type = s3
provider = Other
region = auto
endpoint = https://s3.example.com
access_key_id = ...
secret_access_key = ...
```

and set `FDROID_RCLONE_REMOTE=myhost` in `.env`.

**Note: `data/rclone.conf` holds your keys. It is not committed, and not
published.**

---

## 11. Test locally before you push

Do these commands in the `fdroid` directory, after `cp .env.example .env`
and filling it in (section 4).

Run the indexer once, the same way CI does:

```bash
docker compose run --rm -e FDROID_SCAN_INTERVAL=0 fdroid
```

Show the address and the fingerprint:

```bash
cat data/repo-info.txt
```

Rebuild the image after changing the `Dockerfile`:

```bash
docker compose build --no-cache fdroid
```

To use a different version of the tools, change the `ARG` values at the top
of the `Dockerfile`. Do not use a `33.x` value for `BUILD_TOOLS_VERSION`.
That version of `apksigner` accepts invalid signatures, and `fdroidserver`
ignores it.

---

## 12. Problems and solutions

**The GitHub Actions run fails at "Check the F-Droid indexing secrets and
variables".**
`FDROID_KEYSTORE_BASE64`, `FDROID_CONFIG_YML_BASE64` or `FDROID_BASE_URL` is
missing. Run the one-time setup in section 4.

**The client app shows an unsigned repository, or a fingerprint error.**
The signing key changed. This happens if the two GitHub secrets were
regenerated instead of restored from a backup. Restore them from your
offline copy (section 7). If you have no backup, the users must remove the
repository and add the new link.

**The client app does not show a new app.**
Check the workflow run's log for the "Sign and index the repository" step.
Usually the APK file has no metadata file yet - see section 5.

**Metadata edits keep disappearing.**
Metadata must be committed under `fdroid/metadata/`, not edited only in a
local `data/metadata/` - that directory is never committed and CI never sees
it. See section 5.

**The workflow's S3 steps fail with an access error.**
Check `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` have write access to the
`fdroid/` prefix in the bucket, and that `S3_ENDPOINT_URL` / `AWS_REGION`
match your host.

**The build stops at the `apksigner --version` step.**
The download of the Android build-tools failed, or the version does not
exist. Check the access to `dl.google.com`. Then set a different
`BUILD_TOOLS_VERSION` in the `Dockerfile`.

**The Worker returns an error or stale content.**
The Worker only proxies the bucket; check the bucket directly at its raw
URL first. If that works but the Worker doesn't, check `ORIGIN_BASE_URL` in
`fdroid/worker/wrangler.jsonc` matches the bucket's public URL, and redeploy
with `npx wrangler deploy`.

---

## 13. References

- [Setup an F-Droid App Repo](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/)
- [Installing the Server and Repo Tools](https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/)
- [Signing Process](https://f-droid.org/docs/Signing_Process/)
- [fdroidserver source and config.yml example](https://github.com/f-droid/fdroidserver)
- [Cloudflare Workers](https://developers.cloudflare.com/workers/)
