package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 삭제된 블록(deleted_at not null)은 조회에서 제외한다
public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId);

    Optional<Block> findByIdAndUser_IdAndDeletedAtIsNull(Long blockId, Long userId);

    boolean existsByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(Long userId);
}
