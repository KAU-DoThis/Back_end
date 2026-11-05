package com.dothis.fintris.block.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockDeleteResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockServiceDeleteTest {

    private static final Long USER_ID = 1L;
    private static final Long BLOCK_ID = 3L;

    @Mock BlockRepository blockRepository;
    @Mock BlockCategoryRepository blockCategoryRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserRepository userRepository;
    @Mock WaitingFundRepository waitingFundRepository;
    @InjectMocks BlockService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void 남은_잔액은_남은_돈_나눌_돈으로_돌아간다() {
        Block block = block(240_000, false);
        when(blockRepository.findByIdAndDeletedAtIsNull(BLOCK_ID)).thenReturn(Optional.of(block));
        when(waitingFundRepository.save(any(WaitingFund.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockDeleteResponse response = service.deleteBlock(USER_ID, BLOCK_ID);

        assertThat(response.returnedAmount()).isEqualTo(240_000);
        assertThat(response.waitingFund().source()).isEqualTo(WaitingFundSource.OTHER);
        assertThat(response.waitingFund().label()).isEqualTo("남은 돈");
        assertThat(response.waitingFund().amount()).isEqualTo(240_000);
        assertThat(response.waitingFund().originalAmount()).isEqualTo(240_000);
        assertThat(block.getBalance()).isZero();
        assertThat(block.getDeletedAt()).isNotNull();

        // 이동 기록: 블록 → 나눌 돈
        ArgumentCaptor<BlockTransfer> transfer = ArgumentCaptor.forClass(BlockTransfer.class);
        verify(blockTransferRepository).save(transfer.capture());
        assertThat(transfer.getValue().getFromBlock()).isSameAs(block);
        assertThat(transfer.getValue().getToBlock()).isNull();
        assertThat(transfer.getValue().getToWaitingFund().getAmount()).isEqualTo(240_000);
        assertThat(transfer.getValue().getAmount()).isEqualTo(240_000);
    }

    @Test
    void 잔액이_0이면_나눌_돈을_만들지_않는다() {
        when(blockRepository.findByIdAndDeletedAtIsNull(BLOCK_ID)).thenReturn(Optional.of(block(0, false)));

        BlockDeleteResponse response = service.deleteBlock(USER_ID, BLOCK_ID);

        assertThat(response.returnedAmount()).isZero();
        assertThat(response.waitingFund()).isNull();
        verify(waitingFundRepository, never()).save(any());
        verify(blockTransferRepository, never()).save(any());
    }

    @Test
    void 기본_블록은_삭제할_수_없다() {
        when(blockRepository.findByIdAndDeletedAtIsNull(BLOCK_ID)).thenReturn(Optional.of(block(950_000, true)));

        assertThatThrownBy(() -> service.deleteBlock(USER_ID, BLOCK_ID))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(BlockErrorCode.BLOCK_DEFAULT_UNDELETABLE);
        verify(waitingFundRepository, never()).save(any());
    }

    private Block block(long balance, boolean isDefault) {
        Block block = Block.builder()
                .user(user)
                .type(BlockType.GOAL)
                .name("여행")
                .balance(balance)
                .isDefault(isDefault)
                .targetAmount(800_000L)
                .startDate(LocalDate.of(2026, 10, 1))
                .dueDate(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(block, "id", BLOCK_ID);
        return block;
    }
}
