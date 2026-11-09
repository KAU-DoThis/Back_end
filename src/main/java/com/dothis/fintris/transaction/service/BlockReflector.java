package com.dothis.fintris.transaction.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockCategory;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// 거래를 블록의 남은 돈(balance)에 반영하고 되돌린다 (기능명세서 v1 F-21). 블록 크기(capacity)는 바꾸지 않는다
// 호출하는 쪽의 트랜잭션 안에서 쓴다. 블록 잔액 충돌은 @Version으로 막고 재시도는 호출하는 쪽이 한다
// TODO: 블록에서 모자란 금액(거래 금액 − blockAmount)은 나눌 돈에서 뺀다 — 빼는 순서를 블록 담당과 정한 뒤 붙인다
@Component
@RequiredArgsConstructor
public class BlockReflector {

    private final BlockRepository blockRepository;
    private final BlockCategoryRepository blockCategoryRepository;
    private final UserSettingRepository userSettingRepository;
    private final BlockTargetResolver blockTargetResolver;

    // 블록에 반영해야 하는 거래인지: 출금, 소비 제외·취소가 아님, 초기 블록 확정 뒤에 일어남
    public boolean shouldReflect(Transaction transaction, LocalDateTime blocksConfirmedAt) {
        return blocksConfirmedAt != null
                && transaction.getDirection() == TransactionDirection.OUT
                && !transaction.isExcluded() && !transaction.isCanceled()
                && !transaction.getOccurredAt().isBefore(blocksConfirmedAt);
    }

    public Optional<LocalDateTime> blocksConfirmedAt(Long userId) {
        return userSettingRepository.findByUser_Id(userId).map(UserSetting::getBlocksConfirmedAt);
    }

    // 동기화 뒤 다시 맞출 이유가 있는 거래만 고른다. 이미 맞게 반영된 거래를 다시 반영하면
    // 그 사이 블록에 돈이 더 들어왔을 때 지난 거래가 0에서 멈춰 덜 뺀 금액까지 뒤늦게 가져가기 때문이다
    public boolean needsReconcile(Transaction transaction, LocalDateTime blocksConfirmedAt) {
        boolean reflected = transaction.getBlock() != null;
        boolean target = shouldReflect(transaction, blocksConfirmedAt);
        boolean amountShrank = reflected && transaction.getBlockAmount() != null
                && transaction.getBlockAmount() > transaction.getAmount();
        return reflected != target || amountShrank;
    }

    // 지금 상태에 맞게 다시 맞춘다: 반영돼 있으면 되돌리고, 반영 대상이면 다시 반영한다
    // (카테고리 수정·취소·부분취소·소비 제외 뒤에 부른다). 반영 블록이나 뺀 금액이 바뀌었으면 true
    public boolean reconcile(Transaction transaction, LocalDateTime blocksConfirmedAt) {
        Block before = transaction.getBlock();
        Long beforeAmount = transaction.getBlockAmount();
        release(transaction);
        if (shouldReflect(transaction, blocksConfirmedAt)) {
            reflect(transaction);
        }
        return before != transaction.getBlock() || !Objects.equals(beforeAmount, transaction.getBlockAmount());
    }

    // 남은 돈은 0에서 멈춘다. 고를 블록이 없으면(기본 블록도 없음) 반영하지 않는다
    boolean reflect(Transaction transaction) {
        Long userId = transaction.getUser().getId();
        List<Block> blocks = blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);
        Optional<Block> target = blockTargetResolver.resolve(transaction, blocks, categoryBlock(userId, transaction));
        if (target.isEmpty()) {
            return false;
        }
        Block block = target.get();
        long deducted = Math.min(block.getBalance(), transaction.getAmount());
        block.withdraw(deducted);
        transaction.reflectTo(block, deducted);
        return true;
    }

    // 반영할 때 실제로 뺀 금액만 돌려준다. 삭제된 블록은 남은 돈을 이미 나눌 돈으로 돌려보냈으니 넣지 않는다
    void release(Transaction transaction) {
        Block block = transaction.getBlock();
        if (block == null) {
            return;
        }
        if (block.getDeletedAt() == null && transaction.getBlockAmount() != null) {
            block.deposit(transaction.getBlockAmount());
        }
        transaction.releaseBlock();
    }

    private Block categoryBlock(Long userId, Transaction transaction) {
        if (transaction.getCategory() == null) {
            return null;
        }
        return blockCategoryRepository.findAllByUser_IdAndCategory_IdIn(userId, List.of(transaction.getCategory().getId()))
                .stream().map(BlockCategory::getBlock).findFirst().orElse(null);
    }
}
