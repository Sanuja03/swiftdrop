#!/bin/sh
set -eu
: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${EXPECTED_SCHEMA_BUNDLE:?EXPECTED_SCHEMA_BUNDLE is required}"
case "$EXPECTED_SCHEMA_BUNDLE" in
    *[!0-9a-f]*) echo 'Invalid schema bundle digest.' >&2; exit 1 ;;
esac
[ "${#EXPECTED_SCHEMA_BUNDLE}" -eq 64 ] || exit 1
export PGCONNECT_TIMEOUT=5

attempt=0
while [ "$attempt" -lt 120 ]; do
    applied=$(psql -X -A -t -q -c "SELECT bundle_sha256 FROM public.swiftdrop_deployment_schema WHERE deployment = 'swiftdrop' AND bundle_sha256 = '$EXPECTED_SCHEMA_BUNDLE'" 2>/dev/null || true)
    if [ "$applied" = "$EXPECTED_SCHEMA_BUNDLE" ]; then
        echo 'Required schema bundle is present.'
        exit 0
    fi
    if [ $((attempt % 12)) -eq 0 ]; then
        echo 'Waiting for database connectivity and the matching db-init completion marker...'
    fi
    attempt=$((attempt + 1))
    sleep 5
done
echo 'Schema wait timed out; inspect the db-init Job and database configuration.' >&2
exit 1
