-- 停止旧版后端并备份后，在尚无 version 列的现有数据库上执行一次。
-- 开发模式 ddl-auto=update 会自动添加；已存在列时不要重复执行。
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
