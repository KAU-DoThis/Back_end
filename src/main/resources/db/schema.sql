-- =====================================================================
-- FINTRIS P0 스키마 (MySQL 8.x)
-- 기준: FINTRIS_개발_컨텍스트_v3.md 8장 (기능 명세서 F-01~F-26)
-- 작성: 2026-10-13
--
-- 규칙
--  - PK: BIGINT AUTO_INCREMENT, 이름은 {단수}_id
--  - 테이블 복수형 snake_case, enum은 VARCHAR (JPA @Enumerated STRING)
--  - 공통 컬럼 created_at, updated_at (이력·로그 테이블은 created_at만)
--  - 금액은 BIGINT(원 단위), FK는 걸되 CASCADE 없음
--
-- TBD
--  - linked_accounts를 connections / linked_accounts로 나눌지 → 지금은 한 테이블
--  - saving_suggestions 저장 여부 → 맨 아래 주석으로만 둠
--  - user_settings.nickname 필요 여부 → NULL 허용으로 둠
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 계정 (F-17, F-18)
-- ---------------------------------------------------------------------

CREATE TABLE users (
    user_id             BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255) NOT NULL,
    password            VARCHAR(255) NULL COMMENT '소셜 가입은 NULL',
    auth_provider       VARCHAR(20)  NOT NULL COMMENT 'EMAIL / GOOGLE / KAKAO',
    email_verified      TINYINT(1)   NOT NULL DEFAULT 0,
    plan                VARCHAR(20)  NOT NULL DEFAULT 'FREE' COMMENT 'FREE / PREMIUM',
    last_login_at       DATETIME(6)  NULL,
    failed_login_count  INT          NOT NULL DEFAULT 0 COMMENT '5회 연속 실패 시 잠금',
    locked_until        DATETIME(6)  NULL COMMENT '10분 로그인 제한',
    deleted_at          DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE social_accounts (
    social_account_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    provider            VARCHAR(20)  NOT NULL COMMENT 'GOOGLE / KAKAO',
    provider_user_id    VARCHAR(255) NOT NULL COMMENT '구글 sub, 카카오 id',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (social_account_id),
    UNIQUE KEY uk_social_provider_user (provider, provider_user_id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    refresh_token_id    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    token               VARCHAR(512) NOT NULL,
    expires_at          DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (refresh_token_id),
    UNIQUE KEY uk_refresh_tokens_token (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 동의는 바뀔 때마다 행을 추가한다(이력). 최신 상태 = item별 가장 최근 행
CREATE TABLE consents (
    consent_id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    item                VARCHAR(40)  NOT NULL COMMENT 'TERMS / PRIVACY / CODEF / AI_PROCESSING / NOTIFICATION_READ / PUSH / PRODUCT_INFO',
    agreed              TINYINT(1)   NOT NULL,
    doc_version         VARCHAR(20)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL COMMENT '= changed_at',
    PRIMARY KEY (consent_id),
    KEY idx_consents_user_item (user_id, item, created_at),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_settings (
    user_setting_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    onboarding_done     TINYINT(1)   NOT NULL DEFAULT 0,
    link_skipped        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '계좌 연동 나중에 하기(체험 모드)',
    nickname            VARCHAR(30)  NULL COMMENT 'TBD: 필요 여부',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_setting_id),
    UNIQUE KEY uk_user_settings_user (user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. 계좌·카드 연동 (F-19)
-- ---------------------------------------------------------------------

CREATE TABLE linked_accounts (
    linked_account_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    institution_code      VARCHAR(10)  NOT NULL,
    type                  VARCHAR(10)  NOT NULL COMMENT 'BANK / CARD',
    connected_id          VARCHAR(512) NOT NULL COMMENT 'CODEF connectedId, 암호화 저장',
    account_name          VARCHAR(100) NULL,
    account_number_last4  CHAR(4)      NULL COMMENT '계좌번호는 뒤 4자리만',
    balance               BIGINT       NULL COMMENT '카드는 NULL',
    balance_updated_at    DATETIME(6)  NULL,
    is_active             TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '미배분 잔액 계산 포함 여부',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / ERROR',
    last_synced_at        DATETIME(6)  NULL,
    deleted_at            DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (linked_account_id),
    KEY idx_linked_accounts_user_active (user_id, is_active),
    CONSTRAINT fk_linked_accounts_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. 카테고리 (F-20) — 시드 데이터는 파일 맨 아래
-- ---------------------------------------------------------------------

CREATE TABLE categories (
    category_id         BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(30)  NOT NULL,
    category_type       VARCHAR(10)  NOT NULL COMMENT 'EXPENSE / TRANSFER',
    display_order       INT          NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_categories_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. 블록 (F-04, F-06, F-07, F-11)
-- ---------------------------------------------------------------------

CREATE TABLE blocks (
    block_id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    type                VARCHAR(20)  NOT NULL COMMENT 'SPENDING / GOAL / FIXED',
    name                VARCHAR(50)  NOT NULL,
    icon                VARCHAR(50)  NULL,
    balance             BIGINT       NOT NULL DEFAULT 0,
    is_default          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '기본 사용형 블록, 삭제 불가',
    target_amount       BIGINT       NULL COMMENT '목표형만',
    start_date          DATE         NOT NULL,
    due_date            DATE         NULL COMMENT '사용형=기간 끝, 목표형=기한',
    rule                JSON         NULL COMMENT 'pay_day, pay_amount, monthly_target, match_condition',
    status              VARCHAR(20)  NOT NULL DEFAULT 'SAFE' COMMENT 'SAFE / CAUTION / DANGER',
    created_by          VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'AI / USER',
    display_order       INT          NOT NULL DEFAULT 0,
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT '@Version 낙관적 잠금',
    deleted_at          DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (block_id),
    KEY idx_blocks_user_deleted (user_id, deleted_at),
    CONSTRAINT fk_blocks_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 카테고리 → 블록 연결 (F-21 반영 우선순위 2). 카테고리 하나는 사용자별 블록 하나에만
CREATE TABLE block_categories (
    block_category_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    block_id            BIGINT       NOT NULL,
    category_id         BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (block_category_id),
    UNIQUE KEY uk_block_categories_user_category (user_id, category_id),
    CONSTRAINT fk_block_categories_user     FOREIGN KEY (user_id)     REFERENCES users (user_id),
    CONSTRAINT fk_block_categories_block    FOREIGN KEY (block_id)    REFERENCES blocks (block_id),
    CONSTRAINT fk_block_categories_category FOREIGN KEY (category_id) REFERENCES categories (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE block_status_logs (
    block_status_log_id BIGINT       NOT NULL AUTO_INCREMENT,
    block_id            BIGINT       NOT NULL,
    from_status         VARCHAR(20)  NOT NULL,
    to_status           VARCHAR(20)  NOT NULL,
    reason              VARCHAR(255) NULL,
    created_at          DATETIME(6)  NOT NULL COMMENT '= changed_at',
    PRIMARY KEY (block_status_log_id),
    KEY idx_block_status_logs_block (block_id, created_at),
    CONSTRAINT fk_block_status_logs_block FOREIGN KEY (block_id) REFERENCES blocks (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- from/to NULL = 미배분. 둘 다 NULL은 서비스에서 막는다
CREATE TABLE block_transfers (
    block_transfer_id   BIGINT       NOT NULL AUTO_INCREMENT,
    from_block_id       BIGINT       NULL,
    to_block_id         BIGINT       NULL,
    amount              BIGINT       NOT NULL,
    reason              VARCHAR(20)  NOT NULL COMMENT 'MANUAL / SAVING / AI',
    created_at          DATETIME(6)  NOT NULL COMMENT '= executed_at',
    PRIMARY KEY (block_transfer_id),
    CONSTRAINT fk_block_transfers_from FOREIGN KEY (from_block_id) REFERENCES blocks (block_id),
    CONSTRAINT fk_block_transfers_to   FOREIGN KEY (to_block_id)   REFERENCES blocks (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. 거래 (F-19, F-20, F-21, F-25, F-26)
-- ---------------------------------------------------------------------

CREATE TABLE transactions (
    transaction_id      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    linked_account_id   BIGINT        NULL COMMENT '현금 빠른 입력은 NULL',
    occurred_at         DATETIME(6)   NOT NULL,
    amount              BIGINT        NOT NULL,
    direction           VARCHAR(5)    NOT NULL COMMENT 'IN / OUT',
    merchant_or_payee   VARCHAR(255)  NULL COMMENT '수취인은 마스킹(김OO)',
    balance_after       BIGINT        NULL,
    source              VARCHAR(20)   NOT NULL COMMENT 'CODEF / NOTIFICATION / MANUAL',
    status              VARCHAR(20)   NOT NULL COMMENT 'PROVISIONAL / CONFIRMED / NEEDS_REVIEW',
    category_id         BIGINT        NULL COMMENT '분류 전 NULL',
    classify_stage      TINYINT       NULL COMMENT '1~4',
    confidence          DECIMAL(4,3)  NULL,
    context_tag         VARCHAR(30)   NULL,
    classified_by       VARCHAR(10)   NULL COMMENT 'AUTO / USER / RULE',
    block_id            BIGINT        NULL COMMENT '미반영 NULL',
    excluded            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '자기 계좌 간 이동 등 소비 제외',
    is_canceled         TINYINT(1)    NOT NULL DEFAULT 0,
    external_key        VARCHAR(255)  NULL COMMENT 'TBD: CODEF 중복 판정 키',
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (transaction_id),
    UNIQUE KEY uk_transactions_external_key (external_key),
    KEY idx_transactions_user_occurred (user_id, occurred_at),
    KEY idx_transactions_user_status (user_id, status),
    CONSTRAINT fk_transactions_user     FOREIGN KEY (user_id)           REFERENCES users (user_id),
    CONSTRAINT fk_transactions_account  FOREIGN KEY (linked_account_id) REFERENCES linked_accounts (linked_account_id),
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id)       REFERENCES categories (category_id),
    CONSTRAINT fk_transactions_block    FOREIGN KEY (block_id)          REFERENCES blocks (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE receivables (
    receivable_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    transaction_id      BIGINT       NOT NULL,
    amount              BIGINT       NOT NULL,
    settled             TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (receivable_id),
    CONSTRAINT fk_receivables_user        FOREIGN KEY (user_id)        REFERENCES users (user_id),
    CONSTRAINT fk_receivables_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. 절약 목표 (F-03, F-05)
-- ---------------------------------------------------------------------

CREATE TABLE saving_goals (
    saving_goal_id      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    category_id         BIGINT       NOT NULL,
    base_amount         BIGINT       NOT NULL COMMENT '직전 3개월 평균',
    target_amount       BIGINT       NOT NULL,
    actual_amount       BIGINT       NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / CONFIRMED / ALLOCATED',
    linked_block_id     BIGINT       NULL COMMENT '달성 시 배분할 목표형 블록',
    confirmed_saving    BIGINT       NULL COMMENT '기준 - 실제 (0 미만이면 0)',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (saving_goal_id),
    KEY idx_saving_goals_user_status (user_id, status),
    CONSTRAINT fk_saving_goals_user     FOREIGN KEY (user_id)         REFERENCES users (user_id),
    CONSTRAINT fk_saving_goals_category FOREIGN KEY (category_id)     REFERENCES categories (category_id),
    CONSTRAINT fk_saving_goals_block    FOREIGN KEY (linked_block_id) REFERENCES blocks (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. 맥락 질문 (F-15)
-- ---------------------------------------------------------------------

CREATE TABLE context_rules (
    context_rule_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    `match`             JSON         NOT NULL COMMENT '가맹점+시간대 또는 수취인',
    tag                 VARCHAR(30)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (context_rule_id),
    CONSTRAINT fk_context_rules_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE context_questions (
    context_question_id BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id      BIGINT       NOT NULL,
    type                VARCHAR(30)  NOT NULL COMMENT 'LATE_NIGHT_FOOD / EASY_PAY / PERSONAL_TRANSFER / LOW_CONFIDENCE',
    answer_tag          VARCHAR(30)  NULL,
    answered_at         DATETIME(6)  NULL,
    context_rule_id     BIGINT       NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (context_question_id),
    CONSTRAINT fk_context_questions_transaction FOREIGN KEY (transaction_id)  REFERENCES transactions (transaction_id),
    CONSTRAINT fk_context_questions_rule        FOREIGN KEY (context_rule_id) REFERENCES context_rules (context_rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. 알림 (F-09, F-10, F-22, F-25)
-- ---------------------------------------------------------------------

CREATE TABLE notification_settings (
    notification_setting_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT      NOT NULL,
    type                    VARCHAR(30) NOT NULL COMMENT 'CONTEXT / DAILY_GUIDE / ANOMALY / REPORT / PRODUCT',
    enabled                 TINYINT(1)  NOT NULL DEFAULT 1,
    quiet_start             TIME        NOT NULL DEFAULT '23:00:00',
    quiet_end               TIME        NOT NULL DEFAULT '07:00:00',
    daily_max               TINYINT     NOT NULL DEFAULT 5,
    lock_screen_hide        TINYINT(1)  NOT NULL DEFAULT 0,
    guide_time              TIME        NOT NULL DEFAULT '08:00:00',
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_setting_id),
    UNIQUE KEY uk_notification_settings_user_type (user_id, type),
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_logs (
    notification_log_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    type                VARCHAR(30)  NOT NULL,
    sent_at             DATETIME(6)  NOT NULL,
    delivered           TINYINT(1)   NOT NULL DEFAULT 0,
    push_token          VARCHAR(255) NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_log_id),
    KEY idx_notification_logs_user_sent (user_id, sent_at),
    CONSTRAINT fk_notification_logs_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_tokens (
    push_token_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    token               VARCHAR(255) NOT NULL COMMENT 'Expo push token',
    device_id           VARCHAR(100) NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (push_token_id),
    UNIQUE KEY uk_push_tokens_token (token),
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_app_whitelist (
    notification_app_whitelist_id BIGINT       NOT NULL AUTO_INCREMENT,
    package_name                  VARCHAR(150) NOT NULL,
    institution                   VARCHAR(50)  NOT NULL,
    parser_version                VARCHAR(20)  NOT NULL,
    created_at                    DATETIME(6)  NOT NULL,
    updated_at                    DATETIME(6)  NOT NULL,
    PRIMARY KEY (notification_app_whitelist_id),
    UNIQUE KEY uk_whitelist_package (package_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- TBD: 절약 제안 저장이 필요하면 주석 해제
-- ---------------------------------------------------------------------
-- CREATE TABLE saving_suggestions (
--     saving_suggestion_id   BIGINT       NOT NULL AUTO_INCREMENT,
--     user_id                BIGINT       NOT NULL,
--     category_id            BIGINT       NOT NULL,
--     base_month             CHAR(7)      NOT NULL,
--     action_text            VARCHAR(255) NOT NULL,
--     expected_saving_amount BIGINT       NOT NULL,
--     status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / GOAL_SET / DISMISSED',
--     created_at             DATETIME(6)  NOT NULL,
--     updated_at             DATETIME(6)  NOT NULL,
--     PRIMARY KEY (saving_suggestion_id),
--     CONSTRAINT fk_saving_suggestions_user     FOREIGN KEY (user_id)     REFERENCES users (user_id),
--     CONSTRAINT fk_saving_suggestions_category FOREIGN KEY (category_id) REFERENCES categories (category_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 카테고리 시드 (F-20 초기 12개 + 이체 유형 4개)
-- ---------------------------------------------------------------------
INSERT INTO categories (code, name, category_type, display_order, created_at, updated_at) VALUES
    ('FOOD',            '식비',              'EXPENSE',   1, NOW(6), NOW(6)),
    ('DELIVERY_DINING', '배달/외식',         'EXPENSE',   2, NOW(6), NOW(6)),
    ('CAFE',            '카페/간식',         'EXPENSE',   3, NOW(6), NOW(6)),
    ('TRANSPORT',       '교통',              'EXPENSE',   4, NOW(6), NOW(6)),
    ('HOUSING',         '주거/관리비',       'EXPENSE',   5, NOW(6), NOW(6)),
    ('TELECOM',         '통신',              'EXPENSE',   6, NOW(6), NOW(6)),
    ('LIVING',          '생활/생필품',       'EXPENSE',   7, NOW(6), NOW(6)),
    ('SHOPPING',        '쇼핑/의류',         'EXPENSE',   8, NOW(6), NOW(6)),
    ('LEISURE',         '문화/여가',         'EXPENSE',   9, NOW(6), NOW(6)),
    ('HEALTH',          '의료/건강',         'EXPENSE',  10, NOW(6), NOW(6)),
    ('SUBSCRIPTION',    '구독료',            'EXPENSE',  11, NOW(6), NOW(6)),
    ('ETC',             '기타',              'EXPENSE',  12, NOW(6), NOW(6)),
    ('DUTCH_PAY',       '더치페이',          'TRANSFER', 13, NOW(6), NOW(6)),
    ('GIFT',            '선물/경조사',       'TRANSFER', 14, NOW(6), NOW(6)),
    ('LOAN_OUT',        '빌려준 돈',         'TRANSFER', 15, NOW(6), NOW(6)),
    ('SELF_TRANSFER',   '자기 계좌 간 이동', 'TRANSFER', 16, NOW(6), NOW(6));
