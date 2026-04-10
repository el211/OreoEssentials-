-- ============================================================
-- OreoEssentials Web Panel — initial schema
-- ============================================================

CREATE TABLE IF NOT EXISTS web_users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(64)  NOT NULL UNIQUE,
    email               VARCHAR(256) NOT NULL UNIQUE,
    password_hash       TEXT         NOT NULL,
    role                VARCHAR(16)  NOT NULL CHECK (role IN ('PLAYER','OWNER','ADMIN')),
    minecraft_uuid      UUID,
    minecraft_username  VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_web_users_email          ON web_users (email);
CREATE INDEX IF NOT EXISTS idx_web_users_username       ON web_users (username);
CREATE INDEX IF NOT EXISTS idx_web_users_minecraft_uuid ON web_users (minecraft_uuid);

-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS servers (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    slug           VARCHAR(64)  NOT NULL UNIQUE,
    logo_url       VARCHAR(512),
    description    VARCHAR(512),
    owner_id       BIGINT       NOT NULL REFERENCES web_users(id) ON DELETE CASCADE,
    api_key_hash   TEXT,
    api_key_prefix VARCHAR(16),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_servers_slug        ON servers (slug);
CREATE INDEX IF NOT EXISTS idx_servers_owner_id    ON servers (owner_id);
CREATE INDEX IF NOT EXISTS idx_servers_key_prefix  ON servers (api_key_prefix);

-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS server_configs (
    server_id            BIGINT      PRIMARY KEY REFERENCES servers(id) ON DELETE CASCADE,
    data_source_mode     VARCHAR(16) NOT NULL DEFAULT 'API_SYNC'
                             CHECK (data_source_mode IN ('API_SYNC','DIRECT_MONGO','HYBRID')),
    encrypted_mongo_uri  VARCHAR(1024),
    mongo_database_name  VARCHAR(128),
    last_synced_at       TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS player_server_links (
    id             BIGSERIAL PRIMARY KEY,
    player_id      BIGINT      NOT NULL REFERENCES web_users(id) ON DELETE CASCADE,
    server_id      BIGINT      NOT NULL REFERENCES servers(id)   ON DELETE CASCADE,
    minecraft_uuid UUID        NOT NULL,
    verified       BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at    TIMESTAMPTZ,
    CONSTRAINT uq_player_server UNIQUE (player_id, server_id)
);

CREATE INDEX IF NOT EXISTS idx_psl_player_id      ON player_server_links (player_id);
CREATE INDEX IF NOT EXISTS idx_psl_server_id      ON player_server_links (server_id);
CREATE INDEX IF NOT EXISTS idx_psl_minecraft_uuid ON player_server_links (minecraft_uuid);

-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS cached_player_data (
    id              BIGSERIAL PRIMARY KEY,
    link_id         BIGINT      NOT NULL UNIQUE REFERENCES player_server_links(id) ON DELETE CASCADE,
    data_json       TEXT        NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cpd_link_id ON cached_player_data (link_id);

-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES web_users(id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_rt_user_id    ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_rt_token_hash ON refresh_tokens (token_hash);
