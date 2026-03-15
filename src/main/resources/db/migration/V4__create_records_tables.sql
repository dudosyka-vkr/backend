CREATE TABLE IF NOT EXISTS records (
    id SERIAL PRIMARY KEY,
    test_id INTEGER NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    user_login VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_records_test_id ON records(test_id);
CREATE INDEX idx_records_user_login ON records(user_login);
CREATE INDEX idx_records_started_at ON records(started_at);

CREATE TABLE IF NOT EXISTS record_items (
    id SERIAL PRIMARY KEY,
    record_id INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,
    image_id INTEGER NOT NULL REFERENCES test_images(id) ON DELETE CASCADE,
    metrics_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_record_items_record_id ON record_items(record_id);
