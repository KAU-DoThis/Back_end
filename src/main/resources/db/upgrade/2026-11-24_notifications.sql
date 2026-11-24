-- =====================================================================
-- 알림함(v4 9장)을 위한 증분 SQL (MySQL 8.x)
-- schema.sql은 빈 DB용이라 이미 만들어진 DB에는 이 파일을 한 번 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-24_notifications.sql
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE notifications (
    notification_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    type                VARCHAR(30)  NOT NULL COMMENT 'CONTEXT_QUESTION / BURN_RATE / GOAL_ACHIEVED / REPORT 등 (v4 9장)',
    title               VARCHAR(255) NOT NULL,
    body                VARCHAR(500) NULL,
    ref_id              BIGINT       NULL COMMENT '눌렀을 때 열 대상 ID (질문이면 context_question_id)',
    read_at             DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_id),
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
