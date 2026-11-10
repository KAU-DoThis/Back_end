package com.dothis.fintris.transaction.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.home.service.HomeService;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.ManualPreviewResponse;
import com.dothis.fintris.transaction.dto.ManualSaveRequest;
import com.dothis.fintris.transaction.dto.ManualSaveResponse;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.service.ManualInputParser.Parsed;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

// 빠른 입력 (10, F-26). 현금처럼 연동 계좌에 안 잡히는 지출을 직접 넣는다
// 지금은 규칙으로 금액·내용·날짜만 읽는다. AI 해석(카테고리 추천)은 AI 내부 API가 정해지면 미리보기에 붙인다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualTransactionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime PAST_DAY_TIME = LocalTime.NOON; // 지난 날짜는 시각을 몰라 정오로 둔다

    private final TransactionRepository transactionRepository;
    private final BlockRepository blockRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BlockTargetResolver blockTargetResolver;
    private final BlockReflector blockReflector;
    private final HomeService homeService;

    // 저장하지 않고 해석 결과와 차감될 블록만 보여 준다. 금액을 못 읽으면 400 MANUAL_PARSE_FAILED
    public ManualPreviewResponse preview(Long userId, String text) {
        LocalDate today = LocalDate.now(KST);
        Parsed parsed = ManualInputParser.parse(text, today)
                .orElseThrow(() -> GeneralException.of(TransactionErrorCode.MANUAL_PARSE_FAILED));
        Transaction draft = draft(userId, parsed.amount(), parsed.description(), occurredAt(parsed.occurredDate(), today),
                null);
        return ManualPreviewResponse.of(parsed.amount(), parsed.description(), parsed.occurredDate(), null,
                targetBlock(userId, draft, null));
    }

    // 확정 거래로 저장하고 바로 블록에서 뺀다. 응답에 홈과 같은 오늘 남은 금액을 붙인다
    @Transactional
    public ManualSaveResponse save(Long userId, ManualSaveRequest request) {
        LocalDate today = LocalDate.now(KST);
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> GeneralException.of(TransactionErrorCode.TRANSACTION_CATEGORY_NOT_FOUND));

        Transaction transaction = transactionRepository.save(draft(userId, request.amount(), request.description(),
                occurredAt(request.occurredDate(), today), category));
        blockReflector.reconcile(transaction, blockReflector.blocksConfirmedAt(userId).orElse(null));

        return new ManualSaveResponse(TransactionResponse.from(transaction), homeService.todayRemaining(userId));
    }

    private Transaction draft(Long userId, long amount, String description, LocalDateTime occurredAt, Category category) {
        return Transaction.builder()
                .user(userRepository.getReferenceById(userId))
                .occurredAt(occurredAt)
                .amount(amount)
                .direction(TransactionDirection.OUT)
                .merchantOrPayee(description)
                .source(TransactionSource.MANUAL)
                .status(TransactionStatus.CONFIRMED)
                .category(category)
                .classifiedBy(category == null ? null : ClassifiedBy.USER)
                .build();
    }

    // 오늘이면 지금 시각, 지난 날짜면 정오
    private LocalDateTime occurredAt(LocalDate date, LocalDate today) {
        return date.equals(today) ? LocalDateTime.now(KST) : date.atTime(PAST_DAY_TIME);
    }

    // 블록 반영과 같은 규칙으로 고른다 (고정지출 조건 → 카테고리 연결 블록 → 기본 블록)
    private Block targetBlock(Long userId, Transaction draft, Block categoryBlock) {
        List<Block> blocks = blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);
        return blockTargetResolver.resolve(draft, blocks, categoryBlock).orElse(null);
    }
}
