package com.medibook.notification.repository;

import com.medibook.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByRecipientIdOrderBySentAtDesc(int recipientId);

    List<Notification> findByRecipientIdAndIsRead(int recipientId, boolean isRead);

    int countByRecipientIdAndIsRead(int recipientId, boolean isRead);

    List<Notification> findByType(String type);

    List<Notification> findByRelatedId(int relatedId);

    List<Notification> findByRelatedIdAndType(int relatedId, String type);

    void deleteByNotificationId(int notificationId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId")
    void markAllReadByRecipientId(@Param("recipientId") int recipientId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND " +
           "n.sentAt < :cutoff")
    void deleteOldReadNotifications(
        @Param("cutoff") java.time.LocalDateTime cutoff);
}