# API Guide

Task Management REST API. For installation and PostgreSQL setup, see the [README](../README.md).

## Try the API

Use [requests.http](../requests.http) in IntelliJ's HTTP client, or this PowerShell walkthrough:

```powershell
$base = 'http://localhost:8080/api'
$auth = Invoke-RestMethod "$base/auth/register" -Method Post -ContentType 'application/json' -Body '{"email":"alex@example.com","name":"Alex","password":"a-long-example-password"}'
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }
$project = Invoke-RestMethod "$base/projects" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"name":"Launch","description":"Product launch"}'
$task = Invoke-RestMethod "$base/projects/$($project.id)/tasks" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"title":"Prepare release notes","description":"Draft the announcement","status":"TODO","priority":"HIGH","dueDate":"2026-12-01"}'
Invoke-RestMethod "$base/tasks?status=TODO&sort=dueDate,asc" -Headers $headers
Invoke-RestMethod "$base/tasks/$($task.id)/comments" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"body":"Draft is ready for review."}'
```

Registration returns `201` and a bearer token. Login uses the same email/password and returns `200`. Emails are stored lowercase. Passwords must contain 12–72 characters and at most 72 UTF-8 bytes. Passwords are hashed with BCrypt cost 12. Access tokens last one hour; log in again after expiry. JWT verification uses Spring Security's resource server support ([reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)).

## Endpoints

Every endpoint except registration, login, and health requires `Authorization: Bearer <accessToken>`. Request bodies are JSON unless marked multipart. IDs are UUIDs.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Application liveness |
| POST | `/api/auth/register` | Register: `email`, `name`, `password` |
| POST | `/api/auth/login` | Login: `email`, `password` |
| GET | `/api/users/me` | Current profile |
| PATCH | `/api/users/me` | Change display name: `name` |
| PUT | `/api/users/me/password` | Change password: `currentPassword`, `newPassword`; returns 204 and revokes all existing tokens |
| GET | `/api/projects` | List projects you belong to |
| POST | `/api/projects` | Create project: `name`, optional `description` |
| GET | `/api/projects/{projectId}` | Project details |
| PUT | `/api/projects/{projectId}` | Owner updates project: `name`, optional `description` |
| DELETE | `/api/projects/{projectId}` | Owner deletes project and all its tasks, comments, and attachments |
| GET | `/api/projects/{projectId}/invitation` | Owner retrieves invite code |
| POST | `/api/projects/{projectId}/invitation` | Owner rotates invite code; previous code stops working |
| POST | `/api/projects/join` | Join using `inviteCode`; repeat joins are idempotent |
| GET | `/api/projects/{projectId}/members` | List members and owner/member roles |
| POST | `/api/projects/{projectId}/members` | Owner adds an already registered user by `email` |
| DELETE | `/api/projects/{projectId}/members/{userId}` | Owner removes member, or member leaves; owner's membership cannot be removed |
| GET | `/api/tasks` | List accessible tasks with filters, search, pagination, and sorting |
| POST | `/api/projects/{projectId}/tasks` | Create task in project |
| GET | `/api/tasks/{taskId}` | Task details |
| PUT | `/api/tasks/{taskId}` | Replace editable task fields using current `version` |
| DELETE | `/api/tasks/{taskId}` | Delete task and its comments and attachments |
| GET | `/api/tasks/{taskId}/comments` | List comments |
| POST | `/api/tasks/{taskId}/comments` | Add comment: `body` |
| PUT | `/api/tasks/{taskId}/comments/{commentId}` | Author edits comment: `body` |
| DELETE | `/api/tasks/{taskId}/comments/{commentId}` | Author or owner deletes comment |
| GET | `/api/tasks/{taskId}/attachments` | List attachment metadata |
| POST | `/api/tasks/{taskId}/attachments` | Upload multipart field `file`, at most 10 MiB |
| GET | `/api/tasks/{taskId}/attachments/{attachmentId}` | Download file |
| DELETE | `/api/tasks/{taskId}/attachments/{attachmentId}` | Uploader or owner deletes attachment |

Task creation requires `title`, `status` (`TODO`, `IN_PROGRESS`, `DONE`), and `priority` (`LOW`, `MEDIUM`, `HIGH`, `URGENT`). Optional fields are `description`, `dueDate` (ISO date, `YYYY-MM-DD`), and `assigneeId`. Past due dates are allowed. Task updates use the same fields plus the numeric `version` returned by the last read. `PUT` replaces all editable fields; omitting `assigneeId` or `dueDate` clears it, and omitting `description` sets it to an empty string. A stale version returns `409`; reload before retrying.

### Task queries and pagination

`GET /api/tasks` accepts `projectId`, `status`, `priority`, `assigneeId`, `dueFrom`, `dueTo`, `q`, `page`, `size`, and `sort`. Filters are combined with AND. Search matches title or description, case insensitively, with SQL wildcard characters treated literally. Date bounds are inclusive. Tasks with no due date do not match date bounds.

```http
GET /api/tasks?projectId=<uuid>&status=IN_PROGRESS&priority=HIGH&q=release&dueTo=2026-12-31&sort=dueDate,asc&page=0&size=20
```

Pages are zero based; size defaults to 20 and is capped at 100. Lists return `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`. Task sort fields are `createdAt`, `updatedAt`, `title`, and `dueDate`; use `field,asc` or `field,desc`. Default is `createdAt,desc`. A UUID tie breaker keeps pagination deterministic. Project lists support `name` and `createdAt`; comments, attachments, and memberships sort chronologically.

### Access rules and file handling

- Only members can access a project's tasks, comments, files, and member list. Requests from outsiders return `404`. Global task search only returns tasks from the caller's projects.
- All project members can create, edit, assign, and delete tasks. Assignees must be current members. Only the owner can change or delete a project, add members directly, or retrieve/rotate its invitation.
- Invitations are random 256 bit bearer secrets. Share them privately; anyone with a code and an account can join. Rotating a code does not remove existing members.
- Removing a member immediately revokes project access and clears their assignments. Existing comments and uploads retain their author IDs. The owner may delete these contributions.
- Project mutations serialize against membership changes. Tasks also use optimistic version checks to reject stale edits.
- Files are stored as database binary data with their metadata; cascade deletion is atomic. Lists exclude file content. Downloads require membership, sanitize filenames, use `Content-Disposition: attachment`, and force `application/octet-stream` with `nosniff`. Empty files are rejected. Uploaded content is not virus scanned.
- Authentication endpoints allow 20 combined requests per minute per remote IP, per application instance. Exceeding the limit returns `429` with `Retry-After`. Behind a proxy or with multiple instances, configure trusted proxy handling and a shared edge rate limiter.

### Errors

Errors consistently return JSON with `timestamp`, `status`, `error`, `message`, `path`, and `fields`. Field validation failures populate `fields`. Common statuses are `400` for invalid input, `401` for invalid credentials/token, `403` for disallowed member actions, `404` for absent/inaccessible resources, `409` for conflicts or stale edits, `413` for oversized uploads, and `429` for authentication rate limiting. Stack traces and password hashes are never returned.
