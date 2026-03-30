-- Migration V3: Add missing tables/columns for new features (subtasks, time tracking, user settings)
-- This migration is written defensively so it can be applied to existing databases without failing.

-- 1) Subtasks enhancements (created_by and assigned_to)
ALTER TABLE IF EXISTS subtasks
    ADD COLUMN IF NOT EXISTS assigned_to_id BIGINT;

ALTER TABLE IF EXISTS subtasks
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_subtask_assigned_to_user'
    ) THEN
        ALTER TABLE subtasks
            ADD CONSTRAINT fk_subtask_assigned_to_user
            FOREIGN KEY (assigned_to_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_subtask_created_by_user'
    ) THEN
        ALTER TABLE subtasks
            ADD CONSTRAINT fk_subtask_created_by_user
            FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END
$$;

-- 2) Time tracking: modify time_entries
ALTER TABLE IF EXISTS time_entries
    ADD COLUMN IF NOT EXISTS log_date DATE;

ALTER TABLE IF EXISTS time_entries
    ADD COLUMN IF NOT EXISTS is_manual BOOLEAN DEFAULT TRUE;

-- Ensure not-null constraints are satisfied if the columns already exist but are null.
-- NOTE: This may require manual data cleanup for existing rows.

-- 3) Active timers table
CREATE TABLE IF NOT EXISTS active_timers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_active_timer_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_active_timer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_active_timer_task_user UNIQUE (task_id, user_id)
);

-- 4) User settings table
CREATE TABLE IF NOT EXISTS user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    timezone VARCHAR(64),
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dark_mode BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
