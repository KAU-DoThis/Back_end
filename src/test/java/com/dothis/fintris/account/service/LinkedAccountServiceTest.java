package com.dothis.fintris.account.service;

import com.dothis.fintris.account.code.AccountErrorCode;
import com.dothis.fintris.account.dto.LinkedAccountCreateRequest;
import com.dothis.fintris.account.dto.LinkedAccountResponse;
import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefAccountClient;
import com.dothis.fintris.codef.client.CodefAccountClient.CodefAccountResult;
import com.dothis.fintris.codef.client.CodefBankAccountClient;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccount;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccountsResult;
import com.dothis.fintris.codef.client.CodefCardClient;
import com.dothis.fintris.codef.client.CodefCardClient.Card;
import com.dothis.fintris.codef.client.CodefCardClient.CardsResult;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedAccountServiceTest {

    private static final Long USER_ID = 1L;
    private static final String KOOKMIN = "0004";
    private static final String SHINHAN = "0088";
    private static final String SHINHAN_CARD = "0306";

    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock UserRepository userRepository;
    @Mock CodefAccountClient codefAccountClient;
    @Mock CodefBankAccountClient codefBankAccountClient;
    @Mock CodefCardClient codefCardClient;
    @InjectMocks LinkedAccountService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    private LinkedAccountCreateRequest request(String institutionCode) {
        return new LinkedAccountCreateRequest(institutionCode, "codef", "pw1234", "980225");
    }

    private void givenUserAndExisting(List<LinkedAccount> existing) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(existing);
    }

    private LinkedAccount existingAccount(String institutionCode, String connectedId) {
        return LinkedAccount.builder().user(user).institutionCode(institutionCode)
                .type(LinkedAccountType.BANK).connectedId(connectedId).build();
    }

    private void givenSavedEcho() {
        when(linkedAccountRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("첫 연동이면 connectedId 없이 등록하고 계좌마다 한 행을 저장한다")
    void firstLinkCreatesConnectedIdAndSavesEachAccount() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(isNull(), eq(KOOKMIN), eq(LinkedAccountType.BANK),
                eq("codef"), eq("pw1234"), eq("980225")))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefBankAccountClient.fetchDemandDepositAccounts("cid-1", KOOKMIN)).thenReturn(
                new BankAccountsResult(List.of(
                        new BankAccount("1002440001234", "입출금통장", 1_250_000L),
                        new BankAccount("1002440005678", "생활비통장", null)), null, null));
        givenSavedEcho();

        List<LinkedAccountResponse> responses = service.link(USER_ID, request(KOOKMIN));

        ArgumentCaptor<List<LinkedAccount>> captor = ArgumentCaptor.captor();
        verify(linkedAccountRepository).saveAll(captor.capture());
        List<LinkedAccount> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getConnectedId()).isEqualTo("cid-1");
        assertThat(saved.get(0).getAccountNumber()).isEqualTo("1002440001234");
        assertThat(saved.get(0).getAccountNumberLast4()).isEqualTo("1234");
        assertThat(saved.get(0).getBalance()).isEqualTo(1_250_000L);
        assertThat(saved.get(0).getBalanceUpdatedAt()).isNotNull();
        // 잔액을 모르면 갱신 시각도 남기지 않는다
        assertThat(saved.get(1).getBalance()).isNull();
        assertThat(saved.get(1).getBalanceUpdatedAt()).isNull();

        assertThat(responses).extracting(LinkedAccountResponse::institutionName).containsOnly("국민은행");
        assertThat(responses).extracting(LinkedAccountResponse::accountNumberLast4).containsExactly("1234", "5678");
    }

    @Test
    @DisplayName("이미 connectedId가 있으면 그 값으로 기관을 추가한다")
    void additionalInstitutionReusesConnectedId() {
        givenUserAndExisting(List.of(existingAccount(KOOKMIN, "cid-1")));
        when(codefAccountClient.register(eq("cid-1"), eq(SHINHAN), eq(LinkedAccountType.BANK),
                anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefBankAccountClient.fetchDemandDepositAccounts("cid-1", SHINHAN)).thenReturn(
                new BankAccountsResult(List.of(new BankAccount("110123456789", "신한 입출금", 5_000L)), null, null));
        givenSavedEcho();

        service.link(USER_ID, request(SHINHAN));

        verify(codefAccountClient).register(eq("cid-1"), eq(SHINHAN), eq(LinkedAccountType.BANK),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("지원하지 않는 기관코드는 CODEF를 호출하지 않고 거절한다")
    void rejectsUnsupportedInstitution() {
        assertThatThrownBy(() -> service.link(USER_ID, request("9999")))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_INSTITUTION_NOT_SUPPORTED);
        verifyNoInteractions(codefAccountClient, codefBankAccountClient);
    }

    @Test
    @DisplayName("이미 연동한 기관은 CODEF를 호출하지 않고 거절한다")
    void rejectsAlreadyLinkedInstitution() {
        givenUserAndExisting(List.of(existingAccount(KOOKMIN, "cid-1")));

        assertThatThrownBy(() -> service.link(USER_ID, request(KOOKMIN)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_ALREADY_LINKED);
        verifyNoInteractions(codefAccountClient, codefBankAccountClient);
    }

    @Test
    @DisplayName("기관이 인증을 거절하면 기관이 준 사유를 담아 던지고 저장하지 않는다")
    void deliversInstitutionRejectionReason() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult(null, "CF-12801", "비밀번호가 일치하지 않습니다."));

        assertThatThrownBy(() -> service.link(USER_ID, request(KOOKMIN)))
                .isInstanceOfSatisfying(AccountLinkRejectedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(AccountErrorCode.ACCOUNT_LINK_REJECTED);
                    assertThat(e.getInstitutionCode()).isEqualTo("CF-12801");
                    assertThat(e.getInstitutionMessage()).isEqualTo("비밀번호가 일치하지 않습니다.");
                });
        verify(linkedAccountRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("추가 인증(CF-03002)이 필요하면 미지원 오류로 안내한다")
    void additionalAuthIsUnsupported() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult(null, "CF-03002", "추가 인증이 필요합니다."));

        assertThatThrownBy(() -> service.link(USER_ID, request(KOOKMIN)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_ADDITIONAL_AUTH_UNSUPPORTED);
    }

    @Test
    @DisplayName("보유계좌 조회가 거절되면 그 사유를 전달하고 저장하지 않는다")
    void deliversAccountListFailureReason() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefBankAccountClient.fetchDemandDepositAccounts("cid-1", KOOKMIN))
                .thenReturn(new BankAccountsResult(List.of(), "CF-02100", "조회 계좌로 등록된 계좌가 없습니다."));

        assertThatThrownBy(() -> service.link(USER_ID, request(KOOKMIN)))
                .isInstanceOfSatisfying(AccountLinkRejectedException.class,
                        e -> assertThat(e.getInstitutionMessage()).isEqualTo("조회 계좌로 등록된 계좌가 없습니다."));
        verify(linkedAccountRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("연동할 입출금 계좌가 하나도 없으면 오류로 안내한다")
    void failsWhenNoEligibleAccount() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefBankAccountClient.fetchDemandDepositAccounts("cid-1", KOOKMIN))
                .thenReturn(new BankAccountsResult(List.of(), null, null));

        assertThatThrownBy(() -> service.link(USER_ID, request(KOOKMIN)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_NO_ELIGIBLE_ACCOUNT);
        verify(linkedAccountRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("카드사는 보유카드를 조회해 카드마다 한 행을 저장하고, 잔액·미배분 포함은 비워 둔다")
    void linksCardsWithoutBalance() {
        givenUserAndExisting(List.of(existingAccount(KOOKMIN, "cid-1")));
        when(codefAccountClient.register(eq("cid-1"), eq(SHINHAN_CARD), eq(LinkedAccountType.CARD),
                anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefCardClient.fetchCards("cid-1", SHINHAN_CARD, "980225")).thenReturn(
                new CardsResult(List.of(
                        new Card("6056********0123", "OO체크카드", "체크/본인"),
                        new Card("6253********0456", "할인카드", "신용/본인")), null, null));
        givenSavedEcho();

        List<LinkedAccountResponse> responses = service.link(USER_ID, request(SHINHAN_CARD));

        ArgumentCaptor<List<LinkedAccount>> captor = ArgumentCaptor.captor();
        verify(linkedAccountRepository).saveAll(captor.capture());
        List<LinkedAccount> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(card -> {
            assertThat(card.getType()).isEqualTo(LinkedAccountType.CARD);
            assertThat(card.getBalance()).isNull();
            assertThat(card.getBalanceUpdatedAt()).isNull();
            assertThat(card.isActive()).isFalse();
            assertThat(card.getConnectedId()).isEqualTo("cid-1");
        });
        assertThat(saved.get(0).getAccountNumber()).isEqualTo("6056********0123");
        assertThat(responses).extracting(LinkedAccountResponse::accountNumberLast4).containsExactly("0123", "0456");
        assertThat(responses).extracting(LinkedAccountResponse::institutionName).containsOnly("신한카드");
        verifyNoInteractions(codefBankAccountClient);
    }

    @Test
    @DisplayName("연동할 카드가 하나도 없으면 오류로 안내한다")
    void failsWhenNoCard() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefCardClient.fetchCards("cid-1", SHINHAN_CARD, "980225"))
                .thenReturn(new CardsResult(List.of(), null, null));

        assertThatThrownBy(() -> service.link(USER_ID, request(SHINHAN_CARD)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_NO_ELIGIBLE_ACCOUNT);
        verify(linkedAccountRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("보유카드 조회가 거절되면 기관 코드와 사유를 전달한다")
    void deliversCardListFailure() {
        givenUserAndExisting(List.of());
        when(codefAccountClient.register(any(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new CodefAccountResult("cid-1", null, null));
        when(codefCardClient.fetchCards("cid-1", SHINHAN_CARD, "980225"))
                .thenReturn(new CardsResult(List.of(), "CF-12801", "조회에 실패했습니다."));

        assertThatThrownBy(() -> service.link(USER_ID, request(SHINHAN_CARD)))
                .isInstanceOfSatisfying(AccountLinkRejectedException.class, e -> {
                    assertThat(e.getInstitutionCode()).isEqualTo("CF-12801");
                    assertThat(e.getInstitutionMessage()).isEqualTo("조회에 실패했습니다.");
                });
    }

    private LinkedAccount ownedAccount(Long id, String institutionCode) {
        User owner = User.builder().email("o@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        LinkedAccount account = LinkedAccount.builder().user(owner).institutionCode(institutionCode)
                .type(LinkedAccountType.BANK).connectedId("cid-1").build();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    @DisplayName("해제하면 CODEF 등록을 지우고 같은 기관의 항목만 모두 해제한다")
    void unlinkRemovesWholeInstitution() {
        LinkedAccount first = ownedAccount(10L, KOOKMIN);
        LinkedAccount second = ownedAccount(11L, KOOKMIN);
        LinkedAccount other = ownedAccount(12L, SHINHAN);
        when(linkedAccountRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(first));
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(first, second, other));
        when(codefAccountClient.unregister("cid-1", KOOKMIN, LinkedAccountType.BANK))
                .thenReturn(new CodefAccountResult("cid-1", null, null));

        service.unlink(USER_ID, 10L);

        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(first.isActive()).isFalse();
        assertThat(second.getDeletedAt()).isNotNull();
        assertThat(other.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("CODEF 삭제가 실패하면 DB는 그대로 두고 사유를 전달한다")
    void unlinkKeepsRowsWhenCodefFails() {
        LinkedAccount account = ownedAccount(10L, KOOKMIN);
        when(linkedAccountRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(account));
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(account));
        when(codefAccountClient.unregister(anyString(), anyString(), any()))
                .thenReturn(new CodefAccountResult(null, "CF-04000", "삭제에 실패했습니다."));

        assertThatThrownBy(() -> service.unlink(USER_ID, 10L))
                .isInstanceOf(AccountLinkRejectedException.class);
        assertThat(account.getDeletedAt()).isNull();
        verify(linkedAccountRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("다른 사용자의 계좌는 해제할 수 없다")
    void unlinkRejectsOtherUsersAccount() {
        when(linkedAccountRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(ownedAccount(10L, KOOKMIN)));

        assertThatThrownBy(() -> service.unlink(999L, 10L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_FORBIDDEN);
        verifyNoInteractions(codefAccountClient);
    }

    @Test
    @DisplayName("없거나 이미 해제된 계좌는 404로 안내한다")
    void unlinkRejectsMissingAccount() {
        when(linkedAccountRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(USER_ID, 10L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        verifyNoInteractions(codefAccountClient);
    }

    @Test
    @DisplayName("목록 조회는 응답에 connectedId와 전체 계좌번호를 담지 않는다")
    void listResponseHidesSecrets() {
        LinkedAccount account = LinkedAccount.builder().user(user).institutionCode(KOOKMIN)
                .type(LinkedAccountType.BANK).connectedId("secret-cid")
                .accountNumber("1002440001234").accountNumberLast4("1234").build();
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(account));

        List<LinkedAccountResponse> responses = service.getLinkedAccounts(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).toString()).doesNotContain("secret-cid").doesNotContain("1002440001234");
    }
}
