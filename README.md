# SwiftDrop – Parcel Delivery & Courier Management System

A web-based parcel delivery and courier management platform designed to streamline delivery operations, parcel tracking, courier assignments, and merchant integrations.

## Architecture

**Architecture Pattern:** Modular Monolith

**Proposed Technology Stack:**
- Backend: Java 21, Spring Boot
- Frontend: React, Vite
- Database: PostgreSQL
- Authentication: Spring Security, JWT, OAuth 2.0
- Messaging: RabbitMQ
- Real-time Communication: WebSockets
- Containerization: Docker, Docker Compose
- Web Server: Nginx

## System Modules

1. Identity & Authentication
2. Customer Management
3. Merchant Management
4. Delivery & Parcel Tracking
5. Courier Management
6. Dispatch & Assignment
7. Pricing & Delivery Zones
8. Notifications
9. External Integrations

## Repository Structure

```text
swiftdrop/
├── backend/              # Spring Boot application
│   ├── src/
│   ├── Dockerfile
│   └── .dockerignore
│
├── frontend/             # React application
│   ├── src/
│   ├── Dockerfile
│   └── .dockerignore
│
├── docs/                 # Technical documentation and diagrams
├── infrastructure/       # Infrastructure and deployment configurations
│
├── docker-compose.yml    # Multi-container orchestration
├── .env.example          # Environment variable template
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

Install the following before running the application:

- [Git](https://git-scm.com/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)

Docker Desktop must be running before executing the Docker commands.

### 1. Clone the Repository

```bash
git clone https://github.com/Sanuja03/swiftdrop.git
cd swiftdrop
```

### 2. Configure Environment Variables

Create a `.env` file in the project root using `.env.example` as a template.

```env
POSTGRES_DB=swiftdrop
POSTGRES_USER=swiftdrop
POSTGRES_PASSWORD=your_local_password
RABBITMQ_USERNAME=swiftdrop
RABBITMQ_PASSWORD=your_local_password
JWT_SECRET=a_base64_key_of_at_least_256_bits
```

These are required by `docker compose`; `.env.example` lists every optional variable.

For Google login, set `GOOGLE_CLIENT_ID` to your public OAuth client ID and register
`http://localhost:5173` as an authorized browser origin. Compose passes that same value
to the backend and the frontend build. Rebuild the frontend after changing it; Vite
configuration is compiled into the image. Never pass signing keys or passwords as
frontend build arguments. `CORS_ALLOWED_ORIGINS` defaults to `http://localhost:5173`.

For the AI delivery assistant, set `AI_API_KEY` to the API key of an OpenAI-compatible
provider (`AI_BASE_URL` and `AI_MODEL` choose the provider and model). Without it the rest
of the app works normally and the assistant replies with a "not configured" message.

New accounts must verify their email before they can sign in, so outside the dev profile
signup needs SMTP: set `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD`. The dev profile's
seeded accounts (see below) are already verified and work without SMTP.

Use a suitable local development password. Do not commit the `.env` file or share production credentials.

### 3. Build and Start the Application

Run the following command from the project root:

```bash
docker compose up --build -d
```

Docker Compose builds the application images and starts the frontend, backend, PostgreSQL and RabbitMQ containers. On the first start (a new database volume) PostgreSQL runs `backend/src/main/resources/schema.sql`, which creates every table and the starter zones and prices.

### 4. Access the Application

Once the containers have started, the services are available at:

| Service | URL / Port |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Backend Health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5433 |
| RabbitMQ management | http://localhost:15672 |

### 5. Check Container Status

To check whether the services are running:

```bash
docker compose ps
```

### 6. View Application Logs

To view logs from all services:

```bash
docker compose logs -f
```

To view logs for an individual service:

```bash
docker compose logs backend
docker compose logs frontend
docker compose logs postgres
```

### 7. Stop the Application

To stop and remove the running containers and network:

```bash
docker compose down
```

PostgreSQL data is stored in a persistent Docker volume and is retained when the containers are stopped.

Do not use volume deletion to apply schema changes. `schema.sql` initializes an empty
PostgreSQL volume once; existing databases require a backup and reviewed migrations.
See [the database upgrade contract](k8s/db/README.md), including the separate merchant
account-status review. Changing database or RabbitMQ credentials in `.env` does not
rotate credentials already stored on existing volumes. Keep the volumes and coordinate
credential/schema upgrades with the database or broker owner.

PostgreSQL and RabbitMQ have Compose health checks; backend startup waits for both.
Compose does not currently provide application readiness checks, so the frontend may
briefly return 502 while Spring Boot starts. Inspect `/actuator/health` and application
logs before the demo. Nginx forwards `/api` and `/ws` and accepts the backend's 6 MB
multipart request limit. Photo proof additionally requires external MinIO configuration.

## Docker Services

The current Docker Compose configuration includes:

| Service | Technology | Container Port | Host Port |
|---|---|---:|---:|
| Frontend | React + Nginx | 80 | 5173 |
| Backend | Spring Boot | 8080 | 8080 |
| Database | PostgreSQL 17 | 5432 | 5433 |
| Message broker | RabbitMQ 4 | 5672, 15672 | 5672, 15672 |

The backend connects to PostgreSQL through Docker's internal service network using the hostname `postgres`.

Not in Compose yet: Mailpit (email) and MinIO (courier proof photos). Without SMTP settings, emails are skipped; without the MINIO_* settings, photo proof is unavailable and recipient-name proof still works.

## Running the backend locally (dev profile)

`SPRING_PROFILES_ACTIVE=dev` uses a local H2 file database (`backend/data/`) instead of Supabase and creates test data on first start: zones, prices, three demo parcels and these accounts (all `@example.com`):

| Account | Role |
|---|---|
| test.customer@example.com | Customer (owns the demo parcels) |
| test.courier@example.com | Courier |
| test.dispatcher@example.com | Dispatcher |
| test.admin@example.com | Admin |
| test.merchant@example.com | Approved merchant with an API key |

Set `DEV_USER_PASSWORD` to choose the password of the first four; otherwise each gets a generated password, logged once. The merchant uses `DEV_MERCHANT_PASSWORD` (set in `application-dev.properties`). To send status events through RabbitMQ (merchant webhooks, notifications), run `docker compose up -d rabbitmq` and start the backend with `DEV_MESSAGING_ENABLED=true` and the same `RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`.

## API Tests and Kubernetes

- `postman/`: Postman collection and local environment for the REST API, plus the Newman
  smoke run used in CI. See `postman/README.md`.
- `k8s/`: Kubernetes manifests (namespace, ConfigMap, Secret example, Services, Deployments,
  database jobs). See `k8s/README.md`. Credentials go in your own copy of
  `k8s/secret.example.yaml`, never in the repository.

## Development Notes

- Docker Compose provides a consistent local development environment for the team.
- Each team member should create their own local `.env` file.
- Environment files containing credentials must not be committed to Git.
- Database data persists through the named Docker volume `postgres_data`.
- Application services can be rebuilt and restarted using Docker Compose.

## Team

Developed as a group assignment for the Enterprise Application Development module.

## License

Academic project – University of Moratuwa.
