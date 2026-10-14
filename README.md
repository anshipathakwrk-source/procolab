# Tandem – Project Collaboration Platform

Java full-stack web app for teams to plan projects, assign and track tasks, share files,
discuss work in threaded comments and monitor progress.

**Stack:** Java 17 · Spring Boot 3 (Web, Data JPA, Security, Validation) · MySQL 8 · JWT · HTML/CSS/vanilla JavaScript

## 1. Prerequisites
- JDK 17+
- Maven 3.8+ (or use your IDE's Maven support)
- MySQL 8 running on `localhost:3306`

## 2. Database setup (pick one)
**A. Let Spring create the tables (easiest).** Nothing to do. `createDatabaseIfNotExist=true` and
`spring.jpa.hibernate.ddl-auto=update` build the schema on first run.

**B. Run the SQL script yourself.**
```bash
mysql -u root -p < database/schema.sql
```
Then set `spring.jpa.hibernate.ddl-auto=none` in `application.properties`.

## 3. Configure
Edit `src/main/resources/application.properties`, or use environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | MySQL login |
| `JWT_SECRET` | dev placeholder | **Change this**, minimum 32 characters |
| `UPLOAD_DIR` | `uploads` | Where uploaded files are stored |

## 4. Run
```bash
mvn spring-boot:run
```
Open **http://localhost:8080**, create an account, then create a project.
To test teamwork, register a second account (private window) and add it by email from the **Team** tab.

## 5. Roles and permissions
| Action | Owner | Manager | Member |
|---|:-:|:-:|:-:|
| View project, tasks, files, comments | ✔ | ✔ | ✔ |
| Create tasks, comment, upload files, change task status | ✔ | ✔ | ✔ |
| Edit a task | ✔ | ✔ | creator or assignee only |
| Delete a task / file | ✔ | ✔ | creator or uploader only |
| Edit project, add/remove members | ✔ | ✔ | – |
| Archive / restore project | ✔ | – | – |

## 6. REST API (all `/api/**` routes except `/api/auth/**` need `Authorization: Bearer <token>`)
```
POST   /api/auth/register            POST   /api/auth/login
GET    /api/projects                 POST   /api/projects
GET    /api/projects/{id}            PUT    /api/projects/{id}
PATCH  /api/projects/{id}/archive
GET    /api/projects/{id}/members    POST   /api/projects/{id}/members
DELETE /api/projects/{id}/members/{memberId}
GET    /api/projects/{id}/progress   GET    /api/projects/{id}/activity
GET    /api/projects/{id}/tasks      POST   /api/projects/{id}/tasks
GET    /api/tasks/mine
PUT    /api/tasks/{id}               DELETE /api/tasks/{id}
PATCH  /api/tasks/{id}/status
GET    /api/tasks/{id}/comments      POST   /api/tasks/{id}/comments
DELETE /api/comments/{id}
GET    /api/projects/{id}/files      POST   /api/projects/{id}/files   (multipart: file, taskId?)
GET    /api/files/{id}/download      DELETE /api/files/{id}
```

## 7. Project layout
```
src/main/java/com/collab/platform
  entity/       JPA entities + enums
  repository/   Spring Data repositories
  dto/          request/response records
  service/      business rules and permission checks
  controller/   REST endpoints
  security/     JWT filter, token util, Spring Security config
  exception/    ApiException + global error handler
src/main/resources/static   index.html, css/style.css, js/app.js
database/schema.sql         MySQL script
```

## 8. Ideas for next steps
Email invitations, notifications, task labels and filters, pagination, refresh tokens,
Docker Compose with MySQL, unit tests for the service layer (JUnit + Mockito).

## 9. Deploying to Render
Render offers PostgreSQL (not MySQL), so production uses the `prod` profile (`application-prod.properties`).
1. Push this folder to a GitHub repository.
2. In Render: **New > Blueprint**, choose the repo. `render.yaml` creates the database and web service.
3. Open the `.onrender.com` URL once the deploy finishes.
Free-plan limits: the service sleeps after 15 minutes idle, the free database expires after 30 days,
and uploaded files are wiped on restart (use a paid instance with a disk to keep them).
