-- Drop old simple-test tables in FK order
DROP TABLE IF EXISTS test_pass_tokens;
DROP TABLE IF EXISTS record_items;
DROP TABLE IF EXISTS records;
DROP TABLE IF EXISTS test_images;
DROP TABLE IF EXISTS tests;

-- Rename one_image_* tables
ALTER TABLE one_image_tests RENAME TO tests;
ALTER TABLE one_image_test_records RENAME TO records;
ALTER TABLE one_image_test_pass_tokens RENAME TO test_pass_tokens;

-- Rename FK columns to drop the one_image prefix
ALTER TABLE records RENAME COLUMN one_image_test_id TO test_id;
ALTER TABLE test_pass_tokens RENAME COLUMN one_image_test_id TO test_id;

-- Rename indexes
ALTER INDEX IF EXISTS idx_one_image_test_records_test_id RENAME TO idx_records_test_id;
ALTER INDEX IF EXISTS idx_one_image_tests_user_id RENAME TO idx_tests_user_id;
