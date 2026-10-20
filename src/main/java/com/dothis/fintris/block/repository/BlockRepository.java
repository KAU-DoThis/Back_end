package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 삭제된 블록(deleted_at not null)은 조회에서 제외한다
public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId);

    List<Block> findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId, BlockType type);

    // 주인 확인(403)은 서비스에서 하므로 user 조건 없이 찾는다
    Optional<Block> findByIdAndDeletedAtIsNull(Long blockId);

    // 새 블록을 맨 뒤에 두기 위해 현재 가장 큰 display_order를 찾는다
    Optional<Block> findTopByUser_IdAndDeletedAtIsNullOrderByDisplayOrderDesc(Long userId);

    boolean existsByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(Long userId);
}
