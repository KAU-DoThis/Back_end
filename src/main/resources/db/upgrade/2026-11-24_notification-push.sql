-- =====================================================================
-- 알림 푸시 발송(F-22)을 위한 증분 SQL (MySQL 8.x)
-- 2026-11-24_notifications.sql 다음에 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-24_notification-push.sql
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 알림마다 푸시를 보냈는지. 받지 않는 시간이면 HELD로 두고 끝나면 보낸다
ALTER TABLE notifications
    ADD COLUMN push_status VARCHAR(10) NULL COMMENT 'HELD / SENT / SKIPPED / FAILED' AFTER read_at,
    ADD COLUMN pushed_at   DATETIME(6) NULL AFTER push_status,
    ADD KEY idx_notifications_push (push_status, created_at);

-- 2. 발송 기록에 어떤 알림인지 남긴다 (알림은 30일 뒤 지워지므로 FK는 걸지 않는다)
ALTER TABLE notification_logs
    ADD COLUMN notification_id BIGINT NULL COMMENT '보낸 알림' AFTER user_id;
