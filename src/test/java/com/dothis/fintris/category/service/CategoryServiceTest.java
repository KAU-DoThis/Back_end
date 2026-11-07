package com.dothis.fintris.category.service;

import com.dothis.fintris.category.dto.CategoryResponse;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService service;

    private Category category(long id, String code, String name, CategoryType type, int order) {
        Category category = Category.builder().code(code).name(name).categoryType(type).displayOrder(order).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Test
    @DisplayName("유형을 주지 않으면 전체를 표시 순서대로 돌려준다")
    void all() {
        when(categoryRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                category(3L, "CAFE", "카페/간식", CategoryType.EXPENSE, 3),
                category(13L, "DUTCH_PAY", "더치페이", CategoryType.TRANSFER, 13)));

        List<CategoryResponse> result = service.getCategories(null);

        assertThat(result).containsExactly(
                new CategoryResponse(3L, "CAFE", "카페/간식", CategoryType.EXPENSE),
                new CategoryResponse(13L, "DUTCH_PAY", "더치페이", CategoryType.TRANSFER));
    }

    @Test
    @DisplayName("유형을 주면 그 유형만 조회한다")
    void byType() {
        when(categoryRepository.findAllByCategoryTypeOrderByDisplayOrderAsc(CategoryType.EXPENSE)).thenReturn(List.of(
                category(3L, "CAFE", "카페/간식", CategoryType.EXPENSE, 3)));

        List<CategoryResponse> result = service.getCategories(CategoryType.EXPENSE);

        assertThat(result).extracting(CategoryResponse::code).containsExactly("CAFE");
        verify(categoryRepository, never()).findAllByOrderByDisplayOrderAsc();
    }
}
