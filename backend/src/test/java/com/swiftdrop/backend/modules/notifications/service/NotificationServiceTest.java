package com.swiftdrop.backend.modules.notifications.service;

import com.swiftdrop.backend.modules.notifications.entity.Notification;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository);
    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    void anotherUsersNotificationCannotBeMarkedRead() {
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class,
                () -> service.markAsRead(notificationId, userId));
        assertEquals(404, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void repeatedMarkReadPreservesOriginalReadTime() {
        Instant readAt = Instant.parse("2026-11-01T00:00:00Z");
        var notification = Notification.builder().id(notificationId).userId(userId)
                .read(true).readAt(readAt).build();
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);
        service.markAsRead(notificationId, userId);
        assertEquals(readAt, notification.getReadAt());
    }

    @Test
    void markReadUpdatesTheOwnedNotification() {
        var notification = Notification.builder().id(notificationId).userId(userId).build();
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);
        service.markAsRead(notificationId, userId);
        assertTrue(notification.isRead());
        assertNotNull(notification.getReadAt());
    }

    @Test
    void eventNotificationRequiresAnEventId() {
        assertThrows(IllegalArgumentException.class, () -> service.createEventNotification(
                null, userId, null, "Title", "Message", "PARCEL_STATUS"));
        verifyNoInteractions(repository);
    }

    @Test
    void createsGeneralNotificationsWithDefaultTypeAndPersistsEventIdentity() {
        when(repository.save(any())).thenAnswer(call -> {
            Notification notification = call.getArgument(0);
            notification.setId(notificationId);
            notification.onCreate();
            return notification;
        });
        var general = service.createNotification(userId, null, "Title", "Message", " ");
        assertEquals("GENERAL", general.notificationType());
        assertFalse(general.isRead());
        assertNotNull(general.createdAt());
        UUID eventId = UUID.randomUUID();
        service.createEventNotification(eventId, userId, null, "Title", "Message", "PARCEL_STATUS");
        verify(repository).save(argThat(notification -> eventId.equals(notification.getEventId())
                && userId.equals(notification.getUserId())));
    }

    @Test
    void listsAndUnreadCountsAreScopedToTheAuthenticatedUser() {
        Notification notification = Notification.builder().id(notificationId).userId(userId).build();
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        when(repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        when(repository.countByUserIdAndReadFalse(userId)).thenReturn(1L);
        assertEquals(notificationId, service.getMyNotifications(userId).getFirst().id());
        assertEquals(notificationId, service.getUnreadNotifications(userId).getFirst().id());
        assertEquals(1L, service.getUnreadCount(userId));
    }

    @Test
    void invalidNotificationsNeverReachPersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(null, null, "T", "M", null));
        assertThrows(IllegalArgumentException.class, () -> service.createEventNotification(UUID.randomUUID(), null, null, "T", "M", "PARCEL_STATUS"));
        for (String blank : new String[]{null, "", " "}) {
            assertThrows(IllegalArgumentException.class, () -> service.createNotification(userId, null, blank, "M", null));
            assertThrows(IllegalArgumentException.class, () -> service.createNotification(userId, null, "T", blank, null));
            assertThrows(IllegalArgumentException.class, () -> service.createEventNotification(UUID.randomUUID(), userId, null, blank, "M", "PARCEL_STATUS"));
            assertThrows(IllegalArgumentException.class, () -> service.createEventNotification(UUID.randomUUID(), userId, null, "T", blank, "PARCEL_STATUS"));
        }
        verifyNoInteractions(repository);
    }
}
