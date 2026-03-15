CREATE TABLE IF NOT EXISTS tests (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cover_filename VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tests_user_id ON tests(user_id);

CREATE TABLE IF NOT EXISTS test_images (
    id SERIAL PRIMARY KEY,
    test_id INTEGER NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL
);

CREATE INDEX idx_test_images_test_id ON test_images(test_id);
