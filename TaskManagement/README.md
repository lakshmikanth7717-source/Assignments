# Task Management API

A REST backend for teams to organize projects, assign tasks, and collaborate through comments and file attachments.

**Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL, Flyway, and Maven. PostgreSQL is the only supported database.

## Features

- Registration, login, profiles, BCrypt password hashing, and JWT authentication.
- Projects with owner/member permissions, invitations, and task assignments.
- Task CRUD with status, priority, due dates, search, filtering, sorting, and pagination.
- Comments and authenticated attachment upload/download, with a 10 MiB file limit.
- Request validation, consistent JSON errors, login rate limiting, and stale-edit protection.

## Setup

Requires JDK 21 or 25 and a running PostgreSQL server. Docker Compose is an optional way to start PostgreSQL; Maven is included through the wrapper.

```bash
git clone --branch lakshmikanth/task-management https://github.com/lakshmikanth7717-source/Assignments.git
cd Assignments/TaskManagement
```

1. Copy `.env.example` to `.env` and replace the placeholder values. On PowerShell: `Copy-Item .env.example .env`.
2. Configure the PostgreSQL connection and a random JWT secret:

   | Variable | Value |
   | --- | --- |
   | `DB_URL` | `jdbc:postgresql://localhost:5432/task_management` |
   | `DB_USERNAME` | Your PostgreSQL login; `taskapp` for Docker Compose |
   | `DB_PASSWORD` | Password for that login |
   | `JWT_SECRET` | Random secret containing at least 32 UTF-8 bytes |
   | `PORT` | Optional; defaults to `8080` |
   | `CORS_ORIGINS` | Optional comma separated frontend origins |

   Generate a JWT secret in PowerShell, then copy the printed value into `.env`:

   ```powershell
   $bytes = New-Object byte[] 48
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   $rng.Dispose()
   ```

3. Create the database using your PostgreSQL administrator account. For a new application login:

   ```sql
   CREATE ROLE taskapp LOGIN PASSWORD 'your-database-password';
   CREATE DATABASE task_management OWNER taskapp;
   ```

   Alternatively, set `DB_USERNAME=taskapp` in `.env` and run `docker compose up -d --wait db`. Use either an existing server or Compose on port 5432. Compose persists data in its `postgres_data` volume.

4. Start the application from this directory:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   On Linux/macOS: `sh mvnw spring-boot:run`.

The API runs at `http://localhost:8080/api`; `GET /api/health` reports application liveness. Flyway initializes the schema, and Hibernate validates it at startup. No Spring profile is required.

The application loads `.env` as Java properties: use unquoted `KEY=value` entries and escape literal backslashes as `\\`. Environment variables override file values. Credentials and generated files are excluded from Git.

## API usage

Register or log in to receive an `accessToken`:

```http
POST /api/auth/register
Content-Type: application/json

{"email":"alex@example.com","name":"Alex","password":"a-long-example-password"}
```

Send `Authorization: Bearer <accessToken>` with protected requests. Tokens expire after one hour; changing a password revokes existing tokens.

| Resource | Endpoints |
| --- | --- |
| Authentication and profile | `/api/auth/register`, `/api/auth/login`, `/api/users/me` |
| Projects and membership | `/api/projects`, `/api/projects/{id}/members`, `/api/projects/join` |
| Tasks | `/api/projects/{id}/tasks`, `/api/tasks`, `/api/tasks/{id}` |
| Comments | `/api/tasks/{id}/comments` |
| Attachments | `/api/tasks/{id}/attachments` |

See the [API guide](docs/API.md) for methods, payloads, filtering, errors, and permissions. [requests.http](requests.http) contains ready-to-edit IntelliJ HTTP requests.

## Code organization

```text
src/main/java/com/taskmanagement/
  authentication/  Registration, login, JWTs, users, and profiles
  authorization/   HTTP access rules and project permission checks
  project/         Projects, memberships, and invitations
  task/            Task lifecycle and queries
  collaboration/   Comments and attachments
  common/          Errors, pagination, and shared utilities
  health/          Liveness endpoint
src/main/resources/db/migration/  Versioned PostgreSQL schema
```

Each feature owns its controller, service, repository, entities, and API models. Permission decisions are centralized in `ProjectAccess`; database mutations use transactions and task versions protect concurrent edits. `.editorconfig` defines shared formatting conventions.

## Build

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/task-management-1.0.0.jar
```

In IntelliJ, import `pom.xml` as a Maven project, select JDK 21/25, and run `TaskManagementApplication` with this directory as the working directory.

Unit tests are not included, as requested. Real-time updates and notifications are not implemented.
