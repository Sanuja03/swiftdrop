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
POSTGRES_PASSWORD=your_local_password
```

Use a suitable local development password. Do not commit the `.env` file or share production credentials.

### 3. Build and Start the Application

Run the following command from the project root:

```bash
docker compose up --build -d
```

Docker Compose builds the application images and starts the frontend, backend, and PostgreSQL database containers.

### 4. Access the Application

Once the containers have started, the services are available at:

| Service | URL / Port |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Backend Health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5432 |

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

To remove the containers, network, and database volume:

```bash
docker compose down -v
```

**Warning:** Using `-v` deletes the persisted local PostgreSQL database data.

## Docker Services

The current Docker Compose configuration includes:

| Service | Technology | Container Port | Host Port |
|---|---|---:|---:|
| Frontend | React + Nginx | 80 | 5173 |
| Backend | Spring Boot | 8080 | 8080 |
| Database | PostgreSQL 17 | 5432 | 5432 |

The backend connects to PostgreSQL through Docker's internal service network using the hostname `postgres`.

RabbitMQ and other infrastructure integrations will be incorporated.

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
