-- Migration V4: Ensure task_attachments.file_data is stored as bytea
-- Fixes issues where Hibernate tries to convert the column to oid.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'task_attachments'
          AND column_name = 'file_data'
          AND data_type <> 'bytea'
    ) THEN
        ALTER TABLE task_attachments
            ALTER COLUMN file_data TYPE bytea USING file_data::bytea;
    END IF;
END
$$;
