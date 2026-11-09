package com.dothis.fintris.block.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.SupportedInstitution;
import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockDetailResponse;
import com.dothis.fintris.block.dto.BlockHistoryResponse;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.simulation.ImpactCalculator;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.service.DailyGuideCalculator;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.BlockSpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

// 블록 상세 화면 (앱 시안 02, 02-1, 02-2 / API 명세 v4 6장)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 50;
    private static final int RECENT_INFLOWS = 3;
    private static final int MAX_PROJECTED_MONTHS = 24;
    private static final int RECENT_TRANSACTIONS = 5;
    private static final int RECENT_PAYMENTS = 6;

    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final BlockCategoryRepository blockCategoryRepository;
    private final TransactionRepository transactionRepository;

    public BlockDetailResponse getDetail(Long userId, Long blockId) {
        Block block = findOwnedBlock(userId, blockId);
        BlockResponse response = BlockResponse.of(block, blockCategoryRepository.findAllByBlock_Id(blockId));
        LocalDate today = LocalDate.now(KST);
        return switch (block.getType()) {
            case GOAL -> goal(block, response, today);
            case FIXED -> fixed(block, response, today);
            case SPENDING -> spending(block, response, today);
        };
    }

    // 02-1: 달성률, 이번 달 확보, 언제 다 모일까요?, 들어온 돈
    private BlockDetailResponse goal(Block block, BlockResponse response, LocalDate today) {
        long target = block.getTargetAmount();
        long balance = block.getBalance();
        Long monthlyTarget = block.getRule() != null ? block.getRule().monthlyTarget() : null;

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        long secured = blockTransferRepository.sumInSince(block.getId(), monthStart)
                - blockTransferRepository.sumOutSince(block.getId(), monthStart);
        long needed = monthlyTarget != null ? Math.max(0, monthlyTarget - secured) : 0;

        LocalDate paceDate = ImpactCalculator.expectedDate(target, balance, monthlyTarget, today);
        Long delay = (paceDate != null && block.getDueDate() != null)
                ? ChronoUnit.DAYS.between(block.getDueDate(), paceDate) : null;

        List<BlockHistoryResponse.Item> inflows = blockTransferRepository
                .findHistory(block.getId(), PageRequest.of(0, 10)).getContent().stream()
                .map(transfer -> BlockHistoryResponse.Item.of(block, transfer))
                .filter(item -> item.amount() > 0)
                .limit(RECENT_INFLOWS)
                .toList();

        String resolution = null;
        if (balance >= target) {
            resolution = "ACHIEVED";
        } else if (block.getDueDate() != null && block.getDueDate().isBefore(today)) {
            resolution = "EXPIRED";
        }

        // 늦어지면 "목표일 미루기"와 "절약분 넣기" 버튼을 보여 준다 (02-1)
        List<String> actions = (delay != null && delay > 0)
                ? List.of("POSTPONE_DUE_DATE", "PUT_WAITING_FUND") : List.of();

        BlockDetailResponse.GoalDetail detail = new BlockDetailResponse.GoalDetail(
                BlockCalculator.fillRate(balance, target),
                new BlockDetailResponse.ThisMonth(monthlyTarget, secured, needed),
                new BlockDetailResponse.Projection(block.getDueDate(), paceDate, delay, paceLabel(delay),
                        series(block, target, monthlyTarget, today)),
                inflows,
                resolution);
        return new BlockDetailResponse(response, null, actions, ruleText(block), detail);
    }

    // 02-2: 확보율, 다음 납부일, 모자란 돈, 납부 확인 조건, 알림
    private BlockDetailResponse fixed(Block block, BlockResponse response, LocalDate today) {
        BlockRule rule = block.getRule();
        Long payAmount = rule != null ? rule.payAmount() : null;
        Integer payDay = rule != null ? rule.payDay() : null;
        long shortage = payAmount != null ? Math.max(0, payAmount - block.getBalance()) : 0;

        // 모자라면 "다른 블록에서 채우기" 버튼 (03-2 이동 시트로)
        List<String> actions = shortage > 0 ? List.of("FILL_FROM_OTHER") : List.of();
        // 고정지출 블록에 반영된 출금 = 납부 확인 조건에 맞은 납부
        List<BlockDetailResponse.Payment> payments = transactionRepository
                .findTop12ByBlock_IdOrderByOccurredAtDescIdDesc(block.getId()).stream()
                .map(transaction -> payment(transaction, payDay))
                .toList();

        BlockDetailResponse.FixedDetail detail = new BlockDetailResponse.FixedDetail(
                Objects.requireNonNullElse(BlockCalculator.fillRate(block.getBalance(), payAmount), 0),
                payDay != null ? BlockCalculator.nextPayDate(today, payDay) : null,
                payAmount != null ? payAmount : 0,
                shortage,
                rule != null ? rule.matchCondition() : null,
                rule != null ? rule.alerts() : null,
                onTimeStreak(payments),
                payments.stream().limit(RECENT_PAYMENTS).toList());
        return new BlockDetailResponse(response, null, actions, ruleText(block), detail);
    }

    // 02: 기간, 남은 비율·일수, 오늘 가능·씀·남음 (홈 dailyGuide와 같은 계산)
    private BlockDetailResponse spending(Block block, BlockResponse response, LocalDate today) {
        Long monthlyAmount = block.getRule() != null ? block.getRule().monthlyAmount() : null;
        long days = block.getDueDate() != null ? DailyGuideCalculator.remainingDays(today, block.getDueDate()) : 0;
        // "오늘 가능"은 아침 기준이라 하루 동안 그대로다: 오늘 이 블록에서 뺀 돈을 되돌린 잔액으로 계산한다
        BlockSpent todaySpent = transactionRepository.sumBlockSpent(block.getId(),
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long available = DailyGuideCalculator.todayAvailable(block.getBalance() + todaySpent.deducted(), days);
        long spent = todaySpent.spent();

        BlockDetailResponse.SpendingDetail detail = new BlockDetailResponse.SpendingDetail(
                new BlockDetailResponse.Period(block.getStartDate(), block.getDueDate()),
                BlockCalculator.fillRate(block.getBalance(), monthlyAmount),
                days,
                new BlockDetailResponse.Today(available, spent, available - spent),
                null,
                transactionRepository.findTop12ByBlock_IdOrderByOccurredAtDescIdDesc(block.getId()).stream()
                        .limit(RECENT_TRANSACTIONS).map(TransactionResponse::from).toList());
        return new BlockDetailResponse(response, null, List.of(), ruleText(block), detail);
    }

    private String paceLabel(Long delay) {
        if (delay == null) {
            return null;
        }
        if (delay == 0) {
            return "목표대로";
        }
        return "목표보다 " + ImpactCalculator.span(Math.abs(delay)) + (delay > 0 ? " 늦음" : " 빠름");
    }

    // 월별 누적: 블록을 만든 달부터 이번 달까지는 실제 이동 기록, 그 뒤는 월 확보 금액으로 목표까지 예상
    private List<BlockDetailResponse.SeriesPoint> series(Block block, long target, Long monthlyTarget, LocalDate today) {
        TreeMap<YearMonth, Long> net = new TreeMap<>();
        for (BlockTransfer transfer : blockTransferRepository.findAllOfBlock(block.getId())) {
            YearMonth month = YearMonth.from(transfer.getCreatedAt());
            boolean incoming = transfer.getToBlock() != null && transfer.getToBlock().getId().equals(block.getId());
            net.merge(month, incoming ? transfer.getAmount() : -transfer.getAmount(), Long::sum);
        }

        List<BlockDetailResponse.SeriesPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        YearMonth month = net.isEmpty() ? current : net.firstKey();
        long cumulative = 0;
        for (; !month.isAfter(current); month = month.plusMonths(1)) {
            cumulative += net.getOrDefault(month, 0L);
            points.add(new BlockDetailResponse.SeriesPoint(month.toString(), cumulative, false));
        }
        if (monthlyTarget != null && monthlyTarget > 0) {
            for (int i = 0; i < MAX_PROJECTED_MONTHS && cumulative < target; i++, month = month.plusMonths(1)) {
                cumulative = Math.min(target, cumulative + monthlyTarget);
                points.add(new BlockDetailResponse.SeriesPoint(month.toString(), cumulative, true));
            }
        }
        return points;
    }

    private BlockDetailResponse.Payment payment(Transaction transaction, Integer payDay) {
        LocalDate date = transaction.getOccurredAt().toLocalDate();
        boolean onTime = payDay == null || date.getDayOfMonth() <= Math.min(payDay, date.lengthOfMonth());
        return new BlockDetailResponse.Payment(date, transaction.getMerchantOrPayee(),
                accountLabel(transaction.getLinkedAccount()), transaction.getAmount(), onTime);
    }

    // 예: 국민은행 ••34. 현금 입력이면 null
    private String accountLabel(LinkedAccount account) {
        if (account == null) {
            return null;
        }
        String name = SupportedInstitution.findByCode(account.getInstitutionCode())
                .map(SupportedInstitution::getDisplayName).orElse(account.getInstitutionCode());
        return account.getAccountNumberLast4() == null ? name : name + " ••" + account.getAccountNumberLast4();
    }

    // 최신 납부부터 한 달씩 거슬러 올라가며, 달마다 제때 낸 납부가 있으면 센다. 빠진 달이나 늦은 달에서 멈춘다
    static int onTimeStreak(List<BlockDetailResponse.Payment> payments) {
        if (payments.isEmpty()) {
            return 0;
        }
        Map<YearMonth, Boolean> onTimeByMonth = new HashMap<>();
        for (BlockDetailResponse.Payment payment : payments) {
            onTimeByMonth.merge(YearMonth.from(payment.date()), payment.onTime(), Boolean::logicalOr);
        }
        int streak = 0;
        for (YearMonth month = YearMonth.from(payments.getFirst().date());
             Boolean.TRUE.equals(onTimeByMonth.get(month)); month = month.minusMonths(1)) {
            streak++;
        }
        return streak;
    }

    // 화면 "규칙" 한 줄
    String ruleText(Block block) {
        BlockRule rule = block.getRule();
        return switch (block.getType()) {
            case GOAL -> {
                List<String> parts = new ArrayList<>();
                parts.add("목표 " + ImpactCalculator.won(block.getTargetAmount()));
                if (block.getDueDate() != null) {
                    parts.add(block.getDueDate().getMonthValue() + "월 " + block.getDueDate().getDayOfMonth() + "일");
                }
                if (rule != null && rule.monthlyTarget() != null) {
                    parts.add("매달 " + ImpactCalculator.won(rule.monthlyTarget()));
                }
                yield String.join(" · ", parts);
            }
            case FIXED -> (rule != null && rule.payDay() != null && rule.payAmount() != null)
                    ? "매월 " + rule.payDay() + "일 · " + ImpactCalculator.won(rule.payAmount()) + "원" : null;
            case SPENDING -> (rule != null && rule.monthlyAmount() != null)
                    ? "월 " + ImpactCalculator.won(rule.monthlyAmount()) + "원 · 매월 "
                    + (rule.startDay() != null ? rule.startDay() : 1) + "일 시작"
                    : block.getStartDate() + " ~ " + block.getDueDate();
        };
    }

    // TODO: 고정지출 납부 기록(PAYMENT)은 거래 반영(F-21)이 붙으면 같은 목록에 넣는다
    public BlockHistoryResponse getHistory(Long userId, Long blockId, int page, int size) {
        Block block = findOwnedBlock(userId, blockId);
        Slice<BlockTransfer> slice = blockTransferRepository.findHistory(blockId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE)));
        return new BlockHistoryResponse(
                slice.getContent().stream().map(transfer -> BlockHistoryResponse.Item.of(block, transfer)).toList(),
                slice.hasNext());
    }

    // 없으면 404, 남의 블록이면 403
    Block findOwnedBlock(Long userId, Long blockId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));
        if (!block.isOwnedBy(userId)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_FORBIDDEN);
        }
        return block;
    }
}
