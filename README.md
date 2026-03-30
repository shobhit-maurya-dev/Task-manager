# TaskFlow

TaskFlow app(Full-stack Web Application). Angular 17 frontend, Spring Boot 3 backend, PostgreSQL database.

## Stack

- **Frontend:** Angular 17, Bootstrap 5, Tailwind CSS 3
- **Backend:** Spring Boot 3, Spring Security, JWT (jjwt 0.12.5)
- **Database:** PostgreSQL
- **Tests:** Jasmine/Karma (frontend), JUnit + Mockito (backend)

## Requirements

- Node.js 18+, npm 9+
- Angular CLI 17 — `npm i -g @angular/cli`
- Java 17+
- Maven 3.8+
- PostgreSQL 14+

## Setup

**1. Database**

```bash
psql -U postgres
```
```sql
CREATE DATABASE "Task-Flow";
\c "Task-Flow"
\i database/schema.sql
-- optional test data:
\i database/seed.sql
```

**2. Backend**

```bash
cd backend
```

Edit `src/main/resources/application.properties` — update your DB credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/Task-Flow
spring.datasource.username=postgres
spring.datasource.password=<your-password>
```

```bash
mvn spring-boot:run
# runs on http://localhost:8080
```

**3. Frontend**

```bash
cd frontend
npm install
ng serve
# runs on http://localhost:4200
```

Proxy is configured — all `/api` calls go to `:8080` automatically.

## Structure

```
backend/
  controller/    — REST endpoints (Auth, Task, User, Comments, ActivityFeed)
  service/       — business logic layer
  repository/    — JPA data access
  model/         — entities (User, Task, TaskComment, ActivityLog)
  config/        — Spring Security, JWT filter, CORS
  dto/           — request/response objects
  exception/     — global error handling

frontend/src/app/
  auth/          — login, register components
  tasks/         — dashboard, task-form components
  shared/        — navbar, 404 page, toast notifications
  services/      — auth, task, comment, activity, analytics services
  guards/        — auth guard (protected routes), guest guard
  interceptors/  — JWT attach + 401 auto-logout
  models/        — TypeScript interfaces
  pipes/         — relative-time, assignee-filter

database/
  schema.sql     — full schema (users, tasks, comments, activity_log)
  seed.sql       — sample data
```

## What's Working

**Auth**
- Register with validation (email format, password strength, duplicate check)
- Login → JWT token stored in localStorage (24h expiry)
- Logout clears token, redirects to login
- Route guards on all protected pages

**Tasks**
- Create, view, edit, delete — full CRUD
- Card grid layout with status badges
- Filter by status (All / Pending / In Progress / Completed)
- Filter by priority, search by title/description
- Quick status toggle (Start / Complete) directly on cards
- Delete confirmation dialog
- Overdue detection with visual warnings

**Extras (beyond base requirements)**
- Task comments (add, view, delete own)
- Task assignment to other users
- Priority system (Low / Medium / High)
- Analytics panel with Chart.js (status + priority breakdown)
- Activity feed (tracks all task events)
- Dark mode (toggle in navbar, persisted)
- Toast notification system

**Styling approach:** Bootstrap 5 handles layout, grid, components (navbar, modals, buttons, badges, forms). Tailwind handles spacing, colors, shadows, hover states, responsive tweaks. Dark mode theming uses CSS custom properties for variable-based color switching.

## API

| Method | Endpoint | Auth | What it does |
|--------|----------|------|-------------|
| POST | /api/auth/register | No | Register user |
| POST | /api/auth/login | No | Login, get JWT |
| GET | /api/tasks | Yes | User's tasks (filterable) |
| POST | /api/tasks | Yes | Create task |
| GET | /api/tasks/{id} | Yes | Single task |
| PUT | /api/tasks/{id} | Yes | Update task |
| DELETE | /api/tasks/{id} | Yes | Delete task |
| GET | /api/tasks/summary | Yes | Analytics data |
| GET/POST/DELETE | /api/tasks/{id}/comments | Yes | Task comments |
| GET | /api/activity | Yes | Activity feed |
| GET | /api/users | Yes | User list (for assignment) |

## Tests

```bash
# frontend — ~77 specs
cd frontend
ng test --watch=false --browsers=ChromeHeadless

# backend — 45 integration tests
cd backend
mvn test
```

Frontend tests cover auth service, task service, login/register/dashboard components.
Backend tests cover auth endpoints, task CRUD, comments, assignment, priority, analytics, activity feed, due date alerts. Uses H2 in-memory DB for isolated test runs.
