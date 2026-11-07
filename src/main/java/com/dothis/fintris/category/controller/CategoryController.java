package com.dothis.fintris.category.controller;

import com.dothis.fintris.category.dto.CategoryResponse;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.service.CategoryService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Category", description = "카테고리 (F-20)")
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록", description = "카테고리 수정·빠른 입력 화면용. type을 주면 그 유형만 돌려준다")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(@RequestParam(required = false) CategoryType type) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, categoryService.getCategories(type));
    }
}
