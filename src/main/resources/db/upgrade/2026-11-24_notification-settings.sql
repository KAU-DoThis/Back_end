-- =====================================================================
-- 알림 설정(v4 9장)을 사용자당 한 행으로 바꾸는 증분 SQL (MySQL 8.x)
-- 이전 notification_settings(종류별 행)는 쓰는 코드가 없어 지우고 새로 만든다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-24_notification-settings.sql
-- =====================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS notification_settings;

CREATE TABLE notification_settings (
    notification_setting_id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                       BIGINT      NOT NULL,
    push_enabled                  TINYINT(1)  NOT NULL DEFAULT 1,
    context_enabled               TINYINT(1)  NOT NULL DEFAULT 1,
    context_frequency             VARCHAR(20) NOT NULL DEFAULT 'MAX_3_PER_DAY' COMMENT 'EACH_SYNC / MAX_3_PER_DAY / DAILY_DIGEST',
    quiet_start                   TIME        NOT NULL DEFAULT '23:00:00',
    quiet_end                     TIME        NOT NULL DEFAULT '07:00:00',
    burn_rate_enabled             TINYINT(1)  NOT NULL DEFAULT 1,
    fixed_shortage_enabled        TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '긴급. 끌 때 한 번 확인받는다',
    goal_behind_enabled           TINYINT(1)  NOT NULL DEFAULT 1,
    saving_report_enabled         TINYINT(1)  NOT NULL DEFAULT 1,
    marketing_enabled             TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'MARKETING 동의가 있어야 켤 수 있다',
    daily_guide_time              TIME        NOT NULL DEFAULT '08:00:00',
    lock_screen_hide_amount       TINYINT(1)  NOT NULL DEFAULT 0,
    urgent_off_confirmed          TINYINT(1)  NOT NULL DEFAULT 0,
    created_at                    DATETIME(6) NOT NULL,
    updated_at                    DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_setting_id),
    UNIQUE KEY uk_notification_settings_user (user_id),
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
