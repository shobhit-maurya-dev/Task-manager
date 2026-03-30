-- V6: Many-to-Many Task Assignees
-- Migration from single assigned_to column to multiple assignees join table

CREATE TABLE IF NOT EXISTS task_assignees (
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, user_id),
    CONSTRAINT fk_task_assignee_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignee_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Copy existing assignments to the new join table
INSERT INTO task_assignees (task_id, user_id)
SELECT id, assigned_to FROM tasks WHERE assigned_to IS NOT NULL;

-- Note: We keep the 'assigned_to' column in 'tasks' for now to avoid breaking existing queries 
-- that haven't been refactored yet. We can drop it in a future cleanup migration.
