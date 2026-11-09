package com.dothis.fintris.transaction.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockRule.AmountMode;
import com.dothis.fintris.block.entity.BlockRule.MatchCondition;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// 출금 하나를 어느 블록의 남은 돈에서 뺄지 고른다 (기능명세서 v1 F-21 반영 우선순위)
// 1. 고정지출형 블록의 납부 확인 조건에 맞는 출금 → 그 블록
// 2. 카테고리에 연결된 블록 → 그 블록
// 3. 그 밖의 출금 → 기본 사용형 블록
@Component
public class BlockTargetResolver {

    static final int DEFAULT_DAY_WINDOW = 3;

    // blocks: 사용자의 삭제되지 않은 블록(표시 순서대로), categoryBlock: 거래 카테고리에 연결된 블록(없으면 null)
    public Optional<Block> resolve(Transaction transaction, List<Block> blocks, Block categoryBlock) {
        for (Block block : blocks) {
            if (block.getType() == BlockType.FIXED && matchesFixed(block, transaction)) {
                return Optional.of(block);
            }
        }
        if (categoryBlock != null && categoryBlock.getDeletedAt() == null) {
            return Optional.of(categoryBlock);
        }
        return blocks.stream().filter(Block::isDefault).findFirst();
    }

    boolean matchesFixed(Block fixed, Transaction transaction) {
        BlockRule rule = fixed.getRule();
        MatchCondition condition = rule == null ? null : rule.matchCondition();
        if (condition == null || condition.payee() == null || condition.payee().isBlank()) {
            return false;
        }
        if (!payeeMatches(condition.payee(), transaction.getMerchantOrPayee())) {
            return false;
        }
        if (condition.linkedAccountId() != null && (transaction.getLinkedAccount() == null
                || !condition.linkedAccountId().equals(transaction.getLinkedAccount().getId()))) {
            return false;
        }
        return amountMatches(rule, condition, transaction.getAmount())
                && dayMatches(rule.payDay(), condition.dayWindow(), transaction.getOccurredAt().toLocalDate());
    }

    // 받는 분은 마스킹(김*수)되어 있을 수 있어 *를 아무 글자로 본다. 띄어쓰기는 무시한다
    static boolean payeeMatches(String payee, String merchantOrPayee) {
        if (merchantOrPayee == null) {
            return false;
        }
        String[] parts = payee.replaceAll("\\s+", "").split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.matches(regex.toString(), merchantOrPayee.replaceAll("\\s+", ""));
    }

    // EXACT는 납부액(없으면 최소 금액)과 같아야 하고, RANGE는 최소~최대 안이어야 한다. 기준 금액이 없으면 맞지 않는다고 본다
    private boolean amountMatches(BlockRule rule, MatchCondition condition, long amount) {
        if (condition.amountMode() == AmountMode.RANGE) {
            Long min = condition.minAmount() != null ? condition.minAmount() : rule.payAmount();
            Long max = condition.maxAmount() != null ? condition.maxAmount() : rule.payAmount();
            return min != null && max != null && min <= amount && amount <= max;
        }
        Long exact = rule.payAmount() != null ? rule.payAmount() : condition.minAmount();
        return exact != null && exact == amount;
    }

    // 납부일이 그 달에 없으면 말일로 본다. 앞뒤 dayWindow일(기본 3일) 안이면 맞다
    private boolean dayMatches(Integer payDay, Integer dayWindow, LocalDate date) {
        if (payDay == null) {
            return true;
        }
        int effective = Math.min(payDay, date.lengthOfMonth());
        int window = dayWindow != null ? dayWindow : DEFAULT_DAY_WINDOW;
        int gap = Math.abs(date.getDayOfMonth() - effective);
        // 월말·월초에 걸치면(30일 ↔ 1일) 가까운 쪽으로 잰다
        return Math.min(gap, 31 - gap) <= window;
    }
}
