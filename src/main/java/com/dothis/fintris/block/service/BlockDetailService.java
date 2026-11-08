package com.dothis.fintris.block.service;

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
import java.util.List;
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

    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final BlockCategoryRepository blockCategoryRepository;

    public BlockDetailResponse getDetail(Long userId, Long blockId) {
        Block block = findOwnedBlock(userId, blockId);
        BlockResponse response = BlockResponse.of(block, blockCategoryRepository.findAllByBlock_Id(blockId));
        LocalDate today = LocalDate.now(KST);
        return switch (block.getType()) {
            case GOAL -> goal(block, response, today);
            case FIXED, SPENDING -> new BlockDetailResponse(response, null, List.of(), ruleText(block), null);
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
