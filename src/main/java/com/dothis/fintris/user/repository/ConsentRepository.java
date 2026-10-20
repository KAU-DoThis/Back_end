package com.dothis.fintris.user.repository;

import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Consent> findFirstByUser_IdAndItemOrderByCreatedAtDesc(Long userId, ConsentItem item);
}
