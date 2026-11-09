-- =====================================================================
-- 거래의 블록 반영(F-21, #51)을 위한 증분 SQL (MySQL 8.x)
-- schema.sql은 빈 DB용이라 이미 만들어진 DB에는 이 파일을 한 번 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
-- update 모드 DB는 컬럼을 앱이 만들고, 3번(확정 시각 채우기)도 앱이 뜰 때
-- BlocksConfirmedAtBackfill이 하므로 따로 실행하지 않아도 된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-10_block-reflection.sql
-- 이미 적용한 부분이 있으면 해당 문장에서 "Duplicate column"으로 멈추므로 그 아래부터 실행한다.
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 반영 블록에서 실제로 뺀 금액. 블록이 0에서 멈추면 거래 금액보다 작고, 되돌릴 때 이만큼만 돌려준다
ALTER TABLE transactions
    ADD COLUMN block_amount BIGINT NULL COMMENT '반영 블록에서 실제로 뺀 금액' AFTER block_id;

-- 2. 초기 블록 확정 시각. 이후에 일어난 거래만 블록에 반영한다 (연동 때 가져온 3개월치 제외)
ALTER TABLE user_settings
    ADD COLUMN blocks_confirmed_at DATETIME(6) NULL COMMENT '처음 초기 블록을 확정한 시각' AFTER blocks_confirmed;

-- 3. 이 컬럼이 생기기 전에 확정한 사용자는 시각이 비어 있어 거래가 반영되지 않는다.
--    확정이 온보딩의 마지막 단계라 마지막 수정 시각(updated_at)으로 채운다
UPDATE user_settings SET blocks_confirmed_at = updated_at
WHERE blocks_confirmed = 1 AND blocks_confirmed_at IS NULL;
