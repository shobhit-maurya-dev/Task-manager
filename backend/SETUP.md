# 🚀 TaskFlow Backend Setup Guide

Welcome to the TaskFlow Backend! This guide will help you set up and run the project on your local machine.

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed:
- **Java 17** (OpenJDK or Oracle)
- **Maven 3.8+**
- **PostgreSQL 14+**
- An IDE (IntelliJ IDEA, VS Code, or Eclipse)

---

## 🏗️ 1. Database Setup

1.  **Start your PostgreSQL server.**
2.  **Create a database** named `Task-Flow`:
    ```sql
    CREATE DATABASE "Task-Flow";
    ```
3.  **Default Credentials**:
    - The `dev` profile expects the following:
      - **Username**: `postgres`
      - **Password**: `kali`
    - If your credentials are different, update them in `src/main/resources/application-dev.properties`.

---

## 🔑 2. Secrets Configuration

The project requires an `application-secrets.properties` file in `src/main/resources/`. If it doesn't exist, create it:

```properties
# Supabase Production Credentials (Only needed for 'prod' mode)
DATABASE_URL=jdbc:postgresql://db.[PROJECT_ID].supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres.[PROJECT_ID]
DATABASE_PASSWORD=[YOUR_PASSWORD]

# Gemini AI API Key
gemini.api.key=[YOUR_GEMINI_API_KEY]
```

---

## 🚀 3. Running the Application

### 🛠️ Local Development (Recommended)
This mode uses your local PostgreSQL database. It is the **default** mode.
```bash
mvn spring-boot:run
```

### 🚀 Production Mode (Supabase)
This mode connects to the cloud Supabase database.
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 🧪 Running Tests
```bash
mvn test
```

---

## 📂 4. Project Structure

- `src/main/java/com/taskflow/config`: Security, JWT, and Cloud configurations.
- `src/main/java/com/taskflow/controller`: REST API endpoints.
- `src/main/java/com/taskflow/service`: Business logic.
- `src/main/resources/db/migration`: Flyway SQL migration scripts.
- `src/main/resources/application.properties`: Main settings.

---

## 💡 5. Common Issues & Troubleshooting

- **Database Connection Refused**: Ensure PostgreSQL is running and the database `Task-Flow` exists.
- **Port 8080 already in use**: Change the port in `application.properties` or run `netstat -ano | findstr :8080` to find and kill the process.
- **Flyway Version Mismatch**: If you face migration errors, ensure you are running against a clean database or use `mvn flyway:repair`.

---

Developed with ❤️ for TaskFlow.
