package com.dothis.fintris.account.service;

import com.dothis.fintris.account.code.AccountErrorCode;
import com.dothis.fintris.account.dto.LinkedAccountCreateRequest;
import com.dothis.fintris.account.dto.LinkedAccountResponse;
import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.SupportedInstitution;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefAccountClient;
import com.dothis.fintris.codef.client.CodefAccountClient.CodefAccountResult;
import com.dothis.fintris.codef.client.CodefBankAccountClient;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccount;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccountsResult;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.UserErrorCode;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// CODEF 호출이 오래 걸릴 수 있어 DB 트랜잭션을 걸지 않고, 저장만 saveAll의 트랜잭션으로 묶는다
@Service
@RequiredArgsConstructor
public class LinkedAccountService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String ADDITIONAL_AUTH_REQUIRED = "CF-03002";

    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;
    private final CodefAccountClient codefAccountClient;
    private final CodefBankAccountClient codefBankAccountClient;

    @Transactional(readOnly = true)
    public List<LinkedAccountResponse> getLinkedAccounts(Long userId) {
        return linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .map(LinkedAccountResponse::from)
                .toList();
    }

    public List<LinkedAccountResponse> link(Long userId, LinkedAccountCreateRequest request) {
        SupportedInstitution institution = SupportedInstitution.findByCode(request.institutionCode())
                .orElseThrow(() -> GeneralException.of(AccountErrorCode.ACCOUNT_INSTITUTION_NOT_SUPPORTED));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> GeneralException.of(UserErrorCode.USER_NOT_FOUND));

        List<LinkedAccount> existing = linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
        if (existing.stream().anyMatch(account -> account.getInstitutionCode().equals(institution.getCode()))) {
            throw GeneralException.of(AccountErrorCode.ACCOUNT_ALREADY_LINKED);
        }
        // connectedId는 사용자마다 하나이므로 이미 있으면 기관만 추가(add)한다
        String connectedId = existing.isEmpty() ? null : existing.get(0).getConnectedId();

        CodefAccountResult registered = codefAccountClient.register(connectedId, institution.getCode(),
                institution.getType(), request.loginId(), request.password(), request.birthDate());
        if (!registered.isSuccess()) {
            throw toLinkException(registered.failureCode(), registered.failureMessage());
        }

        BankAccountsResult fetched = codefBankAccountClient
                .fetchDemandDepositAccounts(registered.connectedId(), institution.getCode());
        if (!fetched.isSuccess()) {
            throw toLinkException(fetched.failureCode(), fetched.failureMessage());
        }
        if (fetched.accounts().isEmpty()) {
            throw GeneralException.of(AccountErrorCode.ACCOUNT_NO_ELIGIBLE_ACCOUNT);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        List<LinkedAccount> saved = linkedAccountRepository.saveAll(fetched.accounts().stream()
                .map(account -> toEntity(user, institution, registered.connectedId(), account, now))
                .toList());
        return saved.stream().map(LinkedAccountResponse::from).toList();
    }

    private LinkedAccount toEntity(User user, SupportedInstitution institution, String connectedId,
                                   BankAccount account, LocalDateTime now) {
        return LinkedAccount.builder()
                .user(user)
                .institutionCode(institution.getCode())
                .type(institution.getType())
                .connectedId(connectedId)
                .accountName(account.accountName())
                .accountNumber(account.accountNumber())
                .accountNumberLast4(last4(account.accountNumber()))
                .balance(account.balance())
                .balanceUpdatedAt(account.balance() == null ? null : now)
                .build();
    }

    private String last4(String accountNumber) {
        String digits = accountNumber.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    // 추가 인증은 아직 지원하지 않고, 그 밖의 거절은 기관이 준 사유를 그대로 전달한다
    private GeneralException toLinkException(String code, String message) {
        if (ADDITIONAL_AUTH_REQUIRED.equals(code)) {
            return GeneralException.of(AccountErrorCode.ACCOUNT_ADDITIONAL_AUTH_UNSUPPORTED);
        }
        return new AccountLinkRejectedException(message);
    }
}
