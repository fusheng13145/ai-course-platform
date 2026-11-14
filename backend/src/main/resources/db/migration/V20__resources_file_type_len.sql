-- V20：resources / resource_versions 的 file_type 扩容到 100。
-- 背景：上传白名单允许 docx/xlsx（MIME 最长 71 字符，如
-- application/vnd.openxmlformats-officedocument.wordprocessingml.document），
-- 但两表列宽均为 varchar(50)，任何 Word/Excel 课件上传都会触发
-- Data truncation → 400「数据完整性校验失败」。扩容至 100 与白名单对齐。
ALTER TABLE resources MODIFY COLUMN file_type varchar(100) NULL;
ALTER TABLE resource_versions MODIFY COLUMN file_type varchar(100) NULL;

