-- =====================================================================
-- 기존 DB를 API 명세 v4 스키마로 올리는 증분 SQL (MySQL 8.x)
-- schema.sql은 빈 DB용이라 이미 만들어진 DB에는 이 파일을 한 번 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
-- (update 모드는 컬럼·테이블은 만들어 주지만 옛 컬럼 삭제와 데이터 정리는 하지 않는다)
--
-- 실행: mysql -u <user> -p <database> < 2026-11-08_api-v4.sql
-- 이미 적용한 부분이 있으면 해당 문장에서 "Duplicate column" 등으로 멈추므로 그 아래부터 실행한다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 동의·온보딩 v4 (#14)
-- ---------------------------------------------------------------------

-- v4에서 없어진 동의 항목 (개발 데이터만 해당)
DELETE FROM consents WHERE item NOT IN ('TERMS', 'PRIVACY', 'CODEF', 'AGE_14', 'MARKETING');

-- link_skipped(체험 모드) 삭제, 온보딩 단계는 tutorial_done·blocks_confirmed로 계산
-- 옛 onboarding_done은 의미가 달라 옮기지 않는다
ALTER TABLE user_settings
    ADD COLUMN tutorial_done    TINYINT(1) NOT NULL DEFAULT 0 AFTER user_id,
    ADD COLUMN blocks_confirmed TINYINT(1) NOT NULL DEFAULT 0 AFTER tutorial_done,
    DROP COLUMN onboarding_done,
    DROP COLUMN link_skipped;

-- ---------------------------------------------------------------------
-- 2. 블록 색상 (#15)
-- ---------------------------------------------------------------------

-- 기존 블록은 NULL로 두고 응답에서 유형별 기본색으로 보여 준다
ALTER TABLE blocks
    ADD COLUMN color VARCHAR(1) NULL COMMENT 'I / O / T / S / Z / J / L. NULL이면 유형별 기본색' AFTER icon;

-- ---------------------------------------------------------------------
-- 3. 나눌 돈(대기 자금) (#16)
-- ---------------------------------------------------------------------

CREATE TABLE waiting_funds (
    waiting_fund_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    source                VARCHAR(20)  NOT NULL COMMENT 'SAVING / REGULAR_INCOME / EXTRA_INCOME / OTHER',
    label                 VARCHAR(50)  NOT NULL COMMENT '홈 카드 제목 (절약분, 정기 입금 · 월급 등)',
    description           VARCHAR(100) NULL COMMENT '9월 절약 확정, 10월 12일 입금 등',
    amount                BIGINT       NOT NULL COMMENT '남은 금액. 0이면 목록에서 빠짐',
    original_amount       BIGINT       NOT NULL,
    source_transaction_id BIGINT       NULL COMMENT '입금 거래 (FK는 거래 기능이 붙을 때 추가)',
    saving_goal_id        BIGINT       NULL COMMENT '절약 목표 확정 (FK는 절약 목표 기능이 붙을 때 추가)',
    income_source_id      BIGINT       NULL COMMENT '입금원 (income_sources 테이블 추가 후 FK)',
    breakdown             JSON         NULL COMMENT '절약분 카테고리별 내역 [{label, amount}]',
    cleared_at            DATETIME(6)  NULL COMMENT '다 나눈 시각',
    version               BIGINT       NOT NULL DEFAULT 0 COMMENT '@Version 낙관적 잠금',
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (waiting_fund_id),
    KEY idx_waiting_funds_user_amount (user_id, amount),
    CONSTRAINT fk_waiting_funds_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE block_transfers
    ADD COLUMN from_waiting_fund_id BIGINT NULL COMMENT '나눌 돈에서 배분' AFTER to_block_id,
    ADD COLUMN to_waiting_fund_id   BIGINT NULL COMMENT '블록 삭제 시 나눌 돈으로 반환' AFTER from_waiting_fund_id,
    MODIFY COLUMN reason VARCHAR(20) NOT NULL COMMENT 'MANUAL / SAVING / AI / ALLOCATION / SUGGESTION',
    ADD CONSTRAINT fk_block_transfers_from_fund FOREIGN KEY (from_waiting_fund_id) REFERENCES waiting_funds (waiting_fund_id),
    ADD CONSTRAINT fk_block_transfers_to_fund   FOREIGN KEY (to_waiting_fund_id)   REFERENCES waiting_funds (waiting_fund_id);
