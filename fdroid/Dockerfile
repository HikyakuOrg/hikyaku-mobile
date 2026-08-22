# fdroidserver on Alpine.
#
# Alpine has no fdroidserver package and no apksigner package. Therefore:
#   - fdroidserver comes from PyPI, into a Python virtual environment.
#   - apksigner comes from the Android build-tools. F-Droid's own "sdkmanager"
#     tool gets them. It verifies the download with a SHA-256 checksum from a
#     signed transparency log.
#
# apksigner is necessary. Without it, fdroidserver uses jarsigner instead, and
# jarsigner accepts unsigned APK files as valid.
#
# Only apksigner stays in the image. The other build-tools programs are for
# app builds, and this repository does not build apps.
FROM alpine:3.22

ARG FDROIDSERVER_VERSION=2.4.5
# Do not use a 33.x version here. That apksigner accepts invalid v3 signatures,
# and fdroidserver then ignores it.
ARG BUILD_TOOLS_VERSION=35.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    PYTHONUNBUFFERED=1 \
    LANG=C.UTF-8
ENV PATH="/opt/venv/bin:${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}:${PATH}"

RUN set -eux; \
    apk add --no-cache \
        python3 py3-pip \
        openjdk21-jdk \
        libmagic \
        rclone \
        git \
        bash coreutils findutils \
        ca-certificates tzdata; \
    python3 -m venv /opt/venv; \
    /opt/venv/bin/pip install --no-cache-dir --upgrade pip; \
    /opt/venv/bin/pip install --no-cache-dir \
        "fdroidserver==${FDROIDSERVER_VERSION}" sdkmanager; \
    /opt/venv/bin/sdkmanager --sdk_root="${ANDROID_HOME}" \
        "build-tools;${BUILD_TOOLS_VERSION}"; \
    BT="${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}"; \
    find "$BT" -mindepth 1 -maxdepth 1 \
        ! -name apksigner ! -name lib ! -name framework \
        -exec rm -rf {} +; \
    rm -rf /root/.android /root/.cache /tmp/*

# The build stops here if a necessary program is missing or broken.
# This is better than a broken repository at run time.
RUN set -eux; \
    command -v keytool; \
    command -v jarsigner; \
    rclone version | head -1; \
    apksigner --version; \
    fdroid --version

COPY entrypoint.sh /usr/local/bin/fdroid-entrypoint
# Remove the CR characters, in case of a checkout on Windows.
RUN sed -i 's/\r$//' /usr/local/bin/fdroid-entrypoint \
 && chmod 0755 /usr/local/bin/fdroid-entrypoint

WORKDIR /data
ENTRYPOINT ["/usr/local/bin/fdroid-entrypoint"]
