package com.dothis.fintris.devtools;

import com.dothis.fintris.user.repository.UserSettingRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 개발 서버 시연용: 블록 구성(04) 전으로 되돌려 같은 계정으로 시연을 다시 할 수 있게 한다
// 블록·이동 기록·나눌 돈·블록 확정 여부만 지운다. 연동 계좌(잔액)·거래(시연 거래 포함)·동의·튜토리얼은 그대로 둔다
// 블록을 가리키는 행(거래의 블록 반영, 이동 기록, 카테고리 연결, 초불이 제안)을 먼저 정리하고 블록을 지운다
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-tools.enabled", havingValue = "true")
public class DevOnboardingResetService {

    private static final String USER_BLOCKS = "select b.id from Block b where b.user.id = :userId";
    private static final String USER_FUNDS = "select w.id from WaitingFund w where w.user.id = :userId";

    private final EntityManager entityManager;
    private final UserSettingRepository userSettingRepository;

    @Transactional
    public DevOnboardingResetResponse reset(Long userId) {
        // 거래는 남기고 어느 블록에서 얼마를 뺐는지만 지운다 (블록을 다시 만들면 확정 뒤 거래만 다시 반영된다)
        int released = entityManager.createQuery(
                        "update Transaction t set t.block = null, t.blockAmount = null "
                                + "where t.user.id = :userId and t.block is not null")
                .setParameter("userId", userId).executeUpdate();
        int transfers = entityManager.createQuery(
                        "delete from BlockTransfer t where t.fromBlock.id in (" + USER_BLOCKS + ") "
                                + "or t.toBlock.id in (" + USER_BLOCKS + ") "
                                + "or t.fromWaitingFund.id in (" + USER_FUNDS + ") "
                                + "or t.toWaitingFund.id in (" + USER_FUNDS + ")")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("delete from CoachProposal p where p.user.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("delete from BlockCategory c where c.user.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        int waitingFunds = entityManager.createQuery("delete from WaitingFund w where w.user.id = :userId")
                .setParameter("userId", userId).executeUpdate();
        int blocks = entityManager.createQuery("delete from Block b where b.user.id = :userId")
                .setParameter("userId", userId).executeUpdate();

        userSettingRepository.findByUser_Id(userId).ifPresent(setting -> setting.resetBlocksConfirmation());
        return new DevOnboardingResetResponse(blocks, transfers, waitingFunds, released);
    }
}
