-- =====================================================================
-- 초불이의 방 이동 제안 (F-16) 증분 SQL (MySQL 8.x)
-- 2026-11-08_api-v4.sql 다음에 한 번 실행한다.
--
-- 실행: mysql -u <user> -p <database> < 2026-11-09_coach-proposals.sql
-- =====================================================================

SET NAMES utf8mb4;

-- 되돌리기(UNDO) 이동 사유 추가
ALTER TABLE block_transfers
    MODIFY COLUMN reason VARCHAR(20) NOT NULL COMMENT 'MANUAL / SAVING / AI / ALLOCATION / SUGGESTION / UNDO';

CREATE TABLE coach_proposals (
    coach_proposal_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    from_block_id       BIGINT       NOT NULL,
    to_block_id         BIGINT       NOT NULL,
    amount              BIGINT       NOT NULL,
    preview             JSON         NOT NULL COMMENT '제안 카드에 보여 준 숫자 (수락 때 블록 version 비교)',
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED / UNDONE (만료는 expires_at으로 계산)',
    transfer_id         BIGINT       NULL COMMENT '수락해서 옮긴 이동',
    result              JSON         NULL COMMENT '옮긴 뒤 잔액',
    undo_transfer_id    BIGINT       NULL COMMENT '되돌린 이동',
    undo_result         JSON         NULL COMMENT '되돌린 뒤 잔액',
    expires_at          DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (coach_proposal_id),
    KEY idx_coach_proposals_user (user_id, created_at),
    CONSTRAINT fk_coach_proposals_user          FOREIGN KEY (user_id)          REFERENCES users (user_id),
    CONSTRAINT fk_coach_proposals_from_block    FOREIGN KEY (from_block_id)    REFERENCES blocks (block_id),
    CONSTRAINT fk_coach_proposals_to_block      FOREIGN KEY (to_block_id)      REFERENCES blocks (block_id),
    CONSTRAINT fk_coach_proposals_transfer      FOREIGN KEY (transfer_id)      REFERENCES block_transfers (block_transfer_id),
    CONSTRAINT fk_coach_proposals_undo_transfer FOREIGN KEY (undo_transfer_id) REFERENCES block_transfers (block_transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
