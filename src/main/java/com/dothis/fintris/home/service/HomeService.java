package com.dothis.fintris.home.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.connection.entity.AccountType;
import com.dothis.fintris.connection.entity.LinkedAccount;
import com.dothis.fintris.connection.repository.LinkedAccountRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.dto.HomeResponse;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LinkedAccountRepository linkedAccountRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    public HomeResponse getHome(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw GeneralException.of(UserErrorCode.USER_NOT_FOUND);
        }

        // 카드는 잔액이 없으므로 총 자산·미배분 계산은 은행 계좌만 쓴다
        List<LinkedAccount> accounts = linkedAccountRepository
                .findAllByUser_IdAndTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(userId, AccountType.BANK);

        long totalAssets = accounts.stream()
                .map(LinkedAccount::getBalance)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        // 잔액이 가장 오래된 계좌 기준으로 "OO일 기준"을 보여 준다
        LocalDateTime balanceUpdatedAt = accounts.stream()
                .map(LinkedAccount::getBalanceUpdatedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        long unallocatedAmount = totalAssets - blockRepository.sumBalanceByUserId(userId);

        return new HomeResponse(
                totalAssets,
                accounts.size(),
                balanceUpdatedAt,
                unallocatedAmount,
                getDailyGuide(userId),
                accounts.stream().map(HomeResponse.AccountItem::from).toList()
        );
    }

    // TODO: 거래 반영(F-21)이 붙으면 "아침 기준 금액 - 오늘 쓴 돈"으로 바꾼다. 지금은 현재 잔액 기준
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

        return new HomeResponse.DailyGuide(
                defaultBlock.getId(),
                defaultBlock.getName(),
                defaultBlock.getBalance(),
                remainingDays,
                DailyGuideCalculator.todayAvailable(defaultBlock.getBalance(), remainingDays)
        );
    }
}
