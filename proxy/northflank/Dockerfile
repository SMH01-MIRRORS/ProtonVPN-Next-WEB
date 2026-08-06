# Container image for the Northflank deployment: the same site and proxy the
# Deno Deploy project serves, run as an ordinary long-lived process.
#
# Northflank is not an edge platform, and that is the point. There is no adapter
# here and no second implementation to keep in step: `server.ts` runs unchanged,
# so a change to the proxy reaches this deployment by rebuilding, not by being
# ported.
#
# Build context is the repository root, not this directory:
#   docker build -f proxy/northflank/Dockerfile -t pvpn-proxy .

# Not pinned to a patch release on purpose: the entrypoint uses only stable Deno
# APIs plus `--unstable-kv`, and an image that silently stops existing is a
# worse failure than an unannounced minor upgrade. Pin it if a build ever breaks.
FROM denoland/deno:alpine

# Where Deno keeps its module cache *and* the local key-value database that
# backs the quotas. Mount a volume here to keep counters across a redeploy;
# without one they simply start empty, which is a fresh window for everybody
# rather than a broken deployment.
ENV DENO_DIR=/deno-dir

WORKDIR /app

# Copied separately from the sources so a change to the site does not invalidate
# the dependency cache.
COPY deno.json ./
COPY server.ts ./
COPY proxy ./proxy
RUN deno cache server.ts

# The built site, committed to the repository, so this image never depends on a
# build step succeeding twice in two different places.
COPY dist ./dist

# Northflank injects its own PORT; this is the default for a plain `docker run`.
ENV PORT=8080
EXPOSE 8080

# A container that cannot answer is worth restarting, and Northflank will only
# know that if something says so. The health endpoint runs the real routing
# code, so it fails when the proxy is genuinely broken and not merely busy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
	CMD wget --quiet --tries=1 --spider "http://127.0.0.1:${PORT}/__proxy/health" || exit 1

CMD ["deno", "task", "start:container"]
