package com.dothis.fintris.devtools;

import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.transaction.service.BlockReflectionService;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 개발 서버 시연용: 블록 확정 시각을 과거로 옮겨, 그 뒤 거래(시연 거래 등)가 블록에서 빠진 모습을 바로 보여 준다
// 확정 시각만 바꾸면 반영이 다음 정기 동기화까지 미뤄지므로, 옮긴 뒤 반영을 바로 다시 맞춘다
// 거래 하나 = 트랜잭션 하나라 블록 충돌이 나면 그 거래만 다시 시도한다 (정기 동기화와 같은 방식)
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-tools.enabled", havingValue = "true")
public class DevBlocksService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_ATTEMPTS = 3;

    private final UserSettingRepository userSettingRepository;
    private final BlockRepository blockRepository;
    private final TransactionRepository transactionRepository;
    private final BlockReflectionService blockReflectionService;
    private final TransactionTemplate transactionTemplate;

    public DevBlocksConfirmedAtResponse moveBlocksConfirmedAt(Long userId, LocalDateTime confirmedAt) {
        if (confirmedAt.isAfter(LocalDateTime.now(KST))) {
            throw GeneralException.of(DevToolsErrorCode.DEV_CONFIRMED_AT_IN_FUTURE);
        }
        LocalDateTime before = transactionTemplate.execute(status -> {
            // 설정 행은 온보딩 중에 생겨서, 없으면 아직 블록을 확정하지 않은 것이다
            UserSetting setting = userSettingRepository.findByUser_Id(userId)
                    .orElseThrow(() -> GeneralException.of(DevToolsErrorCode.DEV_BLOCKS_NOT_CONFIRMED));
            if (!setting.isBlocksConfirmed() || setting.getBlocksConfirmedAt() == null) {
                throw GeneralException.of(DevToolsErrorCode.DEV_BLOCKS_NOT_CONFIRMED);
            }
            LocalDateTime previous = setting.getBlocksConfirmedAt();
            setting.moveBlocksConfirmedAt(confirmedAt);
            // 사용형 기간이 확정한 날부터라 앞당긴 날 이후 지출이 소진 그래프에 안 잡힌다. 기간 시작일도 같이 당긴다
            LocalDate newStart = confirmedAt.toLocalDate();
            blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId).stream()
                    .filter(block -> block.getType() == BlockType.SPENDING)
                    .filter(block -> block.getStartDate() != null && block.getStartDate().isAfter(newStart))
                    .forEach(block -> block.update(null, null, null, null, newStart, null, null));
            return previous;
        });

        // 앞당기면 새로 반영할 거래가, 늦추면 되돌릴 거래가 생긴다. 둘 중 이른 시각부터 본다
        LocalDateTime from = before.isBefore(confirmedAt) ? before : confirmedAt;
        int reconciled = 0;
        int skipped = 0;
        for (Long transactionId : transactionRepository.findOutgoingIdsSince(userId, from)) {
            Boolean changed = reconcile(transactionId, confirmedAt);
            if (changed == null) {
                skipped++;
            } else if (changed) {
                reconciled++;
            }
        }
        return new DevBlocksConfirmedAtResponse(confirmedAt, reconciled, skipped);
    }

    // 바뀌었으면 true, 그대로면 false, 충돌로 못 맞췄으면 null
    private Boolean reconcile(Long transactionId, LocalDateTime confirmedAt) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return blockReflectionService.reconcileIfNeeded(transactionId, confirmedAt);
            } catch (OptimisticLockingFailureException e) {
                // 다시 시도
            }
        }
        return null;
    }
}
