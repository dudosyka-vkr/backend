CREATE TABLE one_image_test_records (
    id                  SERIAL PRIMARY KEY,
    one_image_test_id   INTEGER NOT NULL REFERENCES one_image_tests(id) ON DELETE CASCADE,
    user_login          VARCHAR(255) NOT NULL,
    metrics_json        TEXT NOT NULL,
    started_at          TIMESTAMP NOT NULL,
    finished_at         TIMESTAMP NOT NULL,
    duration_ms         BIGINT NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_one_image_test_records_test_id ON one_image_test_records(one_image_test_id);
