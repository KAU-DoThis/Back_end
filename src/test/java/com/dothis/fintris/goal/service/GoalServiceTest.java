package com.dothis.fintris.goal.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.dto.BlockDeleteResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.block.service.BlockStatusJudge;
import com.dothis.fintris.block.service.BlockStatusService;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.goal.code.GoalErrorCode;
import com.dothis.fintris.goal.dto.GoalAction;
import com.dothis.fintris.goal.dto.GoalListResponse;
import com.dothis.fintris.goal.dto.GoalResolutionRequest;
import com.dothis.fintris.goal.dto.GoalResolutionResponse;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Mock BlockRepository blockRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock BlockCategoryRepository blockCategoryRepository;
    @Mock BlockStatusService blockStatusService;
    @Mock BlockService blockService;
    @InjectMocks GoalService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        // 판정은 BlockStatusJudgeTest에서 본다. 여기서는 모두 안전으로 둔다
        lenient().when(blockStatusService.judgeAll(anyCollection())).thenAnswer(inv -> {
            Map<Long, BlockStatusJudge.Judgement> result = new LinkedHashMap<>();
            for (Block block : (Collection<Block>) inv.getArgument(0)) {
                result.put(block.getId(), BlockStatusJudge.Judgement.unknown());
            }
            return result;
        });
    }

    private Block goal(Long id, long balance, long target, LocalDate dueDate) {
        Block block = Block.builder().user(user).type(BlockType.GOAL).name("여행").balance(balance)
                .targetAmount(target).startDate(TODAY.minusMonths(6)).dueDate(dueDate)
                .rule(new BlockRule(null, null, 100_000L, null, null, null, null)).displayOrder(1).build();
        ReflectionTestUtils.setField(block, "id", id);
        lenient().when(blockRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(block));
        return block;
    }

    private Block spending(Long id, long balance) {
        Block block = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(balance)
                .startDate(TODAY.withDayOfMonth(1)).displayOrder(0).build();
        ReflectionTestUtils.setField(block, "id", id);
        lenient().when(blockRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(block));
        return block;
    }

    private GoalResolutionRequest request(GoalAction action) {
        return new GoalResolutionRequest(action, null, null, null);
    }

    private void assertError(Runnable call, Object code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("목표 목록: 지난 달 평균 확보액으로 예상 달성일과 늦어지는 일수를 낸다")
    void goals() {
        Block travel = goal(4L, 320_000, 800_000, TODAY.plusDays(30));
        when(blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(USER_ID, BlockType.GOAL))
                .thenReturn(List.of(travel));
        // 지난 3개월 동안 들어온 돈 330,000 - 나간 돈 30,000 = 300,000 → 월 평균 100,000
        when(blockTransferRepository.sumInSince(any(), any())).thenReturn(330_000L, 0L);
        when(blockTransferRepository.sumOutSince(any(), any())).thenReturn(30_000L, 0L);

        GoalListResponse.Goal result = service.getGoals(USER_ID).goals().get(0);

        // 남은 480,000원 ÷ 하루치(100,000 ÷ 30) → 144일 뒤
        assertThat(result.achievementRate()).isEqualTo(40);
        assertThat(result.currentPaceDate()).isEqualTo(TODAY.plusDays(144));
        assertThat(result.delayDays()).isEqualTo(114);
        assertThat(result.status()).isEqualTo(BlockStatus.SAFE);
    }

    @Test
    @DisplayName("달성: KEEP은 매달 넣는 금액을 0으로 멈춘다")
    void keep() {
        Block travel = goal(4L, 800_000, 800_000, TODAY.plusDays(10));

        GoalResolutionResponse response = service.resolve(USER_ID, 4L, request(GoalAction.KEEP));

        assertThat(travel.getRule().monthlyTarget()).isZero();
        assertThat(response.block().blockId()).isEqualTo(4L);
        assertThat(response.movedBlocks()).isEmpty();
    }

    @Test
    @DisplayName("달성: RAISE_TARGET은 목표를 올리고 월 확보 금액을 다시 계산한다. 지금 목표 이하면 막는다")
    void raiseTarget() {
        Block travel = goal(4L, 800_000, 800_000, TODAY.plusMonths(2));

        service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.RAISE_TARGET, null, 1_100_000L, null));

        assertThat(travel.getTargetAmount()).isEqualTo(1_100_000);
        // 남은 300,000원 ÷ 남은 3개월(이번 달 포함)
        assertThat(travel.getRule().monthlyTarget()).isEqualTo(100_000);
        goal(5L, 800_000, 800_000, TODAY.plusMonths(2));
        assertError(() -> service.resolve(USER_ID, 5L,
                new GoalResolutionRequest(GoalAction.RAISE_TARGET, null, 800_000L, null)),
                GoalErrorCode.GOAL_INVALID_RESOLUTION);
    }

    @Test
    @DisplayName("달성: MOVE는 다른 블록으로 옮기고 이동을 기록한다. 잔액보다 많으면 막는다")
    void move() {
        Block travel = goal(4L, 800_000, 800_000, TODAY.plusDays(10));
        Block living = spending(1L, 50_000);

        GoalResolutionResponse response = service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.MOVE,
                null, null, List.of(new GoalResolutionRequest.Move(1L, 300_000L))));

        assertThat(travel.getBalance()).isEqualTo(500_000);
        assertThat(living.getBalance()).isEqualTo(350_000);
        assertThat(response.movedBlocks()).extracting(b -> b.blockId()).containsExactly(1L);
        ArgumentCaptor<BlockTransfer> transfer = ArgumentCaptor.forClass(BlockTransfer.class);
        verify(blockTransferRepository).save(transfer.capture());
        assertThat(transfer.getValue().getAmount()).isEqualTo(300_000);

        // 이제 달성 상태가 아니므로 다시 옮기면 막는다
        assertError(() -> service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.MOVE, null, null,
                List.of(new GoalResolutionRequest.Move(1L, 10_000L)))), GoalErrorCode.GOAL_ACTION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("MOVE 금액 합이 잔액보다 크면 아무것도 옮기지 않는다")
    void moveOverBalance() {
        Block travel = goal(4L, 800_000, 800_000, TODAY.plusDays(10));
        spending(1L, 0);

        assertError(() -> service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.MOVE, null, null,
                List.of(new GoalResolutionRequest.Move(1L, 500_000L), new GoalResolutionRequest.Move(1L, 400_000L)))),
                BlockErrorCode.BLOCK_INSUFFICIENT_AMOUNT);
        assertThat(travel.getBalance()).isEqualTo(800_000);
    }

    @Test
    @DisplayName("기한 지남: EXTEND는 기한을 늘리고 남은 금액 ÷ 남은 개월로 월 확보 금액을 다시 정한다")
    void extend() {
        Block travel = goal(4L, 200_000, 800_000, TODAY.minusDays(1));
        LocalDate newDue = TODAY.plusMonths(2);

        service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.EXTEND, newDue, null, null));

        assertThat(travel.getDueDate()).isEqualTo(newDue);
        assertThat(travel.getRule().monthlyTarget()).isEqualTo(200_000); // 600,000 ÷ 3
        // 기한이 없거나 과거면 막는다
        Block other = goal(5L, 0, 800_000, TODAY.minusDays(1));
        assertError(() -> service.resolve(USER_ID, 5L, request(GoalAction.EXTEND)),
                GoalErrorCode.GOAL_INVALID_RESOLUTION);
        assertError(() -> service.resolve(USER_ID, 5L,
                new GoalResolutionRequest(GoalAction.EXTEND, TODAY.minusDays(3), null, null)),
                BlockErrorCode.BLOCK_INVALID_DUE_DATE);
        assertThat(other.getDueDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("기한 지남: FINISH_AT_CURRENT는 목표를 모은 금액으로 바꾼다. 모은 돈이 없으면 막는다")
    void finishAtCurrent() {
        Block travel = goal(4L, 350_000, 800_000, TODAY.minusDays(1));

        service.resolve(USER_ID, 4L, request(GoalAction.FINISH_AT_CURRENT));

        assertThat(travel.getTargetAmount()).isEqualTo(350_000);
        assertThat(travel.getRule().monthlyTarget()).isZero();
        goal(5L, 0, 800_000, TODAY.minusDays(1));
        assertError(() -> service.resolve(USER_ID, 5L, request(GoalAction.FINISH_AT_CURRENT)),
                GoalErrorCode.GOAL_NOTHING_SAVED);
    }

    @Test
    @DisplayName("기한 지남: CLEANUP은 일부를 옮긴 뒤 블록을 삭제한다 (남은 돈은 삭제 처리에서 나눌 돈으로)")
    void cleanup() {
        Block travel = goal(4L, 350_000, 800_000, TODAY.minusDays(1));
        Block living = spending(1L, 0);
        when(blockService.deleteBlock(USER_ID, 4L)).thenAnswer(inv -> {
            long returned = travel.delete(java.time.LocalDateTime.now());
            return new BlockDeleteResponse(4L, returned, null);
        });

        GoalResolutionResponse response = service.resolve(USER_ID, 4L, new GoalResolutionRequest(GoalAction.CLEANUP,
                null, null, List.of(new GoalResolutionRequest.Move(1L, 100_000L))));

        assertThat(living.getBalance()).isEqualTo(100_000);
        assertThat(response.block()).isNull();
        assertThat(response.movedBlocks()).extracting(b -> b.blockId()).containsExactly(1L);
        verify(blockService).deleteBlock(USER_ID, 4L);
    }

    @Test
    @DisplayName("상태에 맞지 않는 처리와 목표형이 아닌 블록은 막는다")
    void notAllowed() {
        goal(4L, 300_000, 800_000, TODAY.plusDays(30)); // 진행 중
        goal(5L, 800_000, 800_000, TODAY.plusDays(30)); // 달성
        spending(1L, 100_000);

        assertError(() -> service.resolve(USER_ID, 4L, request(GoalAction.KEEP)), GoalErrorCode.GOAL_ACTION_NOT_ALLOWED);
        assertError(() -> service.resolve(USER_ID, 4L, request(GoalAction.REMIND_LATER)),
                GoalErrorCode.GOAL_ACTION_NOT_ALLOWED);
        assertError(() -> service.resolve(USER_ID, 5L, request(GoalAction.CLEANUP)),
                GoalErrorCode.GOAL_ACTION_NOT_ALLOWED);
        assertError(() -> service.resolve(USER_ID, 1L, request(GoalAction.KEEP)), GoalErrorCode.GOAL_NOT_GOAL_BLOCK);
        verify(blockService, never()).deleteBlock(any(), any());
    }
}
