#!/usr/bin/env bash
# Deploys the current commit's image to Railway.
#
# Railway's own builder has never worked on this project — every attempt failed
# before emitting a line of build output — so CI publishes the image and Railway
# runs the tag. The tag has to exist in the registry before Railway is told to
# pull it: deploying the moment CI reports success races the registry, the pull
# 404s, and the deployment fails for no reason anybody can see afterwards. Two
# spurious "build failed" alerts came from exactly that.
set -euo pipefail

SERVICE=48e3344a-73c3-43be-8ea4-17c00444a81d
ENVIRONMENT=548fb42b-72ff-4e48-9897-ca87a782e08d
PROJECT=79b6a8f0-8a23-4a31-bf5c-5d520c93d228
SHA=$(git rev-parse HEAD)
IMAGE="ghcr.io/mayankgoel214/hokiehub-api:$SHA"

echo "waiting for CI to publish the image for ${SHA:0:7}…"
until [ "$(gh run list --workflow=api-image.yml --limit 1 --json status -q '.[0].status')" = "completed" ]; do
  sleep 15
done
if [ "$(gh run list --workflow=api-image.yml --limit 1 --json conclusion -q '.[0].conclusion')" != "success" ]; then
  echo "CI did not publish an image; nothing to deploy." >&2
  exit 1
fi

# The actual guard: do not tell Railway to pull something that cannot be pulled.
echo "waiting for $IMAGE to be pullable…"
for _ in $(seq 1 40); do
  if docker manifest inspect "$IMAGE" >/dev/null 2>&1; then
    echo "  image is in the registry"
    break
  fi
  sleep 10
done
docker manifest inspect "$IMAGE" >/dev/null 2>&1 || {
  echo "image never appeared in the registry; not deploying." >&2
  exit 1
}

railway api 'mutation($sid:String!,$eid:String!,$in:ServiceInstanceUpdateInput!){ serviceInstanceUpdate(serviceId:$sid, environmentId:$eid, input:$in) }' \
  --var sid="$SERVICE" --var eid="$ENVIRONMENT" \
  --var in="{\"source\":{\"image\":\"$IMAGE\"}}" >/dev/null

railway api 'mutation($s:String!,$e:String!){ serviceInstanceDeployV2(serviceId:$s, environmentId:$e) }' \
  --var s="$SERVICE" --var e="$ENVIRONMENT" >/dev/null
echo "deploy triggered"

for _ in $(seq 1 40); do
  STATUS=$(railway api 'query($p:String!){ deployments(first:1, input:{projectId:$p}){ edges { node { status } } } }' \
    --var p="$PROJECT" 2>/dev/null | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['deployments']['edges'][0]['node']['status'])" 2>/dev/null || echo "?")
  case "$STATUS" in
    SUCCESS) echo "deployed"; exit 0 ;;
    FAILED|CRASHED) echo "deployment $STATUS" >&2; exit 1 ;;
  esac
  sleep 12
done
echo "deployment did not settle in time" >&2; exit 1
