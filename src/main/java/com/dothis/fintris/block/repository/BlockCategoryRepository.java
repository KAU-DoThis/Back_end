package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.BlockCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockCategoryRepository extends JpaRepository<BlockCategory, Long> {

    List<BlockCategory> findAllByBlock_Id(Long blockId);

    Optional<BlockCategory> findByUser_IdAndCategory_Id(Long userId, Long categoryId);

    boolean existsByUser_IdAndCategory_Id(Long userId, Long categoryId);

    void deleteAllByBlock_Id(Long blockId);
}
