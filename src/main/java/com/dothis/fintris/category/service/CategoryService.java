package com.dothis.fintris.category.service;

import com.dothis.fintris.category.dto.CategoryResponse;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // type이 없으면 소비·이체 유형을 모두 표시 순서대로 돌려준다
    public List<CategoryResponse> getCategories(CategoryType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllByOrderByDisplayOrderAsc()
                : categoryRepository.findAllByCategoryTypeOrderByDisplayOrderAsc(type);
        return categories.stream().map(CategoryResponse::from).toList();
    }
}
