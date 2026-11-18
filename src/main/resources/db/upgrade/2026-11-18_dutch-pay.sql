-- =====================================================================
-- 더치페이(N-06)를 위한 증분 SQL (MySQL 8.x)
-- schema.sql은 빈 DB용이라 이미 만들어진 DB에는 이 파일을 한 번 실행한다.
-- JPA_DDL_AUTO=validate로 띄우는 DB는 배포 전에 먼저 실행해야 앱이 시작된다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-18_dutch-pay.sql
-- =====================================================================

SET NAMES utf8mb4;

-- 더치페이 (N-06). 거래는 그대로 두고, 묶인 결제·받은 돈은 여기서만 연결한다
CREATE TABLE dutch_pays (
    dutch_pay_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    block_id            BIGINT       NULL COMMENT '받은 돈을 돌려놓은 블록. 가장 큰 결제의 블록',
    returned_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '그 블록에 더한 금액(받은 돈, 결제 합까지). 수정·해제 때 이만큼 되돌린다',
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT '@Version 낙관적 잠금',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (dutch_pay_id),
    KEY idx_dutch_pays_user (user_id),
    CONSTRAINT fk_dutch_pays_user  FOREIGN KEY (user_id)  REFERENCES users (user_id),
    CONSTRAINT fk_dutch_pays_block FOREIGN KEY (block_id) REFERENCES blocks (block_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dutch_pay_transactions (
    dutch_pay_transaction_id BIGINT      NOT NULL AUTO_INCREMENT,
    dutch_pay_id             BIGINT      NOT NULL,
    transaction_id           BIGINT      NOT NULL COMMENT '결제(OUT) 또는 받은 돈(IN). 거래 하나는 더치페이 하나에만',
    my_share                 BIGINT      NULL COMMENT '결제 건의 내 몫(받은 돈을 결제 금액 비율로 뺀 값). 받은 돈 건은 NULL',
    created_at               DATETIME(6) NOT NULL,
    PRIMARY KEY (dutch_pay_transaction_id),
    UNIQUE KEY uk_dutch_pay_transactions_transaction (transaction_id),
    KEY idx_dutch_pay_transactions_dutch_pay (dutch_pay_id),
    CONSTRAINT fk_dutch_pay_transactions_dutch_pay   FOREIGN KEY (dutch_pay_id)   REFERENCES dutch_pays (dutch_pay_id),
    CONSTRAINT fk_dutch_pay_transactions_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
