// Reverse proxy for the self-hosted F-Droid repository.
//
// The repository itself still lives on the S3-compatible bucket configured
// in fdroid/.env (ORIGIN_BASE_URL below points at it). This Worker exists
// only to put a custom domain and edge caching in front of that bucket - it
// holds no files and needs no redeploy when the repository content changes.
//
// The index is signed by fdroidserver, so serving it through a proxy (or a
// public bucket directly) is safe: a client rejects an index with the wrong
// signature. This mirrors fdroid/Caddyfile's cache and secret-path rules for
// anyone who was previously self-hosting with Caddy.

const SHORT_CACHE = "public, max-age=60";
const LONG_CACHE = "public, max-age=31536000, immutable";
const DEFAULT_CACHE = "public, max-age=300";

// Defence in depth. The CI job never uploads these to the bucket, but a
// proxy should not blindly forward a request for them either.
const BLOCKED_PATHS = new Set([
  "/fdroid/keystore.p12",
  "/fdroid/config.yml",
  "/fdroid/rclone.conf",
]);

// Only these headers are forwarded from the origin response. This avoids
// leaking details of the underlying S3-compatible provider (server banner,
// request IDs, bucket ACL headers, ...) to the client.
const FORWARDED_RESPONSE_HEADERS = [
  "content-type",
  "content-length",
  "etag",
  "last-modified",
  "accept-ranges",
  "content-range",
];

function cacheControlFor(pathname) {
  const file = pathname.split("/").pop() ?? "";
  // The signed index and entry files change on every release and must be
  // re-checked often. APK files never change under the same name.
  if (file.startsWith("index") || file.startsWith("entry")) return SHORT_CACHE;
  if (file.endsWith(".apk")) return LONG_CACHE;
  return DEFAULT_CACHE;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405 });
    }

    if (BLOCKED_PATHS.has(url.pathname)) {
      return new Response("Not found", { status: 404 });
    }

    if (!env.ORIGIN_BASE_URL) {
      return new Response("Worker is missing the ORIGIN_BASE_URL variable.", { status: 500 });
    }

    const originUrl = new URL(env.ORIGIN_BASE_URL);
    originUrl.pathname = url.pathname;
    originUrl.search = url.search;

    // Range requests (resumed/partial APK downloads) bypass the cache: caching
    // partial content correctly needs extra bookkeeping this proxy does not
    // do, and range requests are rare enough that always hitting the origin
    // is fine.
    const range = request.headers.get("range");
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), request);

    if (!range) {
      const cached = await cache.match(cacheKey);
      if (cached) return cached;
    }

    const originHeaders = new Headers();
    if (range) originHeaders.set("range", range);

    let originResponse;
    try {
      originResponse = await fetch(originUrl.toString(), {
        method: request.method,
        headers: originHeaders,
      });
    } catch (err) {
      return new Response("Origin fetch failed", { status: 502 });
    }

    if (originResponse.status >= 500) {
      return new Response("Origin error", { status: 502 });
    }

    const headers = new Headers();
    for (const name of FORWARDED_RESPONSE_HEADERS) {
      const value = originResponse.headers.get(name);
      if (value) headers.set(name, value);
    }
    headers.set("cache-control", cacheControlFor(url.pathname));

    // originResponse.body is streamed straight into the reply - the repo can
    // contain multi-hundred-MB APKs, so nothing here buffers it in memory.
    const response = new Response(originResponse.body, {
      status: originResponse.status,
      headers,
    });

    if (!range && originResponse.status === 200) {
      ctx.waitUntil(cache.put(cacheKey, response.clone()));
    }

    return response;
  },
};
