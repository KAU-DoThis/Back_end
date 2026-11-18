package com.dothis.fintris.devtools;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.devtools.DemoTransactionFile.DemoTransaction;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 개발 서버 전용: 시연용 3개월 거래(197건)를 로그인한 계정의 테스트 계좌에 넣는다
// 블록에는 반영하지 않는다. 블록 확정 전 거래라 반영 규칙상 원래 빠지지 않는 거래다 (확정 전에 넣는다)
// 은행·카드 거래가 섞여 있지만 모두 테스트 은행 계좌에 붙인다. 가짜 카드를 만들면 정기 동기화가 CODEF를 불러 오류 상태가 된다
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-tools.enabled", havingValue = "true")
public class DevDemoService {

    static final String KEY_PREFIX = "DEMO-";

    private final DevToolsService devToolsService;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    // 이미 넣은 거래(같은 시연 번호)는 건너뛰어서 여러 번 눌러도 중복되지 않는다
    @Transactional
    public DevDemoTransactionsResponse loadDemoTransactions(Long userId) {
        LinkedAccount account = devToolsService.testAccount(userId);
        List<DemoTransaction> demo = DemoTransactionFile.readClasspath(objectMapper);

        // 처음 배포한 키(DEMO-n)로 이미 넣은 계정도 있어 두 형식 모두 확인한다
        List<String> candidates = new ArrayList<>();
        demo.forEach(transaction -> {
            candidates.add(key(userId, transaction));
            candidates.add(legacyKey(transaction));
        });
        Set<String> existing = new HashSet<>(transactionRepository.findExistingExternalKeys(userId, candidates));
        Map<String, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCode, Function.identity()));

        List<Transaction> fresh = demo.stream()
                .filter(transaction -> !existing.contains(key(userId, transaction))
                        && !existing.contains(legacyKey(transaction)))
                .map(transaction -> toEntity(account, transaction, categories, key(userId, transaction)))
                .toList();
        transactionRepository.saveAll(fresh);
        return new DevDemoTransactionsResponse(account.getId(), fresh.size(), demo.size() - fresh.size());
    }

    // 사용자 ID를 넣는다. DB에 external_key 하나만으로 건 예전 유일 제약이 남아 있으면 계정끼리 같은 키가 겹쳐 실패한다
    static String key(Long userId, DemoTransaction transaction) {
        return KEY_PREFIX + userId + "-" + transaction.id();
    }

    private static String legacyKey(DemoTransaction transaction) {
        return KEY_PREFIX + transaction.id();
    }

    private static Transaction toEntity(LinkedAccount account, DemoTransaction demo, Map<String, Category> categories,
                                        String externalKey) {
        Category category = demo.categoryCode() == null ? null : categories.get(demo.categoryCode());
        return Transaction.builder()
                .user(account.getUser())
                .linkedAccount(account)
                .occurredAt(demo.occurredAt())
                .amount(demo.amount())
                .direction(TransactionDirection.valueOf(demo.direction()))
                .merchantOrPayee(demo.merchantOrPayee())
                .merchantType(demo.merchantType())
                .source(TransactionSource.CODEF)
                .status(TransactionStatus.valueOf(demo.status()))
                .category(category)
                .confidence(demo.confidence())
                .classifiedBy(ClassifiedBy.AUTO)
                .excluded(demo.excluded())
                .isCanceled(demo.canceled())
                .externalKey(externalKey)
                .build();
    }
}
