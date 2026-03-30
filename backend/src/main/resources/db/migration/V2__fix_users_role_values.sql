-- Ensure existing user rows have valid roles and member_type values before enforcing constraints.
-- This is intended for deployed databases where invalid values may already exist.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users') THEN

    -- Ensure the role constraint allows the values we will normalize to
    ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
    ALTER TABLE users
      ADD CONSTRAINT users_role_check
      CHECK (role IN ('ADMIN','MANAGER','MEMBER','VIEWER')) NOT VALID;

    -- Ensure the member_type constraint allows the values we will normalize to
    ALTER TABLE users DROP CONSTRAINT IF EXISTS users_member_type_check;
    ALTER TABLE users
      ADD CONSTRAINT users_member_type_check
      CHECK (member_type IN ('DEVELOPER','TESTER','DESIGNER')) NOT VALID;

    -- Now normalize data so it satisfies the constraints
    UPDATE users
    SET role = 'MEMBER'
    WHERE role NOT IN ('ADMIN', 'MANAGER', 'MEMBER', 'VIEWER');

    UPDATE users
    SET member_type = 'DEVELOPER'
    WHERE member_type NOT IN ('DEVELOPER', 'TESTER', 'DESIGNER');

    -- Validate constraints now that data is fixed
    ALTER TABLE users VALIDATE CONSTRAINT users_role_check;
    ALTER TABLE users VALIDATE CONSTRAINT users_member_type_check;

  END IF;
END
$$;
