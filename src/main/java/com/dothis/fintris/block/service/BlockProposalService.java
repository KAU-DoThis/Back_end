package com.dothis.fintris.block.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockProposalConfirmRequest;
import com.dothis.fintris.block.dto.BlockProposalConfirmResponse;
import com.dothis.fintris.block.dto.BlockProposalPreviewRequest;
import com.dothis.fintris.block.dto.BlockProposalPreviewResponse;
import com.dothis.fintris.block.dto.BlockProposalResponse;
import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockColor;
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector.RecurringPayment;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.user.repository.UserSettingRepository;
import com.dothis.fintris.user.service.UserService;
import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// 온보딩 3/3 초기 블록 구성 (앱 시안 04, API 명세 v4 6장)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockProposalService {

    private final BlockService blockService;
    private final UserService userService;
    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final WaitingFundRepository waitingFundRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringPaymentDetector recurringPaymentDetector;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> ADDABLE_PRESETS = List.of("SAVING", "EMERGENCY", "TRAVEL");

    // 구성안 조회: 최근 3달 거래에서 찾은 고정지출을 먼저 채우고, 남은 돈으로 기본 생활비 블록을 제안한다
    // TODO: 월 평균 수입을 채우고 reason은 AI 문장(실패 시 템플릿)으로 바꾼다
    public BlockProposalResponse getProposals(Long userId) {
        long linked = linkedBalance(userId);
        long available = Math.max(0, unassigned(userId, linked));

        LocalDate today = LocalDate.now(KST);
        List<Transaction> outgoing = recentOutgoing(userId, today);
        List<RecurringPayment> recurring = recurringPaymentDetector.detect(outgoing);
        Long recent3mAvg = recent3mAvg(outgoing, recurring, today);

        List<BlockProposalResponse.Proposal> proposals = new ArrayList<>();
        // 나눌 돈이 모자라면 앞의 고정지출부터 채운다. 합이 잔액을 넘으면 확정할 수 없기 때문
        long left = available;
        for (int i = 0; i < recurring.size(); i++) {
            long balance = Math.min(recurring.get(i).payAmount(), left);
            left -= balance;
            proposals.add(fixedProposal(i + 1, recurring.get(i), balance));
        }
        // 이미 기본 블록이 있으면 새로 제안하지 않는다
        if (!blockRepository.existsByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId)) {
            proposals.add(new BlockProposalResponse.Proposal(
                    "spending-default", BlockType.SPENDING, "생활비", "wallet", BlockColor.I, true,
                    left, new BlockRule(left, 1, null, null, null, null, null),
                    null, recent3mAvg, dailyApprox(left, today), null,
                    recurring.isEmpty()
                            ? "아직 거래 내역이 없어 남은 잔액 전체로 시작했어요. 한 달에 쓸 만큼으로 고쳐 주세요."
                            : "고정지출을 빼고 남은 돈으로 시작했어요. 한 달에 쓸 만큼으로 고쳐 주세요."));
        }
        long allocated = proposals.stream().mapToLong(BlockProposalResponse.Proposal::balance).sum();

        return new BlockProposalResponse(
                new BlockProposalResponse.Summary(null, recurring.size(), linked, allocated, available - allocated),
                proposals, ADDABLE_PRESETS, false);
    }

    // 04에서 금액을 고칠 때마다 요약·하루 금액·경고를 다시 계산한다. 아무것도 저장하지 않는다
    // 경고는 기본 사용형 블록의 월 금액이 최근 3달 평균 지출보다 적을 때만 붙인다
    public BlockProposalPreviewResponse preview(Long userId, BlockProposalPreviewRequest request) {
        LocalDate today = LocalDate.now(KST);
        long linked = linkedBalance(userId);
        long allocated = request.blocks().stream().mapToLong(BlockProposalPreviewRequest.Item::balance).sum();

        List<Transaction> outgoing = recentOutgoing(userId, today);
        Long recent3mAvg = recent3mAvg(outgoing, recurringPaymentDetector.detect(outgoing), today);
        BlockProposalPreviewRequest.Item defaultItem = request.blocks().stream()
                .filter(item -> Boolean.TRUE.equals(item.isDefault()))
                .findFirst()
                .orElseGet(() -> request.blocks().stream()
                        .filter(item -> item.type() == BlockType.SPENDING).findFirst().orElse(null));

        List<BlockProposalPreviewResponse.Block> blocks = request.blocks().stream()
                .map(item -> {
                    if (item.type() != BlockType.SPENDING) {
                        return new BlockProposalPreviewResponse.Block(item.proposalKey(), null, null);
                    }
                    long monthly = item.rule() != null && item.rule().monthlyAmount() != null
                            ? item.rule().monthlyAmount() : item.balance();
                    String warning = item == defaultItem && recent3mAvg != null && monthly < recent3mAvg
                            ? "최근 3달 평균보다 %,d원 적어요".formatted(recent3mAvg - monthly) : null;
                    return new BlockProposalPreviewResponse.Block(item.proposalKey(), dailyApprox(monthly, today), warning);
                })
                .toList();
        return new BlockProposalPreviewResponse(linked, allocated, unassigned(userId, linked) - allocated, blocks);
    }

    // 연동 잔액 중 아직 블록·나눌 돈에 없는 돈 (처음 구성이면 연동 잔액 전체)
    private long unassigned(Long userId, long linked) {
        return linked - blockRepository.sumBalanceByUserId(userId) - waitingFundRepository.sumAmountByUserId(userId);
    }

    // 고정지출 탐지와 3달 평균에 쓰는 출금 (취소 거래 제외)
    private List<Transaction> recentOutgoing(Long userId, LocalDate today) {
        return transactionRepository.findOutgoingSince(userId, today.minusMonths(3).withDayOfMonth(1).atStartOfDay());
    }

    // 최근 3달(오늘부터 거꾸로) 생활비로 쓴 돈의 월 평균, 100원 내림. 소비 제외 거래와 고정지출 납부는 뺀다
    // 거래가 없으면 null (FE는 경고를 띄우지 않는다)
    static Long recent3mAvg(List<Transaction> outgoing, List<RecurringPayment> recurring, LocalDate today) {
        LocalDateTime from = today.minusMonths(3).atStartOfDay();
        List<Transaction> spending = outgoing.stream()
                .filter(transaction -> !transaction.getOccurredAt().isBefore(from))
                .filter(transaction -> !transaction.isExcluded())
                .filter(transaction -> recurring.stream().noneMatch(payment -> isPayment(payment, transaction)))
                .toList();
        if (spending.isEmpty()) {
            return null;
        }
        return spending.stream().mapToLong(Transaction::getAmount).sum() / 3 / 100 * 100;
    }

    private static boolean isPayment(RecurringPayment payment, Transaction transaction) {
        return payment.payee().equals(transaction.getMerchantOrPayee())
                && payment.minAmount() <= transaction.getAmount() && transaction.getAmount() <= payment.maxAmount();
    }

    // 하루 약 얼마 = 월 금액 ÷ 이번 달 일수, 100원 내림
    private static long dailyApprox(long monthly, LocalDate today) {
        return monthly / today.lengthOfMonth() / 100 * 100;
    }

    // 금액이 달마다 조금씩 다르면 납부액 ±10% 안의 출금을 납부로 본다 (API 명세 v4 6장 예시)
    private BlockProposalResponse.Proposal fixedProposal(int no, RecurringPayment payment, long balance) {
        long amount = payment.payAmount();
        BlockRule.MatchCondition condition = payment.minAmount() == payment.maxAmount()
                ? new BlockRule.MatchCondition(payment.payee(), BlockRule.AmountMode.EXACT, amount, amount,
                        RecurringPaymentDetector.MAX_DAY_GAP, payment.linkedAccountId())
                : new BlockRule.MatchCondition(payment.payee(), BlockRule.AmountMode.RANGE,
                        amount * (100 - RecurringPaymentDetector.AMOUNT_TOLERANCE_PERCENT) / 100,
                        amount * (100 + RecurringPaymentDetector.AMOUNT_TOLERANCE_PERCENT) / 100,
                        RecurringPaymentDetector.MAX_DAY_GAP, payment.linkedAccountId());
        BlockRule rule = new BlockRule(null, null, null, payment.payDay(), amount, condition,
                new BlockRule.Alerts(true, true, false));
        return new BlockProposalResponse.Proposal(
                "fixed-" + no, BlockType.FIXED, payment.payee(), "receipt", BlockColor.L, false,
                balance, rule, amount, null, 0, "TRANSACTIONS",
                "지난 %d달 매월 %d일 전후 %s 앞으로 %,d원이 나갔어요"
                        .formatted(payment.months(), payment.payDay(), payment.payee(), amount));
    }

    // [시작하기]: 고친 구성안을 잔액과 함께 한 번에 만들고, 남는 돈은 '남은 돈' 나눌 돈으로 둔다
    @Transactional
    public BlockProposalConfirmResponse confirm(Long userId, BlockProposalConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));
        UserSetting setting = userSettingRepository.findByUser_Id(userId)
                .orElseGet(() -> userSettingRepository.save(UserSetting.builder()
                        .user(user).tutorialDone(false).blocksConfirmed(false).build()));
        if (setting.isBlocksConfirmed()) {
            throw GeneralException.of(BlockErrorCode.BLOCK_PROPOSAL_ALREADY_CONFIRMED);
        }

        List<BlockProposalConfirmRequest.Item> items = request.blocks();
        int defaultIndex = chooseDefault(userId, items);

        // 불변식: 블록 잔액 합 + 나눌 돈 합 = 실제 잔액. 넘치면 확정하지 않는다
        long newTotal = items.stream().mapToLong(BlockProposalConfirmRequest.Item::balance).sum();
        long remaining = linkedBalance(userId)
                - blockRepository.sumBalanceByUserId(userId)
                - waitingFundRepository.sumAmountByUserId(userId)
                - newTotal;
        if (remaining < 0) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INSUFFICIENT_FUNDS);
        }

        int order = blockRepository.findTopByUser_IdAndDeletedAtIsNullOrderByDisplayOrderDesc(userId)
                .map(last -> last.getDisplayOrder() + 1)
                .orElse(0);
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BlockProposalConfirmRequest.Item item = items.get(i);
            BlockCreatedBy createdBy = Objects.requireNonNullElse(item.createdBy(), BlockCreatedBy.USER);
            blocks.add(blockService.newBlock(user, item.toCreateRequest(), item.balance(),
                    order + i, i == defaultIndex, createdBy));
        }
        blockRepository.saveAll(blocks);

        // 블록 기록(02 화면 "블록 만듦 · 첫 배분")에 쓰도록 첫 배분을 남긴다
        List<BlockTransfer> firstFunding = blocks.stream()
                .filter(block -> block.getBalance() > 0)
                .<BlockTransfer>map(block -> BlockTransfer.builder()
                        .toBlock(block)
                        .amount(block.getBalance())
                        .reason(TransferReason.ALLOCATION)
                        .build())
                .toList();
        blockTransferRepository.saveAll(firstFunding);

        List<WaitingFundResponse> waitingFunds = List.of();
        if (remaining > 0) {
            WaitingFund fund = waitingFundRepository.save(WaitingFund.builder()
                    .user(user)
                    .source(WaitingFundSource.OTHER)
                    .label(WaitingFundSource.OTHER.getDefaultLabel())
                    .description("블록 구성 후 남은 돈")
                    .amount(remaining)
                    .originalAmount(remaining)
                    .build());
            waitingFunds = List.of(WaitingFundResponse.from(fund));
        }

        setting.confirmBlocks();
        return new BlockProposalConfirmResponse(
                blocks.stream().map(block -> BlockResponse.of(block, List.of())).toList(),
                waitingFunds,
                userService.getOnboardingStep(userId));
    }

    // 기본 사용형 블록은 사용자당 하나. 이미 있으면 새로 정하지 않고(-1),
    // 없으면 isDefault로 고른 사용형, 그것도 없으면 첫 사용형 블록을 기본으로 한다
    private int chooseDefault(Long userId, List<BlockProposalConfirmRequest.Item> items) {
        List<Integer> marked = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (Boolean.TRUE.equals(items.get(i).isDefault())) {
                marked.add(i);
            }
        }
        if (marked.size() > 1 || marked.stream().anyMatch(i -> items.get(i).type() != BlockType.SPENDING)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DEFAULT);
        }
        if (blockRepository.existsByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId)) {
            return -1;
        }
        if (!marked.isEmpty()) {
            return marked.get(0);
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).type() == BlockType.SPENDING) {
                return i;
            }
        }
        throw GeneralException.of(BlockErrorCode.BLOCK_INVALID_DEFAULT);
    }

    // 카드는 잔액이 없으므로 활성 은행 계좌만 더한다 (홈 totalBalance와 같은 기준)
    private long linkedBalance(Long userId) {
        return linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getType() == LinkedAccountType.BANK && account.isActive())
                .map(LinkedAccount::getBalance)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }
}
