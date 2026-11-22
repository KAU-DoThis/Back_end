package com.dothis.fintris.saving.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.saving.code.SavingGoalErrorCode;
import com.dothis.fintris.saving.dto.SavingGoalCreateRequest;
import com.dothis.fintris.saving.dto.SavingGoalListResponse;
import com.dothis.fintris.saving.dto.SavingGoalResponse;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 절약 목표 생성·조회·취소 (API 명세 v4 8장, 기능명세서 F-03). 확정은 SavingGoalConfirmService
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavingGoalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<SavingGoalStatus> LISTED =
            EnumSet.of(SavingGoalStatus.ACTIVE, SavingGoalStatus.CONFIRMED);

    private final SavingGoalRepository savingGoalRepository;
    private final CategoryRepository categoryRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WaitingFundRepository waitingFundRepository;

    @Transactional
    public SavingGoalResponse create(Long userId, SavingGoalCreateRequest request) {
        LocalDate today = LocalDate.now(KST);
        if (request.targetAmount() < 0) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_INVALID_AMOUNT);
        }
        LocalDate startDate = request.startDate() != null ? request.startDate() : today;
        LocalDate endDate = request.endDate() != null ? request.endDate()
                : SavingGoalCalculator.defaultEndDate(startDate);
        // 이미 끝난 기간이나 거꾸로 된 기간은 저장하지 않는다
        if (endDate.isBefore(startDate) || endDate.isBefore(today)) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_INVALID_PERIOD);
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_CATEGORY_NOT_FOUND));
        if (savingGoalRepository.existsByUser_IdAndCategory_IdAndStatus(userId, category.getId(),
                SavingGoalStatus.ACTIVE)) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_DUPLICATE_CATEGORY);
        }
        Block linkedBlock = request.linkedBlockId() == null ? null : linkedBlock(userId, request.linkedBlockId());

        long baseAmount = SavingGoalCalculator.baseAmount(spent(userId, category.getId(),
                SavingGoalCalculator.baseFrom(startDate), SavingGoalCalculator.baseTo(startDate)));
        // 목표가 기준 이상이면 아낄 돈이 없다. FE는 "예상 절약액 없음" 안내를 띄운다
        if (request.targetAmount() >= baseAmount) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_NO_EXPECTED_SAVING);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));
        // TODO: 절약 후보 테이블이 생기면 suggestionId의 후보를 "목표로 설정함"으로 바꾼다 (김지현 F-02)
        SavingGoal goal = savingGoalRepository.save(SavingGoal.builder()
                .user(user)
                .category(category)
                .baseAmount(baseAmount)
                .targetAmount(request.targetAmount())
                .startDate(startDate)
                .endDate(endDate)
                .linkedBlock(linkedBlock)
                .suggestionId(request.suggestionId())
                .build());
        return SavingGoalResponse.of(goal, spentSoFar(goal, today), SavingGoalStatus.ACTIVE);
    }

    // 진행 중·확정된 목표. 최근에 만든 순
    public SavingGoalListResponse getGoals(Long userId) {
        LocalDate today = LocalDate.now(KST);
        List<SavingGoal> goals = savingGoalRepository.findAllByUser_IdAndStatusInOrderByCreatedAtDescIdDesc(userId, LISTED);
        Set<Long> fundIds = goals.stream().map(SavingGoal::getWaitingFundId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> clearedFunds = waitingFundRepository.findAllById(fundIds).stream()
                .filter(fund -> fund.getAmount() == 0)
                .map(WaitingFund::getId)
                .collect(Collectors.toSet());
        return new SavingGoalListResponse(goals.stream()
                .map(goal -> SavingGoalResponse.of(goal, spentSoFar(goal, today), status(goal, clearedFunds)))
                .toList());
    }

    public SavingGoalResponse getGoal(Long userId, Long savingGoalId) {
        SavingGoal goal = findOwned(userId, savingGoalId);
        return toResponse(goal);
    }

    // 진행 중인 목표만 취소한다. 확정된 절약액은 이미 나눌 돈으로 옮겼으므로 되돌리지 않는다
    @Transactional
    public SavingGoalResponse cancel(Long userId, Long savingGoalId) {
        SavingGoal goal = findOwned(userId, savingGoalId);
        if (goal.getStatus() != SavingGoalStatus.ACTIVE) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_NOT_ACTIVE);
        }
        goal.cancel();
        return toResponse(goal);
    }

    private SavingGoalResponse toResponse(SavingGoal goal) {
        Set<Long> clearedFunds = goal.getWaitingFundId() == null ? Set.of()
                : waitingFundRepository.findById(goal.getWaitingFundId())
                .filter(fund -> fund.getAmount() == 0)
                .map(fund -> Set.of(fund.getId()))
                .orElse(Set.of());
        return SavingGoalResponse.of(goal, spentSoFar(goal, LocalDate.now(KST)), status(goal, clearedFunds));
    }

    // 확정 뒤 만든 절약분을 다 나눴으면 ALLOCATED로 보여 준다
    private SavingGoalStatus status(SavingGoal goal, Set<Long> clearedFunds) {
        if (goal.getStatus() == SavingGoalStatus.CONFIRMED && goal.getWaitingFundId() != null
                && clearedFunds.contains(goal.getWaitingFundId())) {
            return SavingGoalStatus.ALLOCATED;
        }
        return goal.getStatus();
    }

    // 확정됐으면 저장한 실제 지출액, 진행 중이면 시작일부터 오늘까지 쓴 돈
    private long spentSoFar(SavingGoal goal, LocalDate today) {
        if (goal.getActualAmount() != null) {
            return goal.getActualAmount();
        }
        if (goal.getStatus() != SavingGoalStatus.ACTIVE || today.isBefore(goal.getStartDate())) {
            return 0;
        }
        LocalDate until = today.isAfter(goal.getEndDate()) ? goal.getEndDate() : today;
        return spent(goal.getUser().getId(), goal.getCategory().getId(), goal.getStartDate(), until.plusDays(1));
    }

    // 소비 분석과 같은 조건(취소·소비 제외 거래 빼고 출금만)으로 센 카테고리 지출. to는 포함하지 않는다
    long spent(Long userId, Long categoryId, LocalDate from, LocalDate to) {
        return transactionRepository.sumSpentByCategory(userId, from.atStartOfDay(), to.atStartOfDay()).stream()
                .filter(row -> categoryId.equals(row.categoryId()))
                .mapToLong(CategorySpent::amount)
                .sum();
    }

    private Block linkedBlock(Long userId, Long blockId) {
        return blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .filter(block -> block.isOwnedBy(userId) && block.getType() == BlockType.GOAL)
                .orElseThrow(() -> GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_INVALID_LINKED_BLOCK));
    }

    // 없으면 404, 남의 목표면 403. 취소한 목표는 없는 것으로 본다
    private SavingGoal findOwned(Long userId, Long savingGoalId) {
        SavingGoal goal = savingGoalRepository.findById(savingGoalId)
                .filter(found -> found.getStatus() != SavingGoalStatus.CANCELED)
                .orElseThrow(() -> GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) {
            throw GeneralException.of(SavingGoalErrorCode.SAVING_GOAL_FORBIDDEN);
        }
        return goal;
    }
}
