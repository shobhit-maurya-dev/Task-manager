-- ============================================================
-- TaskFlow Database Schema (PostgreSQL)
-- ============================================================

-- Run this after creating the database:
--   CREATE DATABASE "Task-Flow";
-- Then connect to it:
--   \c "Task-Flow"

--,─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(100) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL ,
    role_change_count     INT          NOT NULL DEFAULT 0,
    last_role_change_at   TIMESTAMP,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN','MANAGER','MEMBER','VIEWER'))
);

--Tasks Table (extended: assigned_to, priority)
CREATE TABLE IF NOT EXISTS tasks (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(100)  NOT NULL,
    description TEXT,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    priority    VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    due_date    DATE,
    user_id     BIGINT        NOT NULL,
    assigned_to BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_user       FOREIGN KEY (user_id)     REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee   FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_status   CHECK (status   IN ('PENDING','IN_PROGRESS','COMPLETED')),
    CONSTRAINT chk_priority CHECK (priority IN ('LOW','MEDIUM','HIGH'))
);

-- ─── Task Comments Table (F-EXT-01) 
CREATE TABLE IF NOT EXISTS task_comments (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT    NOT NULL,
    author_id   BIGINT    NOT NULL,
    body        TEXT      NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_task   FOREIGN KEY (task_id)   REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ─── Activity Log Table (F-EXT-05)
CREATE TABLE IF NOT EXISTS activity_log (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT,
    actor_id    BIGINT       NOT NULL,
    action_code VARCHAR(50)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_task  FOREIGN KEY (task_id)  REFERENCES tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_activity_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE
);


-- F-W2-01 - Add is_active and member_type to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS member_type VARCHAR(20) NOT NULL DEFAULT 'DEVELOPER',
ADD CONSTRAINT IF NOT EXISTS chk_member_type CHECK (member_type IN ('DEVELOPER','TESTER','DESIGNER'));

-- F-W2-01 - Create teams table with manager_id foreign key
CREATE TABLE IF NOT EXISTS teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    manager_id BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_manager FOREIGN KEY (manager_id) REFERENCES users(id) ON DELETE CASCADE
);

-- F-W2-01 
CREATE TABLE IF NOT EXISTS team_members (
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- F-W2-01 
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS team_id BIGINT ;
ALTER TABLE tasks ADD CONSTRAINT IF NOT EXISTS fk_tasks_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL;






-- ─── Indexes 
CREATE INDEX IF NOT EXISTS idx_tasks_user_id     ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX IF NOT EXISTS idx_tasks_status      ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority    ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_comments_task_id  ON task_comments(task_id);
CREATE INDEX IF NOT EXISTS idx_activity_actor    ON activity_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_activity_task     ON activity_log(task_id);

-- ─── Trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
