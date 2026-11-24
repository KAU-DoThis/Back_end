package com.dothis.fintris.notification.repository;

import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.entity.PushStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByUser_IdAndTypeInOrderByCreatedAtDescIdDesc(
            Long userId, Collection<NotificationType> types, Pageable pageable);

    // 탭 배지용: 읽지 않은 알림의 종류 목록 (사용자 한 명의 30일치라 많지 않다)
    @Query("select n.type from Notification n where n.user.id = :userId and n.readAt is null")
    List<NotificationType> findUnreadTypes(@Param("userId") Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    // 같은 대상에 같은 종류 알림을 이미 남겼는지 (소진 속도는 블록 기간 시작부터 본다)
    boolean existsByUser_IdAndTypeAndRefIdAndCreatedAtGreaterThanEqual(
            Long userId, NotificationType type, Long refId, LocalDateTime since);

    // 오늘 푸시로 보낸 수 (하루 최대 건수 확인용)
    long countByUser_IdAndPushStatusAndPushedAtGreaterThanEqual(Long userId, PushStatus status, LocalDateTime since);

    long countByUser_IdAndTypeAndPushStatusAndPushedAtGreaterThanEqual(
            Long userId, NotificationType type, PushStatus status, LocalDateTime since);

    // 미뤄 둔 푸시. 하루가 지난 건 늦은 알림이라 다시 보내지 않는다
    List<Notification> findAllByPushStatusAndCreatedAtGreaterThanEqual(PushStatus status, LocalDateTime since);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
