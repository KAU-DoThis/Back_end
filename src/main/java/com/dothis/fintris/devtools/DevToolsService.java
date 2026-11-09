package com.dothis.fintris.devtools;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

// 개발 서버 전용: CODEF 연동 없이 FE가 블록 구성·이동을 시험할 수 있게 가짜 은행 계좌를 만든다
// 계좌번호가 없어서 거래내역 동기화가 이 계좌로 CODEF를 부르지 않는다
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-tools.enabled", havingValue = "true")
public class DevToolsService {

    static final String TEST_ACCOUNT_NAME = "개발용 테스트 계좌";
    private static final String TEST_INSTITUTION_CODE = "0004";
    private static final String TEST_CONNECTED_ID = "DEV-TEST-NOT-REAL";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;

    // 테스트 계좌가 없으면 만들고, 있으면 잔액만 바꾼다
    // 잔액을 바꾸면 블록·나눌 돈 합과 차이가 생기고, 그 차이가 구성안의 "나눌 수 있는 돈"이 된다
    @Transactional
    public DevTestAccountResponse setTestAccount(Long userId, long balance) {
        LocalDateTime now = LocalDateTime.now(KST);
        LinkedAccount existing = linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
                .stream()
                .filter(DevToolsService::isTestAccount)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.markSynced(now, balance);
            return new DevTestAccountResponse(existing.getId(), balance, false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));
        LinkedAccount account = linkedAccountRepository.save(LinkedAccount.builder()
                .user(user)
                .institutionCode(TEST_INSTITUTION_CODE)
                .type(LinkedAccountType.BANK)
                .connectedId(TEST_CONNECTED_ID)
                .accountName(TEST_ACCOUNT_NAME)
                .accountNumberLast4("0000")
                .balance(balance)
                .balanceUpdatedAt(now)
                .lastSyncedAt(now)
                .isActive(true)
                .build());
        return new DevTestAccountResponse(account.getId(), balance, true);
    }

    private static boolean isTestAccount(LinkedAccount account) {
        return account.getAccountNumber() == null && TEST_ACCOUNT_NAME.equals(account.getAccountName());
    }
}
