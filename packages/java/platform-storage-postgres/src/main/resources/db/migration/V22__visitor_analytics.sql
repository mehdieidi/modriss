CREATE TABLE user_login_events
(
    id              text        PRIMARY KEY,
    user_id         text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email_snapshot  text        NOT NULL,
    ip_address      text        NOT NULL DEFAULT '',
    country         text        NOT NULL DEFAULT '',
    os              text        NOT NULL DEFAULT '',
    browser         text        NOT NULL DEFAULT '',
    device          text        NOT NULL DEFAULT '',
    user_agent      text        NOT NULL DEFAULT '',
    request_id      text,
    occurred_at     timestamptz NOT NULL
);
CREATE INDEX user_login_events_user_occurred_idx ON user_login_events (user_id, occurred_at DESC);
CREATE INDEX user_login_events_occurred_idx ON user_login_events (occurred_at DESC);

CREATE TABLE landing_page_visits
(
    id              text        PRIMARY KEY,
    ip_address      text        NOT NULL DEFAULT '',
    country         text        NOT NULL DEFAULT '',
    os              text        NOT NULL DEFAULT '',
    browser         text        NOT NULL DEFAULT '',
    device          text        NOT NULL DEFAULT '',
    user_agent      text        NOT NULL DEFAULT '',
    path            text        NOT NULL DEFAULT '',
    referrer        text        NOT NULL DEFAULT '',
    request_id      text,
    occurred_at     timestamptz NOT NULL
);
CREATE INDEX landing_page_visits_occurred_idx ON landing_page_visits (occurred_at DESC);
