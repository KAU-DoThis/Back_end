package com.dothis.fintris.devtools;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevToolsServiceTest {

    private static final Long USER_ID = 1L;

    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DevToolsService devToolsService;

    @Test
    @DisplayName("테스트 계좌가 없으면 계좌번호 없는 가짜 은행 계좌를 만든다 (동기화가 CODEF를 부르지 않게)")
    void createsWhenMissing() {
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(linkedAccountRepository.save(any(LinkedAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DevTestAccountResponse response = devToolsService.setTestAccount(USER_ID, 3_000_000);

        ArgumentCaptor<LinkedAccount> saved = ArgumentCaptor.forClass(LinkedAccount.class);
        verify(linkedAccountRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(LinkedAccountType.BANK);
        assertThat(saved.getValue().getAccountNumber()).isNull();
        assertThat(saved.getValue().getBalance()).isEqualTo(3_000_000L);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.created()).isTrue();
    }

    @Test
    @DisplayName("테스트 계좌가 있으면 새로 만들지 않고 잔액만 바꾼다")
    void updatesExisting() {
        LinkedAccount existing = LinkedAccount.builder()
                .id(5L)
                .accountName(DevToolsService.TEST_ACCOUNT_NAME)
                .type(LinkedAccountType.BANK)
                .balance(3_000_000L)
                .build();
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(existing));

        DevTestAccountResponse response = devToolsService.setTestAccount(USER_ID, 2_500_000);

        assertThat(existing.getBalance()).isEqualTo(2_500_000L);
        assertThat(response).isEqualTo(new DevTestAccountResponse(5L, 2_500_000, false));
        verify(linkedAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("실제 연동 계좌(계좌번호 있음)는 이름이 같아도 건드리지 않는다")
    void ignoresRealAccount() {
        LinkedAccount real = LinkedAccount.builder()
                .id(7L)
                .accountName(DevToolsService.TEST_ACCOUNT_NAME)
                .accountNumber("12345678901234")
                .type(LinkedAccountType.BANK)
                .balance(100L)
                .build();
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(real));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(linkedAccountRepository.save(any(LinkedAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        devToolsService.setTestAccount(USER_ID, 3_000_000);

        assertThat(real.getBalance()).isEqualTo(100L);
        verify(linkedAccountRepository).save(any(LinkedAccount.class));
    }
}
