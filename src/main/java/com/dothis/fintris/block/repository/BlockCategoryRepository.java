package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.BlockCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BlockCategoryRepository extends JpaRepository<BlockCategory, Long> {

    @EntityGraph(attributePaths = "category")
    List<BlockCategory> findAllByBlock_Id(Long blockId);

    // 목록 조회 시 블록마다 쿼리를 날리지 않도록 한 번에 가져온다
    @EntityGraph(attributePaths = "category")
    List<BlockCategory> findAllByBlock_IdIn(Collection<Long> blockIds);

    List<BlockCategory> findAllByUser_IdAndCategory_IdIn(Long userId, Collection<Long> categoryIds);

    // 바로 DELETE를 실행한다. 같은 트랜잭션에서 다시 INSERT해도 unique 충돌이 나지 않는다
    @Modifying
    @Query("delete from BlockCategory bc where bc.block.id = :blockId")
    void deleteAllByBlockId(@Param("blockId") Long blockId);
}
