package com.dothis.fintris.home.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.home.dto.HomeResponse;
import com.dothis.fintris.transaction.repository.BlockSpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.repository.TransactionTotals;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final Long USER_ID = 1L;

    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock BlockRepository blockRepository;
    @Mock BlockService blockService;
    @Mock WaitingFundService waitingFundService;
    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks HomeService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("오늘 쓸 수 있는 돈은 아침 잔액 기준으로 고정하고, 오늘 쓴 돈과 계좌별 이번 달 쓴 돈을 채운다")
    void dailyGuideAndMonthSpent() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        LinkedAccount bank = LinkedAccount.builder().user(user).institutionCode("0004").type(LinkedAccountType.BANK)
                .connectedId("cid-1").balance(1_000_000L).build();
        ReflectionTestUtils.setField(bank, "id", 3L);
        Block living = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(87_700L)
                .isDefault(true).startDate(today.withDayOfMonth(1)).dueDate(today).displayOrder(1).build();
        ReflectionTestUtils.setField(living, "id", 10L);

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(bank));
        when(waitingFundService.getWaitingFunds(USER_ID)).thenReturn(List.of());
        when(blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(living));
        when(transactionRepository.sumBlockSpent(eq(10L), any(), any())).thenReturn(new BlockSpent(12_300L, 12_300L));
        when(transactionRepository.sumTotals(eq(USER_ID), any(), any(), eq(3L), isNull()))
                .thenReturn(new TransactionTotals(115_900L, 0L, 0L, 0L));

        HomeResponse response = service.getHome(USER_ID);

        // 오늘이 기간 마지막 날이라 아침 잔액 100,000원이 오늘 가능 금액이다
        assertThat(response.dailyGuide().todayAvailable()).isEqualTo(100_000L);
        assertThat(response.dailyGuide().todaySpent()).isEqualTo(12_300L);
        assertThat(response.dailyGuide().todayRemaining()).isEqualTo(87_700L);
        assertThat(response.dailyGuide().blockBalance()).isEqualTo(87_700L);
        assertThat(response.accounts().getFirst().monthSpent()).isEqualTo(115_900L);
    }
}
