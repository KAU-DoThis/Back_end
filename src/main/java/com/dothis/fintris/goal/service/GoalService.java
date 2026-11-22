package com.dothis.fintris.goal.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.code.TransferErrorCode;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.service.BlockCalculator;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.block.service.BlockStatusJudge.Judgement;
import com.dothis.fintris.block.service.BlockStatusService;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.goal.code.GoalErrorCode;
import com.dothis.fintris.goal.dto.GoalListResponse;
import com.dothis.fintris.goal.dto.GoalResolutionRequest;
import com.dothis.fintris.goal.dto.GoalResolutionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 금융 목표 (API 명세 v4 7장, 기능명세서 F-07). 목표는 따로 저장하지 않고 목표형 블록이 가진다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final BlockCategoryRepository blockCategoryRepository;
    private final BlockStatusService blockStatusService;
    private final BlockService blockService;

    public GoalListResponse getGoals(Long userId) {
        LocalDate today = LocalDate.now(KST);
        List<Block> blocks = blockRepository
                .findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(userId, BlockType.GOAL);
        Map<Long, Judgement> statuses = blockStatusService.judgeAll(blocks);
        return new GoalListResponse(blocks.stream()
                .map(block -> toGoal(block, statuses.get(block.getId()), today))
                .toList());
    }

    private GoalListResponse.Goal toGoal(Block block, Judgement status, LocalDate today) {
        long target = block.getTargetAmount() != null ? block.getTargetAmount() : 0;
        // 최근 3개월 평균 확보액 = 지난 달들의 (들어온 돈 - 나간 돈) ÷ 개월 수. 이력이 없으면 월 확보 금액
        int months = GoalCalculator.paceMonths(block.getStartDate(), today);
        long secured = 0;
        if (months > 0) {
            LocalDateTime thisMonth = today.withDayOfMonth(1).atStartOfDay();
            secured = securedSince(block.getId(), thisMonth.minusMonths(months))
                    - securedSince(block.getId(), thisMonth);
        }
        Long paceMonthly = GoalCalculator.paceMonthly(secured, months, monthlyTarget(block, today));
        LocalDate paceDate = GoalCalculator.paceDate(target, block.getBalance(), paceMonthly, today);

        return new GoalListResponse.Goal(
                block.getId(),
                block.getName(),
                block.getBalance(),
                target,
                block.getDueDate(),
                GoalCalculator.achievementRate(block.getBalance(), target),
                paceDate,
                GoalCalculator.delayDays(block.getDueDate(), paceDate),
                status.status(),
                status.status().getLabel(),
                status.subLabel(),
                block.getVersion());
    }

    private long securedSince(Long blockId, LocalDateTime from) {
        return blockTransferRepository.sumInSince(blockId, from) - blockTransferRepository.sumOutSince(blockId, from);
    }

    private Long monthlyTarget(Block block, LocalDate today) {
        BlockRule rule = block.getRule();
        if (rule != null && rule.monthlyTarget() != null) {
            return rule.monthlyTarget();
        }
        if (block.getTargetAmount() == null) {
            return null;
        }
        return recommended(block.getTargetAmount(), block.getBalance(), today, block.getDueDate());
    }

    // 달성(17)·기한 경과(17-1) 처리. 상태에 맞지 않는 처리는 막는다
    // 돈을 옮기는 처리(MOVE·CLEANUP)는 블록 이동처럼 한 트랜잭션에서 하고, 동시 수정은 @Version으로 막는다
    @Transactional
    public GoalResolutionResponse resolve(Long userId, Long blockId, GoalResolutionRequest request) {
        LocalDate today = LocalDate.now(KST);
        Block block = findOwnedBlock(userId, blockId);
        if (block.getType() != BlockType.GOAL) {
            throw GeneralException.of(GoalErrorCode.GOAL_NOT_GOAL_BLOCK);
        }
        boolean achieved = block.getTargetAmount() != null && block.getBalance() >= block.getTargetAmount();
        boolean expired = !achieved && block.getDueDate() != null && block.getDueDate().isBefore(today);
        BlockRule rule = block.getRule() != null ? block.getRule() : BlockRule.empty();

        List<Block> moved = List.of();
        switch (request.action()) {
            case KEEP -> {
                require(achieved);
                // 다 모았으니 매달 넣는 건 멈춘다 (월 확보 금액 0)
                block.update(null, null, null, null, null, null, rule.withMonthlyTarget(0L));
            }
            case RAISE_TARGET -> {
                require(achieved);
                Long newTarget = request.newTargetAmount();
                if (newTarget == null || newTarget <= block.getTargetAmount()) {
                    throw GeneralException.of(GoalErrorCode.GOAL_INVALID_RESOLUTION);
                }
                LocalDate dueDate = request.newDueDate() != null
                        ? validDueDate(request.newDueDate(), block, today)
                        : block.getDueDate();
                block.update(null, null, null, newTarget, null, request.newDueDate(),
                        rule.withMonthlyTarget(recommended(newTarget, block.getBalance(), today, dueDate)));
            }
            case MOVE -> {
                require(achieved);
                if (request.moveTo() == null || request.moveTo().isEmpty()) {
                    throw GeneralException.of(GoalErrorCode.GOAL_INVALID_RESOLUTION);
                }
                moved = moveOut(userId, block, request.moveTo());
            }
            case EXTEND -> {
                require(expired);
                if (request.newDueDate() == null) {
                    throw GeneralException.of(GoalErrorCode.GOAL_INVALID_RESOLUTION);
                }
                LocalDate dueDate = validDueDate(request.newDueDate(), block, today);
                // 늘린 기한 기준으로 이번 달부터 더 확보할 금액을 다시 계산한다
                block.update(null, null, null, null, null, dueDate, rule.withMonthlyTarget(
                        recommended(block.getTargetAmount(), block.getBalance(), today, dueDate)));
            }
            case FINISH_AT_CURRENT -> {
                require(expired);
                if (block.getBalance() <= 0) {
                    throw GeneralException.of(GoalErrorCode.GOAL_NOTHING_SAVED);
                }
                // 목표를 모은 금액으로 바꿔 달성 처리한다
                block.update(null, null, null, block.getBalance(), null, null, rule.withMonthlyTarget(0L));
            }
            case CLEANUP -> {
                require(expired);
                if (request.moveTo() != null && !request.moveTo().isEmpty()) {
                    moved = moveOut(userId, block, request.moveTo());
                }
                // 옮기고 남은 돈은 블록 삭제처럼 "남은 돈" 나눌 돈으로 돌려보낸다
                blockService.deleteBlock(userId, blockId);
            }
            case REMIND_LATER -> {
                require(achieved || expired);
                // TODO: 3일 뒤 다시 알림은 알림(F-22)이 붙으면 예약한다. 지금은 아무것도 바꾸지 않는다
            }
        }
        // 응답에 올라간 version을 담으려면 먼저 저장해야 한다
        blockRepository.flush();
        return response(block, moved);
    }

    private GoalResolutionResponse response(Block block, List<Block> moved) {
        boolean deleted = block.getDeletedAt() != null;
        List<Block> judged = new ArrayList<>(moved);
        if (!deleted) {
            judged.add(block);
        }
        Map<Long, Judgement> statuses = blockStatusService.judgeAll(judged);
        BlockResponse blockResponse = deleted ? null : BlockResponse.of(block,
                blockCategoryRepository.findAllByBlock_Id(block.getId()), statuses.get(block.getId()));
        List<TransferResponse.BlockBalance> movedBlocks = moved.stream()
                .map(target -> TransferResponse.BlockBalance.from(target, statuses.get(target.getId()).status()))
                .toList();
        return new GoalResolutionResponse(blockResponse, movedBlocks);
    }

    private void require(boolean allowed) {
        if (!allowed) {
            throw GeneralException.of(GoalErrorCode.GOAL_ACTION_NOT_ALLOWED);
        }
    }

    // 새 기한은 오늘 이후이고 시작일 이후여야 한다
    private LocalDate validDueDate(LocalDate dueDate, Block block, LocalDate today) {
        if (dueDate.isBefore(today) || dueDate.isBefore(block.getStartDate())) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DUE_DATE);
        }
        return dueDate;
    }

    // 기한이 없거나 지났으면 권장값을 낼 수 없다
    private Long recommended(long target, long balance, LocalDate today, LocalDate dueDate) {
        if (dueDate == null || dueDate.isBefore(today)) {
            return null;
        }
        return BlockCalculator.recommendedMonthlyTarget(target, balance, today, dueDate);
    }

    // 목표 블록에서 다른 내 블록으로 옮긴다. 같은 블록에 여러 번 보내면 합쳐서 한 번에 옮긴다
    private List<Block> moveOut(Long userId, Block from, List<GoalResolutionRequest.Move> moves) {
        Map<Long, Long> amounts = new LinkedHashMap<>();
        for (GoalResolutionRequest.Move move : moves) {
            if (move.blockId().equals(from.getId())) {
                throw GeneralException.of(TransferErrorCode.TRANSFER_SAME_BLOCK);
            }
            amounts.merge(move.blockId(), move.amount(), Math::addExact);
        }
        long total = amounts.values().stream().mapToLong(Long::longValue).sum();
        if (total > from.getBalance()) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INSUFFICIENT_AMOUNT);
        }

        List<Block> targets = new ArrayList<>();
        amounts.forEach((targetId, amount) -> {
            Block to = findOwnedBlock(userId, targetId);
            from.withdraw(amount);
            to.deposit(amount);
            blockTransferRepository.save(BlockTransfer.builder()
                    .fromBlock(from)
                    .toBlock(to)
                    .amount(amount)
                    .reason(TransferReason.MANUAL)
                    .build());
            targets.add(to);
        });
        return targets;
    }

    // 없으면 404, 남의 블록이면 403
    private Block findOwnedBlock(Long userId, Long blockId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));
        if (!block.isOwnedBy(userId)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_FORBIDDEN);
        }
        return block;
    }
}
