package com.dothis.fintris.user.repository;

import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 같은 초에 저장된 행은 createdAt이 같을 수 있어서 최신 순서는 id로 정한다
public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findAllByUser_IdOrderByIdDesc(Long userId);

    Optional<Consent> findFirstByUser_IdAndItemOrderByIdDesc(Long userId, ConsentItem item);
}
