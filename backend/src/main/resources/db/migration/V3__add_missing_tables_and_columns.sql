-- Migration V3: Add missing tables/columns for new features (subtasks, time tracking, user settings)
-- This migration is written defensively so it can be applied to existing databases without failing.

-- 1) Subtasks enhancements (created_by and assigned_to)
-- Use native PostgreSQL ADD COLUMN IF NOT EXISTS (supported in PG 9.6+)
ALTER TABLE subtasks ADD COLUMN IF NOT EXISTS assigned_to_id BIGINT;
ALTER TABLE subtasks ADD COLUMN IF NOT EXISTS created_by BIGINT;

DO $$
BEGIN
    -- Check for subtasks table existence just to be safe
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'subtasks') THEN
        
        -- Add constraint for assigned_to_id if it doesn't exist
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subtask_assigned_to_user') THEN
            ALTER TABLE subtasks
                ADD CONSTRAINT fk_subtask_assigned_to_user
                FOREIGN KEY (assigned_to_id) REFERENCES users(id) ON DELETE SET NULL;
        END IF;

        -- Add constraint for created_by if it doesn't exist
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_subtask_created_by_user') THEN
            ALTER TABLE subtasks
                ADD CONSTRAINT fk_subtask_created_by_user
                FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;
        END IF;
    END IF;
END $$;

-- 2) Time tracking: modify time_entries
ALTER TABLE time_entries ADD COLUMN IF NOT EXISTS log_date DATE;
ALTER TABLE time_entries ADD COLUMN IF NOT EXISTS is_manual BOOLEAN DEFAULT TRUE;

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

