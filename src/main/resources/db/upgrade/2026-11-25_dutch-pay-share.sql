-- =====================================================================
-- 더치페이 내 몫 직접 수정·남은 돈을 나눌 돈으로 (N-06)를 위한 증분 SQL (MySQL 8.x)
-- 2026-11-18_dutch-pay.sql 다음에 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-25_dutch-pay-share.sql
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE dutch_pays
    ADD COLUMN my_share        BIGINT NULL COMMENT '사용자가 정한 내 몫 합. NULL이면 결제 합 - 받은 돈(0 밑으로는 안 감)' AFTER user_id,
    ADD COLUMN waiting_fund_id BIGINT NULL COMMENT '받은 돈 중 블록에 돌려놓고 남은 돈을 넣은 나눌 돈' AFTER my_share,
    ADD COLUMN buffer_amount   BIGINT NULL COMMENT '그 나눌 돈에 넣은 금액. 수정·해제 때 이만큼 되돌린다' AFTER waiting_fund_id,
    ADD CONSTRAINT fk_dutch_pays_waiting_fund FOREIGN KEY (waiting_fund_id) REFERENCES waiting_funds (waiting_fund_id);
