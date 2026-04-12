CREATE TABLE one_image_test_pass_tokens (
    id                SERIAL PRIMARY KEY,
    one_image_test_id INTEGER NOT NULL REFERENCES one_image_tests(id) ON DELETE CASCADE,
    code              VARCHAR(8) NOT NULL UNIQUE
);
