-- Ensure users.role/memeber_type check constraints exist and match current enums.
-- This migration is intentionally safe to run even if the tables/constraints do not exist.
ALTER TABLE IF EXISTS users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE IF EXISTS users
  ADD CONSTRAINT users_role_check
  CHECK (role IN ('ADMIN','MANAGER','MEMBER','VIEWER')) NOT VALID;

ALTER TABLE IF EXISTS users DROP CONSTRAINT IF EXISTS users_member_type_check;
ALTER TABLE IF EXISTS users
  ADD CONSTRAINT users_member_type_check
  CHECK (member_type IN ('DEVELOPER','TESTER','DESIGNER')) NOT VALID;

-- Validate constraints if the table exists
ALTER TABLE IF EXISTS users VALIDATE CONSTRAINT users_role_check;
ALTER TABLE IF EXISTS users VALIDATE CONSTRAINT users_member_type_check;
