package com.dothis.fintris.category.repository;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    List<Category> findAllByOrderByDisplayOrderAsc();

    List<Category> findAllByCategoryTypeOrderByDisplayOrderAsc(CategoryType categoryType);
}
