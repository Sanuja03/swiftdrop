# SwiftDrop Kubernetes deployment

Prepared for `feature/janani-final-tasks`; **nothing has been deployed**. Commands
below are operator instructions for a later authorized deployment. Run them from
the repository root. Examples use PowerShell, as on the development machine.

## Package and prerequisites

| Files | Purpose |
|---|---|
| `namespace.yaml`, `configmap.yaml` | Namespace and non-secret application settings |
| `secret.example.yaml` | Blank credential template; never included in public rendering |
| `postgres.yaml`, `rabbitmq.yaml` | Single-replica StatefulSets, retained PVCs, governing and application Services |
| `backend.yaml`, `frontend.yaml` | Deployments, Services, probes, CPU/memory requests/limits |
| `db-init.yaml`, `db/` | Database Job, ordered initialization and backend schema gate |
| `prepare.py`, `validate.py`, `test_prepare.py` | Dependency-free offline rendering, contract checks and tests |

The `.yaml` files use **JSON syntax, a YAML 1.2 subset** accepted by Kubernetes.
Python can therefore check their syntax without installing a YAML parser. Edit them
as JSON; do not apply the source directory directly. Rendering supplies concrete
images, current SQL, versioned Job/ConfigMap names and cross-resource references.
Generated output goes to ignored `.generated/`; private files belong in ignored `.local/`.

Deployment prerequisites:

- Kubernetes **1.32+**, `kubectl`, and a default dynamic StorageClass or a chosen
  `--storage-class`. PVCs request 5 GiB PostgreSQL and 2 GiB RabbitMQ. Persistent
  volume claims explicitly use `Retain` on StatefulSet deletion/scaling.
- Approximately 2 CPUs/4 GiB node capacity with headroom for startup; limits/requests
  are demo defaults and have not been load-tested. Database/broker are not HA.
- Built backend/frontend images accessible to the cluster; registry credentials
  through a pre-created image pull Secret if needed.
- A private Secret with database credentials, a named RabbitMQ user and a strong
  JWT secret. SMTP and MinIO are optional external services, not provisioned here.
- Python 3.10+ for preparation. Rendering/validation never contacts a cluster or DB.

The Services match the existing application: `postgres:5432`, `rabbitmq:5672`,
`backend:8080`, `frontend:80`; RabbitMQ management is internal on `15672`.
The existing frontend Nginx proxies `/api/` and `/ws` to `backend:8080`. All Services
are internal ClusterIP; no public Ingress, LoadBalancer or TLS is created.

## Build images and prepare offline

For a registry deployment, choose a lowercase registry owner and a fresh build tag:

```powershell
$registry = 'ghcr.io/your-lowercase-owner'
$tag = "$(git rev-parse --short=12 HEAD)-demo1"
docker build -t "${registry}/swiftdrop-backend:${tag}" backend
docker build -t "${registry}/swiftdrop-frontend:${tag}" frontend
docker login ghcr.io
docker push "${registry}/swiftdrop-backend:${tag}"
docker push "${registry}/swiftdrop-frontend:${tag}"
python k8s/prepare.py --backend-image "${registry}/swiftdrop-backend:${tag}" --frontend-image "${registry}/swiftdrop-frontend:${tag}"
python k8s/validate.py
python -m unittest discover -s k8s -p test_prepare.py -v
```

These build/push commands were **not run** during preparation. Do not reuse a tag
for changed source. For kind/minikube, build local image names, load them into the
chosen cluster using its image-load command, and pass those same tagged names to
`prepare.py`; `IfNotPresent` permits locally loaded images.

The existing CI workflow tests PRs/builds images but only pushes GHCR images on a
push to `main`, tagged `latest` and the full commit SHA. Feature/dev pushes alone
do not publish images, and CI does not deploy or validate this Kubernetes package.
Use its full SHA tag if consuming a published build. The renderer rejects `latest`
and untagged application image references; digests are also accepted.

Optional renderer flags: `--namespace`, `--storage-class`, `--image-pull-secret`,
`--postgres-image`, `--rabbitmq-image`, `--revision`. Default infrastructure images
match Compose (`postgres:17-alpine`, `rabbitmq:4.4.0-management`). Pin all four image
digests for byte-identical image content; upstream version tags can move. Do not
change PostgreSQL major versions as an ordinary image update.

The existing frontend Dockerfile builds with Vite defaults: API/WebSocket calls use
the same origin, as required by Nginx. `VITE_*` values are build-time values, not
Kubernetes runtime environment settings. The current Dockerfile exposes no Google
OAuth build argument; coordinate a separate frontend build change to enable a
Google client ID. No teammate Dockerfile or frontend source was changed here.

## Configure credentials and settings

Edit `configmap.yaml` before rendering. Keep `SPRING_JPA_HIBERNATE_DDL_AUTO=none`
and `SPRING_SQL_INIT_MODE=never`. Backend datasource settings explicitly select the
in-cluster PostgreSQL database, overriding the shared Supabase defaults.

```powershell
New-Item -ItemType Directory -Force k8s/.local
Copy-Item k8s/secret.example.yaml k8s/.local/secrets.yaml
```

Fill the private copy in an editor. Required: `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `JWT_SECRET`. Use a RabbitMQ user other than
`guest`. Generate `JWT_SECRET` from at least 32 cryptographically random bytes,
encoded as Base64. Optional SMTP username/password and MinIO access/secret keys
must be provided in pairs. Never commit or paste populated Secrets into reports.

After rendering, validate a populated private copy without printing its values:

```powershell
python k8s/validate.py --secrets k8s/.local/secrets.yaml
```

Set `CORS_ALLOWED_ORIGINS` to the actual browser origin(s), including the port;
the default `http://localhost:5173` matches the port-forward below and WebSocket
origin checks. SMTP defaults to Gmail; set credentials for real verification/reset
email. MinIO photo proof needs an existing endpoint and bucket plus credentials;
without them, that feature remains unavailable. No `dev` profile is enabled.

Database/RabbitMQ passwords initialize accounts **only on new storage**. Editing a
Secret does not change passwords already stored by either service. Coordinate an
in-place credential change and backend restart; never delete volumes to reset them.
Backend uses the initialized database owner for this demo, rather than a separate
least-privilege runtime role. Existing application auth-initializer behavior remains
unchanged, including its startup schema checks and Google email-verification update.

## Database order and preservation

`prepare.py` copies the current repository SQL into an immutable ConfigMap, without
editing or maintaining stale copies of teammate SQL. Its digest names a distinct
`swiftdrop-db-init-<digest>` Job, recorded in `.generated/summary.json`.

The Job waits for PostgreSQL and holds a database advisory lock, then:

1. On an **empty public schema only**, applies `schema.sql` in a transaction.
2. On an existing schema, skips that baseline because its zone/pricing `DO UPDATE`
   seeds would overwrite settings. Required baseline tables/known booking columns
   must already exist, otherwise the Job stops for manual additive-upgrade review.
3. Runs `courier_setup.sql`, then `merchant_webhook_setup.sql`, preserving their
   transactions and failure behavior. Duplicate merchant ownership makes the unique
   index fail; resolve it explicitly, without deleting or reassigning rows blindly.
4. Records the completed bundle in `swiftdrop_deployment_schema`. This ledger is
   append-only metadata; application rows/prices/secrets are not rewritten by it.

Backend has a PostgreSQL-client init container that waits for its exact completed
bundle before starting Java. It requires no Kubernetes API token/RBAC. The Job
fails on SQL errors; a failed migration never writes a success marker. Existing
baseline data is never reseeded. A previously successful bundle stays recorded for
compatible image rollback. Schema checks are guards, not a full migration framework.

New PVCs do not automatically import Supabase/Compose data. Back up and restore into
the target database if migrating existing data. When adopting a volume, first check
PostgreSQL major version and `PGDATA=/var/lib/postgresql/data/pgdata`; an older root
layout must be reconciled explicitly. Preserve RabbitMQ's node identity
`rabbit@rabbitmq-0` and its data directory. Storage class/claim-template changes need
an operator storage plan; this package never recreates volumes to apply them.

## Later deployment and rollout verification

**Future operator steps only; none were executed for this task.** Review the context,
namespace, backups, image references and private settings first. For the default
namespace:

```powershell
kubectl config current-context
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/.local/secrets.yaml
kubectl apply --dry-run=server --validate=strict -f k8s/.generated/manifest.json
kubectl apply -f k8s/.generated/manifest.json
$plan = Get-Content k8s/.generated/summary.json -Raw | ConvertFrom-Json
kubectl -n swiftdrop rollout status statefulset/postgres --timeout=600s
kubectl -n swiftdrop rollout status statefulset/rabbitmq --timeout=600s
kubectl -n swiftdrop wait --for=condition=complete "job/$($plan.databaseJob)" --timeout=900s
kubectl -n swiftdrop logs "job/$($plan.databaseJob)" -c apply-schema
kubectl -n swiftdrop rollout status deployment/backend --timeout=900s
kubectl -n swiftdrop rollout status deployment/frontend --timeout=600s
kubectl -n swiftdrop get pods,services,pvc,jobs
```

Use the same namespace everywhere if overriding it; create that namespace and adjust
the private Secret namespace first. Create the configured image pull Secret there
before deployment if the registry is private. Server dry-run contacts a cluster;
it was not performed here and requires Secrets/namespace/permissions to exist.

In separate terminals:

```powershell
kubectl -n swiftdrop port-forward service/frontend 5173:80
kubectl -n swiftdrop port-forward service/backend 8080:8080
kubectl -n swiftdrop port-forward service/rabbitmq 15672:15672
```

Open `http://localhost:5173`. Check backend `/actuator/health/liveness`,
`/actuator/health/readiness`, and `/api/v1/zones` at port 8080, then run the Postman
collection against the dedicated deployment. RabbitMQ management is at localhost:15672.
No database or broker ports are published by default.

Readiness includes database and RabbitMQ; liveness uses only application availability
so external outages do not create restart loops. SMTP is not a readiness dependency.
Startup probes allow slow JVM/broker initialization. Keep one backend replica: live
tracking currently uses an in-process STOMP broker, so multiple backend pods need a
separate design. Backend updates use `Recreate`, with brief downtime.

## Updating after teammates finish

1. Inspect their final Docker/runtime and SQL changes. Back up existing data. New
   baseline-only changes on an existing database require reviewed **additive** upgrade
   scripts; register those in `SQL_SOURCES` and `db/initialize.sql` before the marker.
   Do not rerun baseline upsert seeds on existing installations.
2. Build/publish fresh immutable app images, or select CI's matching full SHA tags.
3. Rerun `prepare.py` with the new images and the same namespace/storage/credentials.
   It copies current SQL. App-only changes reuse the completed database Job; changed
   SQL/Job settings generate a new Job. Use `--revision reviewed-2` for an intentional
   reviewed rerun, without deleting the existing Job or any storage.
4. Rerun offline validation/tests and, later, server validation. Apply the new rendered
   bundle and repeat Job/rollout verification. App-config changes trigger backend
   rollout via a checksum. After private Secret changes, restart backend explicitly
   once service-side credentials are synchronized.
5. Retain prior image digests for rollback. An image rollback does not roll back SQL;
   confirm additive schema compatibility. Do not automatically prune old database
   Jobs, ConfigMaps or PVCs; they provide history/storage for review.

## Troubleshooting and validation limits

Use `kubectl describe pod <name>`, `kubectl get events --sort-by=.lastTimestamp`,
and container logs in the chosen namespace. Typical causes:

- Pending PVC: missing/default StorageClass, quota or capacity; provision storage.
- ImagePullBackOff: image was not built/pushed/loaded or registry Secret is missing.
- Backend Init: inspect `wait-for-schema` logs and the versioned Job's init/main logs.
  Missing credentials, absent baseline columns or duplicate merchant links require
  correction/review, not a volume reset.
- Readiness 503: inspect PostgreSQL/RabbitMQ and resource pressure; do not weaken liveness
  to depend on them. Frontend 502 can occur while backend is still starting.
- WebSocket rejection: browser origin is missing from CORS settings. Google login or
  proof photos unavailable: configure their build-time/external prerequisites above.

During preparation, Python parsed all eight JSON-compatible YAML source files and
the 14-resource render, and checked resource APIs/kinds, selectors, references,
ports, environment expansion order, probes, requests/limits, retained PVCs, current
SQL copying and migration gating. Renderer tests exercise deterministic output and
version changes; all nine tests passed. Git Bash `bash -n` also passed for the schema
wait script without executing it. These are custom offline contract checks, **not Kubernetes OpenAPI
or admission validation**. `kubectl`, Docker, kustomize and kubeconform were unavailable.
No cluster was contacted; no images were built/pulled, SQL executed, PVCs provisioned,
or application rollouts/probes tested. Complete those checks before a live deployment.
The local validation render used `swiftdrop-backend:validation` and
`swiftdrop-frontend:validation` solely as test references; replace them by rerendering
with your built/published image tags before any deployment.

References: [Kubernetes PVC retention](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention),
[dependent environment variables](https://kubernetes.io/docs/tasks/inject-data-application/define-interdependent-environment-variables/),
[Spring Boot health probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes).
