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
| `db-init.yaml`, `db/`, `migrations.py` | Versioned database upgrades, checksum ledger, current catalog assertion and backend startup gate |
| `frontend.Dockerfile` | Optional Google-enabled Vite build without changing the application Dockerfile |
| `prepare.py`, `validate.py`, `test_*.py` | Dependency-free offline rendering, contract checks and tests |

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

Before upgrading an existing database, review the new REJECTED account state and the optional,
separately authorized merchant backfill in [the database upgrade guide](db/README.md#merchant-account-status-upgrade-after-2949e47).
The Kubernetes Job does not automatically execute `backend/database/merchant_account_statuses.sql`.

For a registry deployment, choose a lowercase registry owner and a fresh build tag:

```powershell
$registry = 'ghcr.io/your-lowercase-owner'
$tag = "$(git rev-parse --short=12 HEAD)-demo1"
docker build -t "${registry}/swiftdrop-backend:${tag}" backend
docker build -f k8s/frontend.Dockerfile -t "${registry}/swiftdrop-frontend:${tag}" frontend
docker login ghcr.io
docker push "${registry}/swiftdrop-backend:${tag}"
docker push "${registry}/swiftdrop-frontend:${tag}"
python k8s/prepare.py --backend-image "${registry}/swiftdrop-backend:${tag}" --frontend-image "${registry}/swiftdrop-frontend:${tag}"
python k8s/validate.py
python -B -m unittest discover -s k8s -p "test_*.py" -v
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

The Kubernetes build recipe keeps the application Dockerfile untouched. It supports
public `VITE_GOOGLE_CLIENT_ID` at build time and forces the API/WebSocket base to the
same browser origin. Kubernetes runtime `VITE_*` environment variables cannot change
a compiled React bundle. For Google login, use the **same public client ID** in both:

```powershell
$googleClientId = 'YOUR_PUBLIC_CLIENT_ID.apps.googleusercontent.com'
docker build -f k8s/frontend.Dockerfile --build-arg "VITE_GOOGLE_CLIENT_ID=$googleClientId" -t "${registry}/swiftdrop-frontend:${tag}" frontend
python k8s/prepare.py --backend-image "${registry}/swiftdrop-backend:${tag}" --frontend-image "${registry}/swiftdrop-frontend:${tag}" --google-client-id "$googleClientId"
```

Register the real browser origin in Google OAuth settings. Client IDs are public;
never put JWT, merchant API keys, webhook secrets or AI credentials in a frontend build.
The render records expected build arguments in its summary/annotations; it cannot
inspect or prove that a supplied image was actually built with them. Original CI
images use the original frontend Dockerfile and need a separate build for Google.
Builds/pushes above are future operator instructions; none were executed here.
The frontend context honors `frontend/.dockerignore` and excludes private `.env` files.
Optional `NODE_IMAGE` / `NGINX_IMAGE` build arguments allow approved base-image digests.

A rendered frontend ConfigMap derives Nginx configuration from the current application
proxy, preserving `/api/` and `/ws` routes to `backend:8080`. It adds a 6 MiB request
limit to accommodate the backend's 5 MiB proof uploads / 6 MiB multipart requests.
A pod checksum rolls Nginx when that mounted file changes; no frontend application
source or original Dockerfile/Nginx file is edited.

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
encoded as Base64. `GOOGLE_CLIENT_ID` is public configuration, not a client secret.
Optional `AI_API_KEY` stays in the Secret; use `AI_BASE_URL`, `AI_MODEL`,
`AI_MAX_ITERATIONS` and `AI_TIMEOUT_SECONDS` in the ConfigMap for the matching provider.
An empty AI key leaves the assistant unconfigured. SMTP username/password and MinIO access/secret keys
must be provided in pairs. Never commit or paste populated Secrets into reports.

After rendering, validate a populated private copy without printing its values:

```powershell
python k8s/validate.py --secrets k8s/.local/secrets.yaml
```

Set `CORS_ALLOWED_ORIGINS` to the actual browser origin(s), including the port;
the default `http://localhost:5173` matches the port-forward below and WebSocket
origin checks. SMTP defaults to Gmail; set credentials for real verification/reset
email. Current EmailService builds verification links with `http://localhost:5173`;
that matches this port-forward demo, but public hosting needs an application change
to make the link origin configurable (manual code entry remains available). No invented
FRONTEND_URL environment variable is supplied. MinIO photo proof needs an existing endpoint and bucket plus credentials;
without them, that feature remains unavailable. No `dev` profile is enabled.

Database/RabbitMQ passwords initialize accounts **only on new storage**. Editing a
Secret does not change passwords already stored by either service. Changing
`POSTGRES_DB` on an initialized volume does not create or move a database either. Coordinate an
in-place credential change and backend restart; never delete volumes to reset them.
Backend uses the initialized database owner for this demo, rather than a separate
least-privilege runtime role. No normal-profile application schema initializer was found; the Job is responsible
for the required catalog. The permissive dev profile, mock Google tokens and duplicate
direct webhook delivery are explicitly disabled. Merchant API keys and webhook signing
secrets are generated per merchant by the application and stored in PostgreSQL, not
shared deployment environment secrets. Preserve/encrypt database backups accordingly.
Fresh bootstrap creates no users; provision a trusted administrator separately before
merchant approval. Do not use public staff signup or enable development seeding.

## Database order and preservation

`prepare.py` copies the current repository SQL into an immutable ConfigMap, without
editing or maintaining stale copies of teammate SQL. Its digest names a distinct
`swiftdrop-db-init-<digest>` Job, recorded in `.generated/summary.json`.

The Job waits for PostgreSQL and holds a database advisory lock. It bootstraps the
current baseline only on an empty public schema. Existing databases never replay
baseline seeds or user/merchant approval backfills. Three immutable, checksum-checked
steps add the merged schema, handle reviewed legacy delivery-proof IDs, and enforce
integrity. A read-only current catalog assertion checks **19 tables / 184 columns**
plus declared keys/indexes before recording the matching bundle marker.

See [db/README.md](db/README.md) for exact ordering, transaction boundaries, supported
legacy contracts, review queries and recovery. Missing business identity/approval data,
unknown courier namespaces, orphan links or incompatible types stop for human review.
No values are invented, accounts auto-approved or secrets overwritten. Successful
steps commit once; failures roll back the current step and never write its ledger entry.
A final failure leaves earlier safe additions available for reviewed reconciliation.

Legacy `delivery_proofs.courier_id -> couriers.id` conversion is **off by default**.
After backups, identity review and stopping old writers, an operator may render with
`--reviewed-proof-conversion`. It uses a frozen canonical repository migration body inside
the versioned transaction. A changed applied checksum is an error: add a new numbered
migration instead of modifying history. Merely changing `--revision` does not replay
already applied steps or authorize identity conversion.

Backend's PostgreSQL-client init container waits for its exact successful bundle,
without a Kubernetes API token/RBAC. Fresh schema seeds are not replayed on retries.
No Job/PVC deletion is used to update the deployment. An existing database with no
zones/prices requires approved catalog setup before booking; it is not silently reseeded.

New PVCs do not automatically import Supabase/Compose data. Back up and restore into
the target database if migrating existing data. When adopting a volume, first check
PostgreSQL major version and `PGDATA=/var/lib/postgresql/data/pgdata`; an older root
layout must be reconciled explicitly. Preserve RabbitMQ's node identity
`rabbit@rabbitmq-0` and its data directory. Storage class/claim-template changes need
an operator storage plan; this package never recreates volumes to apply them.

## Later deployment and rollout verification

**Future operator steps only; none were executed for this task.** Review the context,
namespace, backups, image references and private settings first.
For **existing data upgrades**, schedule downtime and stop all old backend/worker/import
writers before starting the Job. A `Recreate` strategy/init gate alone does not stop
every old writer early enough. Keep writers stopped while a review/conversion is pending;
follow the database guide and validate on a restored PostgreSQL copy first. For the default
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
   scripts; append a new immutable entry in `migrations.MIGRATIONS`. Keep applied
   snapshots unchanged. Rendering generates checksums/runner/current-catalog assertion.
   Do not replay the whole baseline, even though its current price seeds use DO NOTHING.
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
- Readiness does not prove end-to-end API correctness. Existing application issues such
  as the email-verification token bypass and per-process idempotency remain outside this
  package. Review those before exposing the application publicly. No TLS/Ingress/egress
  policy is installed; public hosting and webhook DNS-rebinding defenses need a separate
  network plan. One backend replica remains intentional for in-process STOMP/idempotency.

During offline verification after merge 2949e47, Python parsed all eight JSON-compatible YAML source files and
the 15-resource render, and checked resource APIs/kinds, selectors, references,
ports, environment expansion order, probes, requests/limits, retained PVCs, current
SQL copying, current environment/build contracts and migration gating. Renderer and
migration tests check deterministic output, ledger order, secret preservation, source
JPA/SQL column consistency and reviewed conversion boundaries; all 26 tests passed, including
boundary-whitespace comparison and meaningful SQL/checksum difference regressions. The original
preparation also checked the schema wait script with Git Bash `bash -n` without executing it.
PostgreSQL was unavailable: no SQL grammar/server
execution, fresh/legacy upgrade, live credential, rollout or probe behavior was verified. These are custom offline contract checks, **not Kubernetes OpenAPI
or admission validation**. `kubectl`, Docker, kustomize and kubeconform were unavailable.
No cluster was contacted; no images were built/pulled, SQL executed, PVCs provisioned,
or application rollouts/probes tested. Complete those checks before a live deployment.
The local validation render used `swiftdrop-backend:offline-5f222a2` and
`swiftdrop-frontend:offline-5f222a2` solely as test references; replace them by rerendering
with your built/published image tags before any deployment.

References: [Kubernetes PVC retention](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention),
[dependent environment variables](https://kubernetes.io/docs/tasks/inject-data-application/define-interdependent-environment-variables/),
[Spring Boot health probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes).
