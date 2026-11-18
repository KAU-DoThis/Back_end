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
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.OnboardingStep;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.user.repository.UserSettingRepository;
import com.dothis.fintris.user.service.UserService;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockProposalServiceTest {

    private static final Long USER_ID = 1L;

    @Mock BlockRepository blockRepository;
    @Mock BlockCategoryRepository blockCategoryRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock WaitingFundRepository waitingFundRepository;
    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock UserRepository userRepository;
    @Mock UserSettingRepository userSettingRepository;
    @Mock UserService userService;
    @Mock TransactionRepository transactionRepository;

    private BlockProposalService service;
    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private UserSetting setting;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        // 블록 생성 규칙은 실제 BlockService 것을 그대로 쓴다
        BlockService blockService = new BlockService(blockRepository, blockCategoryRepository,
                blockTransferRepository, categoryRepository, userRepository, waitingFundRepository);
        service = new BlockProposalService(blockService, userService, blockRepository, blockTransferRepository,
                waitingFundRepository, linkedAccountRepository, userRepository, userSettingRepository,
                transactionRepository, new RecurringPaymentDetector());

        setting = UserSetting.builder().user(user).tutorialDone(true).blocksConfirmed(false).build();
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(userSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));
        lenient().when(blockRepository.findTopByUser_IdAndDeletedAtIsNullOrderByDisplayOrderDesc(USER_ID))
                .thenReturn(Optional.empty());
        lenient().when(waitingFundRepository.save(any(WaitingFund.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userService.getOnboardingStep(USER_ID)).thenReturn(OnboardingStep.DONE);
    }

    @Test
    void 매달_나간_출금을_고정지출로_제안하고_생활비는_남은_돈으로_잡는다() {
        linkedBalance(2_000_000);
        LocalDate thisMonth = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);
        when(transactionRepository.findOutgoingSince(any(), any())).thenReturn(List.of(
                out(thisMonth.minusMonths(3).withDayOfMonth(25), 600_000),
                out(thisMonth.minusMonths(2).withDayOfMonth(25), 620_000),
                out(thisMonth.minusMonths(1).withDayOfMonth(25), 600_000)));

        BlockProposalResponse response = service.getProposals(USER_ID);

        assertThat(response.summary().recurringCount()).isEqualTo(1);
        assertThat(response.proposals()).extracting(BlockProposalResponse.Proposal::type)
                .containsExactly(BlockType.FIXED, BlockType.SPENDING);
        BlockProposalResponse.Proposal fixed = response.proposals().get(0);
        assertThat(fixed.balance()).isEqualTo(600_000);
        assertThat(fixed.rule().payDay()).isEqualTo(25);
        assertThat(fixed.rule().matchCondition().amountMode()).isEqualTo(BlockRule.AmountMode.RANGE);
        assertThat(fixed.rule().matchCondition().minAmount()).isEqualTo(540_000);
        assertThat(fixed.rule().matchCondition().maxAmount()).isEqualTo(660_000);
        assertThat(fixed.foundFrom()).isEqualTo("TRANSACTIONS");
        assertThat(response.proposals().get(1).balance()).isEqualTo(1_400_000);
        assertThat(response.summary().unallocated()).isZero();
    }

    @Test
    void 구성안을_잔액과_함께_만들고_남은_돈은_나눌_돈으로_둔다() {
        // 시안 04: 연동 잔액 4,920,000 중 4,800,000을 블록에 나눔 → 120,000 남음
        linkedBalance(4_920_000);
        BlockProposalConfirmRequest request = new BlockProposalConfirmRequest(List.of(
                item(BlockType.FIXED, "월세", 600_000, new BlockRule(null, null, null, 25, 600_000L, null, null)),
                item(BlockType.SPENDING, "생활비", 1_400_000, new BlockRule(1_400_000L, 1, null, null, null, null, null)),
                item(BlockType.FIXED, "통신비", 55_000, new BlockRule(null, null, null, 15, 55_000L, null, null)),
                item(BlockType.SPENDING, "용돈", 2_745_000, null)));

        BlockProposalConfirmResponse response = service.confirm(USER_ID, request);

        assertThat(response.blocks()).extracting(BlockResponse::name).containsExactly("월세", "생활비", "통신비", "용돈");
        assertThat(response.blocks()).extracting(BlockResponse::balance)
                .containsExactly(600_000L, 1_400_000L, 55_000L, 2_745_000L);
        assertThat(response.blocks()).extracting(BlockResponse::displayOrder).containsExactly(0, 1, 2, 3);
        // isDefault를 안 보내면 첫 사용형 블록이 기본
        assertThat(response.blocks()).extracting(BlockResponse::isDefault).containsExactly(false, true, false, false);
        assertThat(response.blocks().get(0).createdBy()).isEqualTo(BlockCreatedBy.AI);

        assertThat(response.waitingFunds()).hasSize(1);
        assertThat(response.waitingFunds().get(0).source()).isEqualTo(WaitingFundSource.OTHER);
        assertThat(response.waitingFunds().get(0).amount()).isEqualTo(120_000);
        assertThat(setting.isBlocksConfirmed()).isTrue();
        assertThat(response.onboardingStep()).isEqualTo(OnboardingStep.DONE);
    }

    @Test
    void 남는_돈이_없으면_나눌_돈을_만들지_않는다() {
        linkedBalance(1_500_000);

        BlockProposalConfirmResponse response = service.confirm(USER_ID, new BlockProposalConfirmRequest(List.of(
                item(BlockType.SPENDING, "생활비", 1_500_000, null))));

        assertThat(response.waitingFunds()).isEmpty();
        verify(waitingFundRepository, never()).save(any());
    }

    @Test
    void 블록_합계가_연동_잔액보다_크면_확정하지_않는다() {
        linkedBalance(1_000_000);

        assertThatThrownBy(() -> service.confirm(USER_ID, new BlockProposalConfirmRequest(List.of(
                item(BlockType.SPENDING, "생활비", 1_500_000, null)))))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(BlockErrorCode.BLOCK_INSUFFICIENT_FUNDS);
        verify(blockRepository, never()).saveAll(any());
        assertThat(setting.isBlocksConfirmed()).isFalse();
    }

    @Test
    void 이미_확정했으면_다시_확정하지_않는다() {
        setting.confirmBlocks();

        assertThatThrownBy(() -> service.confirm(USER_ID, new BlockProposalConfirmRequest(List.of(
                item(BlockType.SPENDING, "생활비", 0, null)))))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(BlockErrorCode.BLOCK_PROPOSAL_ALREADY_CONFIRMED);
    }

    @Test
    void 사용형_블록이_없으면_기본_블록을_정할_수_없다() {
        assertThatThrownBy(() -> service.confirm(USER_ID, new BlockProposalConfirmRequest(List.of(
                item(BlockType.FIXED, "월세", 0, new BlockRule(null, null, null, 25, 600_000L, null, null))))))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(BlockErrorCode.BLOCK_INVALID_DEFAULT);
    }

    @Test
    void 미리보기는_요약과_사용형_하루_금액을_계산하고_생활비가_3달_평균보다_적으면_경고한다() {
        linkedBalance(2_000_000);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 월세 3번(고정지출이라 평균에서 뺌) + 최근 3달 생활비 지출 합 3,600,000 → 월 평균 1,200,000
        when(transactionRepository.findOutgoingSince(any(), any())).thenReturn(List.of(
                out(today.withDayOfMonth(1).minusMonths(3).withDayOfMonth(25), 600_000),
                out(today.withDayOfMonth(1).minusMonths(2).withDayOfMonth(25), 600_000),
                out(today.withDayOfMonth(1).minusMonths(1).withDayOfMonth(25), 600_000),
                spend(today.minusDays(10), 1_800_000),
                spend(today.minusDays(40), 1_800_000)));

        BlockProposalPreviewResponse response = service.preview(USER_ID, new BlockProposalPreviewRequest(List.of(
                new BlockProposalPreviewRequest.Item("fixed-1", BlockType.FIXED, 600_000L, null, null),
                new BlockProposalPreviewRequest.Item("spending-default", BlockType.SPENDING, 1_500_000L,
                        new BlockRule(1_000_000L, 1, null, null, null, null, null), true))));

        assertThat(response.linkedBalance()).isEqualTo(2_000_000);
        assertThat(response.allocated()).isEqualTo(2_100_000);
        assertThat(response.unallocated()).isEqualTo(-100_000);
        assertThat(response.blocks().get(0)).isEqualTo(new BlockProposalPreviewResponse.Block("fixed-1", null, null));
        BlockProposalPreviewResponse.Block living = response.blocks().get(1);
        assertThat(living.proposalKey()).isEqualTo("spending-default");
        assertThat(living.dailyApprox()).isEqualTo(1_000_000 / today.lengthOfMonth() / 100 * 100);
        assertThat(living.warning()).isEqualTo("최근 3달 평균보다 200,000원 적어요");
    }

    @Test
    void 거래가_없으면_3달_평균이_없어_경고하지_않는다() {
        linkedBalance(1_000_000);

        BlockProposalPreviewResponse response = service.preview(USER_ID, new BlockProposalPreviewRequest(List.of(
                new BlockProposalPreviewRequest.Item("spending-default", BlockType.SPENDING, 100_000L, null, null))));

        assertThat(response.blocks().get(0).warning()).isNull();
        assertThat(response.unallocated()).isEqualTo(900_000);
    }

    private Transaction spend(LocalDate date, long amount) {
        return Transaction.builder().user(user).occurredAt(date.atTime(12, 0)).amount(amount)
                .direction(TransactionDirection.OUT).merchantOrPayee("배달의민족").build();
    }

    private void linkedBalance(long balance) {
        LinkedAccount account = LinkedAccount.builder()
                .user(user).institutionCode("0004").type(LinkedAccountType.BANK).connectedId("x").balance(balance)
                .build();
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(account));
    }

    private Transaction out(LocalDate date, long amount) {
        return Transaction.builder().user(user).occurredAt(date.atTime(9, 0)).amount(amount)
                .direction(TransactionDirection.OUT).merchantOrPayee("김*수").build();
    }

    private BlockProposalConfirmRequest.Item item(BlockType type, String name, long balance, BlockRule rule) {
        LocalDate monthEnd = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1).plusMonths(1).minusDays(1);
        return new BlockProposalConfirmRequest.Item(type, name, null, null, balance, null,
                type == BlockType.SPENDING ? monthEnd : null, rule, null, BlockCreatedBy.AI);
    }
}
