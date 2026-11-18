package com.dothis.fintris.home.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.dto.HomeResponse;
import com.dothis.fintris.transaction.repository.BlockSpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LinkedAccountRepository linkedAccountRepository;
    private final BlockRepository blockRepository;
    private final BlockService blockService;
    private final WaitingFundService waitingFundService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public HomeResponse getHome(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw GeneralException.of(UserErrorCode.USER_NOT_FOUND);
        }

        // 카드는 잔액이 없으므로 총 자산·미배분 계산은 활성 은행 계좌만 쓴다
        List<LinkedAccount> accounts = linkedAccountRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getType() == LinkedAccountType.BANK && account.isActive())
                .toList();

        long totalBalance = accounts.stream()
                .map(LinkedAccount::getBalance)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        // 잔액이 가장 오래된 계좌 기준으로 "OO일 기준"을 보여 준다
        LocalDate asOf = oldest(accounts, LinkedAccount::getBalanceUpdatedAt)
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now(KST));

        // 블록 합계 + 나눌 돈이 실제 잔액보다 크면 경고 (v4 5장)
        List<WaitingFundResponse> waitingFunds = waitingFundService.getWaitingFunds(userId);
        long waitingTotal = waitingFunds.stream().mapToLong(WaitingFundResponse::amount).sum();
        boolean overAllocated = blockRepository.sumBalanceByUserId(userId) + waitingTotal > totalBalance;

        return new HomeResponse(
                asOf,
                totalBalance,
                accounts.size(),
                getDailyGuide(userId),
                accounts.stream().map(account -> HomeResponse.AccountItem.of(account, monthSpent(userId, account))).toList(),
                null,
                waitingFunds,
                overAllocated,
                blockService.getBlocks(userId, null),
                oldest(accounts, LinkedAccount::getLastSyncedAt).orElse(null),
                0,
                0
        );
    }

    private Optional<LocalDateTime> oldest(List<LinkedAccount> accounts,
                                           Function<LinkedAccount, LocalDateTime> time) {
        return accounts.stream()
                .map(time)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo);
    }

    // 이번 달 이 계좌에서 쓴 돈. 거래 목록의 spent와 같은 기준(취소·소비 제외 거래 빼고)
    private long monthSpent(Long userId, LinkedAccount account) {
        LocalDate monthStart = LocalDate.now(KST).withDayOfMonth(1);
        return transactionRepository.sumTotals(userId, monthStart.atStartOfDay(),
                monthStart.plusMonths(1).atStartOfDay(), account.getId(), null).spent();
    }

    // 오늘 남은 금액 (홈 dailyGuide.todayRemaining과 같은 값). 빠른 입력 저장 응답 등에서 쓴다. 기본 블록 기간이 없으면 null
    public Long todayRemaining(Long userId) {
        HomeResponse.DailyGuide guide = getDailyGuide(userId);
        return guide == null ? null : guide.todayRemaining();
    }

    // 블록 상세(사용형)와 같은 계산: "오늘 가능"은 아침 기준이라 오늘 기본 블록에서 뺀 돈을 되돌린 잔액으로 계산한다
    private HomeResponse.DailyGuide getDailyGuide(Long userId) {
        Block defaultBlock = blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                .orElse(null);
        if (defaultBlock == null || defaultBlock.getDueDate() == null) {
            return null;
        }

        LocalDate today = LocalDate.now(KST);
        long remainingDays = DailyGuideCalculator.remainingDays(today, defaultBlock.getDueDate());
        // 기간 갱신 로직이 아직 없어서, 기간이 끝난 블록은 계산하지 않는다
        if (remainingDays == 0) {
            return null;
        }

        BlockSpent todaySpentOfBlock = transactionRepository.sumBlockSpent(defaultBlock.getId(),
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long todayAvailable = DailyGuideCalculator.todayAvailable(
                defaultBlock.getBalance() + todaySpentOfBlock.deducted(), remainingDays);
        long todaySpent = todaySpentOfBlock.spent();
        return new HomeResponse.DailyGuide(
                defaultBlock.getId(),
                defaultBlock.getName(),
                defaultBlock.getBalance(),
                todayAvailable,
                todaySpent,
                todayAvailable - todaySpent,
                remainingDays,
                "BLOCK_PERIOD"
        );
    }
}
