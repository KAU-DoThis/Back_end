package com.dothis.fintris.dutchpay.repository;

import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DutchPayTransactionRepository extends JpaRepository<DutchPayTransaction, Long> {

    List<DutchPayTransaction> findAllByDutchPay_Id(Long dutchPayId);

    // 이미 다른 더치페이에 들어간 거래를 고르지 못하게 막을 때 쓴다. 수정 때는 자기 묶음 것을 빼고 본다
    List<DutchPayTransaction> findAllByTransaction_IdIn(Collection<Long> transactionIds);
}
