package com.dothis.fintris.category.service;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorySeederTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategorySeeder seeder;

    private Category existing(String code, String name) {
        return Category.builder().code(code).name(name).categoryType(CategoryType.EXPENSE).displayOrder(1).build();
    }

    @SuppressWarnings("unchecked")
    private List<Category> saved() {
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("카테고리가 하나도 없으면 16개를 순서대로 넣는다")
    void seedsAllWhenEmpty() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        seeder.run(null);

        List<Category> saved = saved();
        assertThat(saved).hasSize(16);
        assertThat(saved.getFirst().getCode()).isEqualTo("FOOD");
        assertThat(saved.getFirst().getName()).isEqualTo("식비");
        assertThat(saved).extracting(Category::getDisplayOrder).isSorted();
        assertThat(saved).filteredOn(category -> category.getCategoryType() == CategoryType.TRANSFER)
                .extracting(Category::getCode)
                .containsExactly("DUTCH_PAY", "GIFT", "LOAN_OUT", "SELF_TRANSFER");
    }

    @Test
    @DisplayName("일부만 있으면 빠진 코드만 넣고, 있는 행(고친 이름 포함)은 건드리지 않는다")
    void seedsOnlyMissing() {
        when(categoryRepository.findAll()).thenReturn(List.of(existing("FOOD", "밥값"), existing("CAFE", "카페/간식")));

        seeder.run(null);

        List<Category> saved = saved();
        assertThat(saved).hasSize(14);
        assertThat(saved).extracting(Category::getCode).doesNotContain("FOOD", "CAFE");
    }

    @Test
    @DisplayName("16개가 다 있으면 아무것도 넣지 않는다")
    void noopWhenComplete() {
        when(categoryRepository.findAll()).thenReturn(CategorySeeder.SEEDS.stream()
                .map(seed -> existing(seed.code(), seed.name())).toList());

        seeder.run(null);

        verify(categoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코드 목록이 schema.sql 시드와 같다 (16개, 중복 없음)")
    void seedListMatchesSchema() {
        assertThat(CategorySeeder.SEEDS).extracting(CategorySeeder.Seed::code).doesNotHaveDuplicates()
                .containsExactly("FOOD", "DELIVERY_DINING", "CAFE", "TRANSPORT", "HOUSING", "TELECOM", "LIVING",
                        "SHOPPING", "LEISURE", "HEALTH", "SUBSCRIPTION", "ETC",
                        "DUTCH_PAY", "GIFT", "LOAN_OUT", "SELF_TRANSFER");
    }
}
