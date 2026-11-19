package com.dothis.fintris.category.service;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 카테고리 기본 데이터(16개)를 앱이 뜰 때 채운다.
// 서버 DB는 update 모드라 테이블만 생기고 schema.sql의 시드는 들어가지 않는다. 그래서 서버에 카테고리가 하나도 없었다 (#79)
// 없는 코드만 넣고 이미 있는 행(이름을 고친 것 포함)은 건드리지 않는다. 몇 번을 다시 떠도 결과가 같다
// 목록은 schema.sql 시드, AI 연동 명세 4절과 같아야 한다. 바꾸면 AI 담당에게도 알린다
@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    record Seed(String code, String name, CategoryType type, int displayOrder) {
    }

    static final List<Seed> SEEDS = List.of(
            new Seed("FOOD", "식비", CategoryType.EXPENSE, 1),
            new Seed("DELIVERY_DINING", "배달/외식", CategoryType.EXPENSE, 2),
            new Seed("CAFE", "카페/간식", CategoryType.EXPENSE, 3),
            new Seed("TRANSPORT", "교통", CategoryType.EXPENSE, 4),
            new Seed("HOUSING", "주거/관리비", CategoryType.EXPENSE, 5),
            new Seed("TELECOM", "통신", CategoryType.EXPENSE, 6),
            new Seed("LIVING", "생활/생필품", CategoryType.EXPENSE, 7),
            new Seed("SHOPPING", "쇼핑/의류", CategoryType.EXPENSE, 8),
            new Seed("LEISURE", "문화/여가", CategoryType.EXPENSE, 9),
            new Seed("HEALTH", "의료/건강", CategoryType.EXPENSE, 10),
            new Seed("SUBSCRIPTION", "구독료", CategoryType.EXPENSE, 11),
            new Seed("ETC", "기타", CategoryType.EXPENSE, 12),
            new Seed("DUTCH_PAY", "더치페이", CategoryType.TRANSFER, 13),
            new Seed("GIFT", "선물/경조사", CategoryType.TRANSFER, 14),
            new Seed("LOAN_OUT", "빌려준 돈", CategoryType.TRANSFER, 15),
            new Seed("SELF_TRANSFER", "자기 계좌 간 이동", CategoryType.TRANSFER, 16));

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existing = categoryRepository.findAll().stream()
                .map(Category::getCode)
                .collect(Collectors.toSet());
        List<Category> missing = SEEDS.stream()
                .filter(seed -> !existing.contains(seed.code()))
                .<Category>map(seed -> Category.builder()
                        .code(seed.code())
                        .name(seed.name())
                        .categoryType(seed.type())
                        .displayOrder(seed.displayOrder())
                        .build())
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        categoryRepository.saveAll(missing);
        log.info("없던 카테고리 기본 데이터를 넣었습니다: {}개 {}", missing.size(),
                missing.stream().map(Category::getCode).toList());
    }
}
