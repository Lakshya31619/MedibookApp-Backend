package com.medibook.notification;

import com.medibook.notification.dto.NotificationDto.*;
import com.medibook.notification.entity.Notification;
import com.medibook.notification.repository.NotificationRepository;
import com.medibook.notification.serviceimpl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "mailFrom", "test@medibook.com");
        ReflectionTestUtils.setField(notificationService, "mailFromName", "MediBook");

        testNotification = new Notification();
        testNotification.setNotificationId(1);
        testNotification.setRecipientId(2);
        testNotification.setType("BOOKING");
        testNotification.setTitle("Appointment Confirmed");
        testNotification.setMessage("Your appointment is confirmed.");
        testNotification.setChannel("APP");
        testNotification.setRead(false);
        testNotification.setSentAt(LocalDateTime.now());
    }

    @Test
    void send_ShouldSaveAppNotification() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2);
        req.setType("BOOKING");
        req.setTitle("Appointment Confirmed");
        req.setMessage("Your appointment is confirmed.");
        req.setChannels(List.of("APP"));

        when(notificationRepository.save(any())).thenReturn(testNotification);

        Notification result = notificationService.send(req);

        assertNotNull(result);
        assertEquals("BOOKING", result.getType());
        verify(notificationRepository).save(any());
    }

    @Test
    void send_ShouldDefaultToApp_WhenNoChannelSpecified() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(2);
        req.setType("BOOKING");
        req.setTitle("Test");
        req.setMessage("Test message");
        req.setChannels(null); // no channel

        when(notificationRepository.save(any())).thenReturn(testNotification);

        notificationService.send(req);

        verify(notificationRepository, atLeastOnce()).save(any());
    }

    @Test
    void markAsRead_ShouldSetReadTrue() {
        when(notificationRepository.findById(1))
            .thenReturn(Optional.of(testNotification));
        when(notificationRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0));

        notificationService.markAsRead(1);

        assertTrue(testNotification.isRead());
    }

    @Test
    void markAsRead_ShouldThrow_WhenNotFound() {
        when(notificationRepository.findById(99))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> notificationService.markAsRead(99));
    }

    @Test
    void getUnreadCount_ShouldReturnCount() {
        when(notificationRepository.countByRecipientIdAndIsRead(2, false))
            .thenReturn(5);

        int count = notificationService.getUnreadCount(2);

        assertEquals(5, count);
    }

    @Test
    void sendBulk_ShouldReturnCorrectCounts() {
        BulkNotificationRequest req = new BulkNotificationRequest();
        req.setRecipientIds(List.of(1, 2, 3));
        req.setType("BROADCAST");
        req.setTitle("System Notice");
        req.setMessage("Maintenance tonight.");
        req.setChannels(List.of("APP"));

        when(notificationRepository.save(any())).thenReturn(testNotification);

        BulkSendResult result = notificationService.sendBulk(req);

        assertEquals(3, result.getTotalSent());
        assertEquals(0, result.getFailed());
    }

    @Test
    void deleteNotification_ShouldThrow_WhenNotFound() {
        when(notificationRepository.existsById(99)).thenReturn(false);

        assertThrows(RuntimeException.class,
            () -> notificationService.deleteNotification(99));
    }
}
