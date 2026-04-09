CREATE TABLE test_pass_tokens (
    id   SERIAL PRIMARY KEY,
    code VARCHAR(8)  NOT NULL UNIQUE,
    test_id INTEGER  NOT NULL REFERENCES tests(id)
);
