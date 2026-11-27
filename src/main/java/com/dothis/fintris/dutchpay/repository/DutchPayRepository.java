package com.dothis.fintris.dutchpay.repository;

import com.dothis.fintris.dutchpay.entity.DutchPay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DutchPayRepository extends JpaRepository<DutchPay, Long> {

    // 수정·해제: 같은 더치페이를 동시에 고치지 못하게 행을 잠그고 version을 올린다
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select d from DutchPay d where d.id = :id")
    Optional<DutchPay> findForUpdate(@Param("id") Long id);
}
