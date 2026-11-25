CREATE TABLE videos (
    id             UUID PRIMARY KEY,
    owner_id       VARCHAR(100)  NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    description    TEXT,
    file_name      VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    s3_key         VARCHAR(500)  NOT NULL UNIQUE,
    cdn_url        VARCHAR(1000) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    failure_reason VARCHAR(500),
    views          BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_videos_status CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX idx_videos_owner   ON videos (owner_id);
-- Acelera el orden "más recientes primero" del buscador sobre videos READY.
CREATE INDEX idx_videos_ready_created ON videos (created_at DESC, id DESC) WHERE status = 'READY';
-- Índice parcial: la limpieza de PENDING solo mira esta porción de la tabla.
CREATE INDEX idx_videos_pending ON videos (created_at) WHERE status = 'PENDING';

CREATE TABLE user_favorites (
    user_id    VARCHAR(100) NOT NULL,
    video_id   UUID         NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, video_id)
);

CREATE INDEX idx_favorites_user_created ON user_favorites (user_id, created_at DESC, video_id DESC);

-- Idempotencia de POST /videos: (usuario, clave) -> video creado.
CREATE TABLE idempotency_keys (
    user_id      VARCHAR(100) NOT NULL,
    idem_key     VARCHAR(200) NOT NULL,
    request_hash CHAR(64)     NOT NULL,
    video_id     UUID         NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);

-- Deduplicación de mensajes (entrega at-least-once): (consumer, evento).
CREATE TABLE processed_events (
    consumer     VARCHAR(50)  NOT NULL,
    event_id     VARCHAR(300) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

CREATE INDEX idx_processed_events_at ON processed_events (processed_at);
