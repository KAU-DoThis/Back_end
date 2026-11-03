-- =====================================================================
-- 로컬 테스트용 데이터 (Figma 01 홈 화면과 같은 숫자)
-- schema.sql을 실행한 DB에서 한 번만 실행한다. 다시 실행하면 이메일 중복으로 실패한다
-- 실제 CODEF 데이터가 아니다. connected_id는 가짜 값이다
--
-- 기대 결과 (GET /api/v1/home)
--  - totalAssets 4,577,000 = 국민 3,000,000 + 카카오 1,577,000
--  - 블록 합 4,370,000 → unallocatedAmount 207,000
--  - 생활비(기본 블록) 950,000, 기간 10/1 ~ 10/31
--    10/14 기준 남은 18일 → todayAvailable 52,700 (실행한 날짜에 따라 달라진다)
-- =====================================================================

INSERT INTO users (email, auth_provider, created_at, updated_at)
VALUES ('demo@fintris.com', 'EMAIL', NOW(6), NOW(6));
SET @uid = LAST_INSERT_ID();

INSERT INTO linked_accounts
    (user_id, institution_code, type, connected_id, account_name, account_number_last4,
     balance, balance_updated_at, is_active, status, last_synced_at, created_at, updated_at)
VALUES
    (@uid, '004', 'BANK', 'LOCAL-SEED-NOT-REAL', 'KB국민 입출금', '1234',
     3000000, '2026-10-14 09:00:00', 1, 'ACTIVE', '2026-10-14 09:00:00', NOW(6), NOW(6)),
    (@uid, '090', 'BANK', 'LOCAL-SEED-NOT-REAL', '카카오뱅크 입출금', '5678',
     1577000, '2026-10-14 09:00:00', 1, 'ACTIVE', '2026-10-14 09:00:00', NOW(6), NOW(6));

INSERT INTO blocks
    (user_id, type, name, balance, is_default, target_amount, start_date, due_date, rule,
     status, created_by, display_order, version, created_at, updated_at)
VALUES
    (@uid, 'GOAL',     '저축',   2000000, 0, 3000000, '2026-08-01', '2027-08-31', NULL,
     'SAFE',    'USER', 0, 0, NOW(6), NOW(6)),
    (@uid, 'SPENDING', '생활비',  950000, 1, NULL,    '2026-10-01', '2026-10-31', NULL,
     'CAUTION', 'AI',   1, 0, NOW(6), NOW(6)),
    (@uid, 'GOAL',     '여행',    320000, 0, 800000,  '2026-08-01', '2026-12-31', '{"monthly_target": 200000}',
     'CAUTION', 'USER', 2, 0, NOW(6), NOW(6)),
    (@uid, 'FIXED',    '월세',    600000, 0, NULL,    '2026-10-01', NULL,         '{"pay_day": 25, "pay_amount": 600000}',
     'SAFE',    'AI',   3, 0, NOW(6), NOW(6)),
    (@uid, 'GOAL',     '비상금',  500000, 0, 500000,  '2026-06-01', '2027-06-30', NULL,
     'SAFE',    'USER', 4, 0, NOW(6), NOW(6));

SELECT @uid AS demo_user_id;
