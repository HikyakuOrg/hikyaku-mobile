# FDROID.md

How to operate the self-hosted F-Droid repository.

---

## 1. Purpose

This setup holds a **binary repository**. You supply the APK files. The server
does not build apps from source code. Therefore the server does not need the
full Android SDK, and the image stays small.

You can publish the repository from this host, or to an S3 bucket, or to both.

All the files are in the `fdroid/` directory:

| File | Function |
|---|---|
| `compose.yml` | The two services: the indexer and the web server. |
| `Dockerfile` | The Alpine image that holds the `fdroidserver` tools. |
| `entrypoint.sh` | The start-up steps of the indexer. |
| `Caddyfile` | The web server rules. |
| `.env.example` | The configuration template. Copy this file. |
| `data/` | The signing key, the APK files and the index. |

---

## 2. Before you start

You must have these items:

1. A Linux host with Docker Engine and the Compose plugin.
2. Approximately 1 GB of disk space, plus the space for your APK files.
3. A domain name, if you publish from this host and want automatic HTTPS.
4. An S3 bucket and access keys, if you publish to S3.

For automatic HTTPS you must also do these two steps:

1. Point the DNS A record of the domain to the host.
2. Open TCP port 80 and TCP port 443 on the firewall.

---

## 3. How it works

The `fdroid` service does six steps at each start:

1. It makes a signing key, but only at the first start.
2. It writes your `.env` values into `config.yml`.
3. If the pull is on, it copies new APK files from the bucket into
   `data/repo/`. See section 11.
4. It signs the APK files in `data/repo/` and writes the index.
5. If S3 is on, it sends `data/repo/` to the bucket.
6. It then looks at `data/repo/` every 60 seconds. If the APK files change, it
   repeats step 3 to step 5.

The `web` service publishes `data/repo/` at `<your-url>/fdroid/repo`. This
service starts only when `COMPOSE_PROFILES` contains `web`.

The signing key stays in `data/keystore.p12`. This file is not in the web root,
and the service does not send it to S3. Users cannot download it.

The index is signed. Therefore the transport does not need to be secret. A
public S3 bucket is safe, because the client app refuses an index with a wrong
signature.

---

## 4. Install the repository

**Caution: Step 4 makes the signing key. Do the backup in section 7 as soon as
the first start is complete.**

1. Go to the `fdroid` directory.

   ```bash
   cd fdroid
   ```

2. Copy the configuration template.

   ```bash
   cp .env.example .env
   ```

3. Open `.env` in a text editor. Set the values. Section 8 gives the details of
   each value.

4. Start the services.

   ```bash
   docker compose up -d
   ```

   The first start takes 3 to 6 minutes. Docker builds the image in this time.

5. Read the log to find the address of your repository.

   ```bash
   docker compose logs fdroid
   ```

   The log shows a line that starts with `Add URL:`. Keep this line.

The installation is now complete.

---

## 5. Add an app

1. Copy the APK file into `fdroid/data/repo/`.

   ```bash
   cp myapp-release.apk fdroid/data/repo/myapp_10203.apk
   ```

   The name of the file is not important. But the format
   `<package-name>_<version-code>.apk` helps you to find the file.

2. Wait 60 seconds. The indexer finds the new file and writes a new index.

   To do this immediately, use this command:

   ```bash
   docker compose exec fdroid fdroid update --create-metadata --pretty
   ```

3. Open `fdroid/data/metadata/<package-name>.yml`. The indexer made this file.
   Set the fields that the users see:

   ```yaml
   AuthorName: Example Org
   Name: My Application
   Summary: A short line of text.
   Description: |-
       A longer text. This text can have more than one paragraph.
   License: Apache-2.0
   WebSite: https://example.com
   ```

4. To add an icon and screenshots, make these files:

   ```
   data/repo/<package-name>/en-US/icon.png
   data/repo/<package-name>/en-US/phoneScreenshots/1.png
   ```

**Note: To remove an app, delete the APK file and the metadata file. Then write
a new index.**

---

## 6. Give the repository to the users

The users need the address and the fingerprint. The fingerprint prevents an
attack from a different server.

Find the two values in this file:

```bash
cat fdroid/data/repo-info.txt
```

The file shows a link with this format:

```
https://apps.example.com/fdroid/repo?fingerprint=A1B2C3...
```

Give this link to the users. The users then do these three steps:

1. Install the F-Droid client from `https://f-droid.org`.
2. Open the link on the Android device.
3. Touch **Add** in the F-Droid client.

The users can also use the menu **Settings > Repositories > Add**. But the link
is safer, because the link contains the fingerprint.

---

## 7. Back up the signing key

**Warning: If you lose the signing key, you cannot update the repository. You
must then make a new repository. All the users must remove the old repository
and add the new one.**

Back up these two files:

```
fdroid/data/keystore.p12
fdroid/data/config.yml
```

The file `config.yml` holds the passwords of the keystore. Keep the two files
together, and keep them in a safe place. Do not put them in Git. The file
`fdroid/.gitignore` prevents this.

To restore the repository, put the two files back into `fdroid/data/`. Then
start the services. The indexer finds the key and keeps it.

---

## 8. The configuration values

Set these values in `fdroid/.env`.

### Necessary values

| Value | Function |
|---|---|
| `COMPOSE_PROFILES` | `web` starts the local web server. Make it empty to publish to S3 only. |
| `FDROID_BASE_URL` | The public address of the repository. Do not add a path. The repository becomes `<value>/fdroid/repo`. |

### Local web server

These values apply only when `COMPOSE_PROFILES` contains `web`.

| Value | Default | Function |
|---|---|---|
| `FDROID_SITE_ADDRESS` | `:80` | The address that the web server listens on. See section 9. |
| `FDROID_HTTP_PORT` | `80` | The host port for HTTP. |
| `FDROID_HTTPS_PORT` | `443` | The host port for HTTPS. |

### S3

| Value | Default | Function |
|---|---|---|
| `FDROID_S3_BUCKET` | empty | The name of the bucket. An empty value turns S3 off. |
| `AWS_ACCESS_KEY_ID` | empty | The access key. |
| `AWS_SECRET_ACCESS_KEY` | empty | The secret key. |
| `FDROID_RCLONE_REMOTE` | empty | The section name in `data/rclone.conf`. See section 10. |
| `FDROID_S3_PULL_PREFIX` | empty | The prefix in the bucket that holds the APK files from CI. An empty value turns the pull off. See section 11. |

### Repository details

| Value | Default | Function |
|---|---|---|
| `FDROID_REPO_NAME` | `My F-Droid Repo` | The name that the client app shows. |
| `FDROID_REPO_DESCRIPTION` | — | A short text about the repository. |
| `FDROID_REPO_ICON` | empty | The name of a PNG file in `data/`. |

### Signing key

**Note: The system uses these two values one time only, at the first start. A
change after that start has no effect.**

| Value | Default | Function |
|---|---|---|
| `FDROID_KEY_ALIAS` | `myrepo` | The name of the key in the keystore. |
| `FDROID_KEY_DNAME` | — | The X.509 name of the certificate. |

### Behaviour

| Value | Default | Function |
|---|---|---|
| `FDROID_ARCHIVE_OLDER` | `0` | The number of recent versions to keep in the main repository. The older versions move to an archive. `0` keeps all the versions in the main repository. |
| `FDROID_SCAN_INTERVAL` | `60` | The time between two checks of `data/repo/`, in seconds. `0` writes one index and then stops the container. |
| `FDROID_MIRRORS` | empty | Other addresses that hold a copy. Put a comma between the items. |
| `TZ` | `UTC` | The time zone of the container. |

---

## 9. Publish from this host

Set `COMPOSE_PROFILES=web`.

### A. Direct, with automatic HTTPS

Use this way if the host is on the internet, and if ports 80 and 443 are free.

```ini
COMPOSE_PROFILES=web
FDROID_BASE_URL=https://apps.example.com
FDROID_SITE_ADDRESS=apps.example.com
```

The web server gets a certificate from Let's Encrypt automatically. It also
renews the certificate automatically.

### B. Behind your own reverse proxy

Use this way if a different program already holds ports 80 and 443.

```ini
COMPOSE_PROFILES=web
FDROID_BASE_URL=https://apps.example.com
FDROID_SITE_ADDRESS=:80
FDROID_HTTP_PORT=8080
FDROID_HTTPS_PORT=8443
```

Then send the path `/fdroid/` from your proxy to `http://127.0.0.1:8080`.

`FDROID_BASE_URL` must show the address that the **users** see. It is not the
address of the container.

---

## 10. Publish to S3

The indexer signs the files on this host. It then sends them to the bucket with
`rclone`. The files go to `<bucket>/fdroid/repo/`.

**Caution: This is a mirror operation. `rclone` removes the files in
`<bucket>/fdroid/` that are not in `data/repo/`. Do not keep other data under
that prefix.**

The bucket must permit public read access for the `fdroid/` prefix. The index
is signed, so public read access is safe.

### A. AWS S3, region us-east-1

This is the simplest configuration. Two keys are sufficient.

```ini
COMPOSE_PROFILES=
FDROID_BASE_URL=https://my-bucket.s3.amazonaws.com
FDROID_S3_BUCKET=my-bucket
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

### B. A different region, or a different S3 service

`fdroidserver` uses the region `us-east-1` and the AWS endpoint if you give the
keys only. For a different region, or for MinIO, Cloudflare R2, Backblaze B2 or
Wasabi, you must supply an `rclone` configuration file.

1. Make the file `fdroid/data/rclone.conf`. Use one of these examples.

   AWS S3, a different region:

   ```ini
   [aws-eu]
   type = s3
   provider = AWS
   region = eu-west-1
   access_key_id = AKIA...
   secret_access_key = ...
   ```

   Cloudflare R2:

   ```ini
   [r2]
   type = s3
   provider = Cloudflare
   region = auto
   endpoint = https://<account-id>.r2.cloudflarestorage.com
   access_key_id = ...
   secret_access_key = ...
   ```

   MinIO:

   ```ini
   [minio]
   type = s3
   provider = Minio
   endpoint = https://minio.example.com
   access_key_id = ...
   secret_access_key = ...
   ```

2. Set the section name in `.env`:

   ```ini
   FDROID_S3_BUCKET=my-bucket
   FDROID_RCLONE_REMOTE=r2
   ```

3. Set `FDROID_BASE_URL` to the public read address of the bucket. For R2 this
   is the public bucket URL or your custom domain.

**Note: `data/rclone.conf` holds your keys. The service does not send this file
to the bucket, and the web server does not publish it. Keep it out of Git.**

### C. Both this host and S3

Set `COMPOSE_PROFILES=web` and `FDROID_S3_BUCKET` together. The host then
serves the repository, and the bucket holds a copy. Put the address that the
users see in `FDROID_BASE_URL`, and put the other address in `FDROID_MIRRORS`.

---

## 11. Get the APK files from the CI job

The file `.github/workflows/android-s3-release.yml` builds a signed APK at each
push to the `main` branch. It puts the APK in `<bucket>/apks/`.

To publish those APK files to your users, set the prefix in `.env`:

```ini
FDROID_S3_BUCKET=my-bucket
FDROID_S3_PULL_PREFIX=apks
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

The indexer then does these steps every 60 seconds:

1. It copies new APK files from `<bucket>/apks/` into `data/repo/`.
2. It signs them and writes a new index.
3. It sends the result to `<bucket>/fdroid/repo/`.

The users get the new version at the next update check of the client app.

**Note: The copy operation never deletes a local file. To remove an app from
the repository, delete the APK file in `data/repo/` and delete the file in
`<bucket>/apks/`. If you delete only the local file, the next pull brings it
back.**

The step ignores the file `latest.apk`, because that file is a copy of a file
that already has a version number in its name.

The two prefixes must stay separate. `fdroid deploy` mirrors `data/repo/` to
`<bucket>/fdroid/repo/` with `rclone sync --delete-after`. If the CI job wrote
to `fdroid/repo/`, that command would delete the files. Each APK therefore uses
space in the bucket two times: one time in `apks/`, and one time in
`fdroid/repo/`.

**Note: The pull uses the AWS region `us-east-1`, the same as `fdroid deploy`.
For a different region or a different S3 service, use `data/rclone.conf` and
`FDROID_RCLONE_REMOTE`. Section 10 gives an example.**

---

## 12. Usual tasks

Do these commands in the `fdroid` directory.

Show the log of the indexer:

```bash
docker compose logs -f fdroid
```

Write a new index immediately:

```bash
docker compose exec fdroid fdroid update --create-metadata --pretty
```

Send the repository to S3 immediately:

```bash
docker compose exec fdroid fdroid deploy --verbose
```

Show the address and the fingerprint again:

```bash
cat data/repo-info.txt
```

Apply a change that you made in `.env`:

```bash
docker compose up -d
```

Stop the repository:

```bash
docker compose down
```

Update the `fdroidserver` tools to the current version:

```bash
docker compose build --no-cache fdroid
```

To use a different version of the tools, change the `ARG` values at the top of
the `Dockerfile`. Do not use a `33.x` value for `BUILD_TOOLS_VERSION`. That
version of `apksigner` accepts invalid signatures, and `fdroidserver` ignores
it.

---

## 13. Problems and solutions

**The container stops immediately. The log shows `FDROID_BASE_URL is not set`.**
The file `.env` does not exist, or the value is empty. Copy `.env.example` to
`.env`. Then set the value.

**The build stops at the `apksigner --version` step.**
The download of the Android build-tools failed, or the version does not exist.
Check the access to `dl.google.com`. Then set a different
`BUILD_TOOLS_VERSION` in the `Dockerfile`.

**The client app shows an unsigned repository, or a fingerprint error.**
The signing key changed. This occurs if you deleted `data/keystore.p12`. Restore
the key from your backup. If you have no backup, the users must remove the
repository and add the new link.

**The client app does not show a new app.**
Read the log with `docker compose logs fdroid`. Usually the APK file has no
metadata file. Write a new index with the `--create-metadata` option.

**The log shows `S3 is on, but there are no credentials`.**
Set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Or make
`data/rclone.conf` and set `FDROID_RCLONE_REMOTE`.

**The log shows `data/rclone.conf is present, but FDROID_RCLONE_REMOTE is
empty`.**
`fdroidserver` needs the name of the section to use. Set
`FDROID_RCLONE_REMOTE` to a section name from that file.

**The log shows `The pull from S3 failed`.**
The indexer continues with the local files. Check the keys, and check that the
prefix in `FDROID_S3_PULL_PREFIX` exists in the bucket. Read the full error
with `docker compose logs fdroid`.

**The upload to S3 fails with an access error.**
Check the keys. Check that the key has write access to the bucket. For a
non-AWS service, check the `endpoint` value in `data/rclone.conf`.

**The web server does not get a certificate.**
Check the DNS record of the domain. Check that port 80 is open. Let's Encrypt
uses port 80 for the challenge. Read the log with `docker compose logs web`.

**The port 80 is already in use.**
A different program holds the port. Use way B in section 9.

---

## 14. References

- [Setup an F-Droid App Repo](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/)
- [Installing the Server and Repo Tools](https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/)
- [Signing Process](https://f-droid.org/docs/Signing_Process/)
- [fdroidserver source and config.yml example](https://github.com/f-droid/fdroidserver)
- [F-Droid sdkmanager](https://gitlab.com/fdroid/sdkmanager)
- [rclone S3 configuration](https://rclone.org/s3/)
