CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);

CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (LOWER(username));
