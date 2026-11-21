package com.swiftdrop.backend.modules.notifications;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.backend.modules.auth.dto.*;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.repository.*;
import com.swiftdrop.backend.modules.auth.service.*;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.service.MerchantAdminService;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;

/** Full services/repositories against H2; email delivery is mocked, no shared records touched. */
@SpringBootTest(properties = {"app.messaging.outbox.publisher-enabled=false", "management.health.mail.enabled=false", "spring.mail.username=notifications@example.test"})
@Transactional
class MerchantOnboardingIntegrationTest {
    @Autowired private AuthService auth;
    @Autowired private UserRepository users;
    @Autowired private MerchantRepository merchants;
    @Autowired private MerchantAdminService approvals;
    @Autowired private EmailVerificationTokenRepository codes;
    @Autowired private OutboxEventRepository outbox;
    @MockitoBean private EmailService email;
    @MockitoBean private org.springframework.mail.javamail.JavaMailSender mail;
    @Autowired private com.swiftdrop.backend.modules.notifications.consumer.MerchantRejectionEmailConsumer rejectionConsumer;
    @Autowired private tools.jackson.databind.json.JsonMapper mapper;
    @Autowired private com.swiftdrop.backend.modules.notifications.consumer.MerchantDecisionNotificationConsumer decisionNotifications;
    @Autowired private com.swiftdrop.backend.modules.notifications.service.NotificationService notificationService;

    @Test
    void rejectionOutboxDeliversTheStoredReasonToBothChannels() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        String reason = "Business details are inconsistent\n\nDetails: The contact address does not match.";
        var response = approvals.rejectMerchant(profile.getId(), "  " + reason + "  ", UUID.randomUUID());
        assertEquals(reason, response.getApprovalNotes());
        var row = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())
                && e.getEventType().equals("MERCHANT_REJECTED")).findFirst().orElseThrow();
        var event = new com.swiftdrop.backend.modules.messaging.event.DomainEventMessage(row.getId(),
                row.getEventType(), row.getAggregateType(), row.getAggregateId(), row.getCreatedAt(), mapper.readTree(row.getPayload()));
        rejectionConsumer.onDecision(event);
        decisionNotifications.onDecision(event);
        decisionNotifications.onDecision(event);
        assertEquals(1, notificationService.getMyNotifications(account.getId()).size());
        var message = org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail).send(message.capture());
        assertArrayEquals(new String[]{request.getEmail()}, message.getValue().getTo());
        assertEquals("SwiftDrop - Merchant Application Rejected", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("Reason:\n" + reason));
        assertEquals(AccountStatus.REJECTED, users.findById(account.getId()).orElseThrow().getStatus());
    }

    @Test
    void approvalOutboxDeliversEmailAndInAppNotification() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.approveMerchant(profile.getId(), "Test approval", UUID.randomUUID());
        var row = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())
                && e.getEventType().equals("MERCHANT_APPROVED")).findFirst().orElseThrow();
        var event = new com.swiftdrop.backend.modules.messaging.event.DomainEventMessage(row.getId(), row.getEventType(),
                row.getAggregateType(), row.getAggregateId(), row.getCreatedAt(), mapper.readTree(row.getPayload()));
        decisionNotifications.onDecision(event);
        rejectionConsumer.onDecision(event);
        var saved = notificationService.getMyNotifications(account.getId());
        assertEquals(1, saved.size());
        assertEquals("MERCHANT_APPROVED", saved.getFirst().notificationType());
        var message = org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mail).send(message.capture());
        assertArrayEquals(new String[]{request.getEmail()}, message.getValue().getTo());
        assertEquals("SwiftDrop - Merchant Application Approved", message.getValue().getSubject());
        assertFalse(message.getValue().getText().contains("sdp_live_"));
    }

    @Test
    void missingBusinessRegistrationNumberCreatesNoAccount() {
        var request = signup();
        request.setRegistrationNumber(" ");
        assertThrows(IllegalArgumentException.class, () -> auth.register(request));
        assertTrue(users.findByEmail(request.getEmail()).isEmpty());
    }

    private SignUpRequest signup() {
        return SignUpRequest.builder().email(UUID.randomUUID() + "@example.test").password("TestPassword123!")
                .fullName("Test Contact").phone("0000000000").role(Role.ROLE_MERCHANT)
                .businessName("TEST ONLY Onboarding").businessAddress("Test address").registrationNumber("TEST-123").build();
    }

    @Test
    void anApprovedMerchantCanLaterBeRejectedAndLosesLoginRefreshAndApiKey() {
        var request = signup();
        var account = auth.register(request).getUser();
        verify(request);
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.approveMerchant(profile.getId(), "Test approval", UUID.randomUUID());
        var session = auth.login(login(request));
        assertNotNull(profile.getApiKey());
        approvals.rejectMerchant(profile.getId(), "Business details changed and could not be verified.", UUID.randomUUID());
        assertEquals(MerchantRegistrationStatus.REJECTED, profile.getRegistrationStatus());
        assertNull(profile.getApiKey());
        assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)));
        assertThrows(AccountNotActiveException.class, () -> auth.refreshToken(
                TokenRefreshRequest.builder().refreshToken(session.getRefreshToken()).build()));
        assertEquals(1, outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())
                && e.getEventType().equals("MERCHANT_REJECTED")).count());
    }

    private LoginRequest login(SignUpRequest request) {
        return LoginRequest.builder().email(request.getEmail()).password(request.getPassword()).build();
    }

    private AuthResponse verify(SignUpRequest request) {
        var code = codes.findAll().stream().filter(c -> c.getUser().getEmail().equals(request.getEmail()))
                .findFirst().orElseThrow().getToken();
        return auth.verifyEmail(new VerifyEmailRequest(request.getEmail(), code));
    }

    @Test
    void signupCreatesAnAccountApplicationAndOutboxEventWithoutTokens() {
        var request = signup();
        var response = auth.register(request);
        var profile = merchants.findByUserId(response.getUser().getId()).orElseThrow();
        assertEquals(MerchantRegistrationStatus.PENDING, profile.getRegistrationStatus());
        assertEquals(request.getBusinessName(), profile.getBusinessName());
        assertEquals(request.getBusinessAddress(), profile.getBusinessAddress());
        assertEquals(AccountStatus.PENDING_APPROVAL, response.getUser().getStatus());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertEquals(1, outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())).count());
    }

    @Test
    void emailVerificationAndItsRepeatNeverLetAPendingMerchantIn() {
        var request = signup();
        auth.register(request);
        assertNull(verify(request).getAccessToken());
        assertNull(verify(request).getAccessToken());
        assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)));
    }

    @Test
    void approvalBeforeVerificationStillRequiresEmailVerification() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.approveMerchant(profile.getId(), "Test approval", UUID.randomUUID());
        assertFalse(users.findById(account.getId()).orElseThrow().isEmailVerified());
        assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)));
        assertNotNull(verify(request).getAccessToken());
        assertNotNull(auth.login(login(request)).getAccessToken());
    }

    @Test
    void verificationThenApprovalAllowsLoginAndPreservesVerification() {
        var request = signup();
        var account = auth.register(request).getUser();
        assertNull(verify(request).getAccessToken());
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.approveMerchant(profile.getId(), "Test approval", UUID.randomUUID());
        var result = auth.login(login(request));
        assertNotNull(result.getAccessToken());
        assertTrue(result.getUser().isEmailVerified());
        assertEquals(AccountStatus.ACTIVE, result.getUser().getStatus());
    }

    @Test
    void rejectionNeverActivatesTheVerifiedAccount() {
        var request = signup();
        var account = auth.register(request).getUser();
        verify(request);
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.rejectMerchant(profile.getId(), "Test rejection", UUID.randomUUID());
        assertEquals(MerchantRegistrationStatus.REJECTED, profile.getRegistrationStatus());
        assertEquals(AccountStatus.REJECTED, users.findById(account.getId()).orElseThrow().getStatus());
        assertTrue(assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)))
                .getMessage().contains("has been rejected"));
        assertNull(verify(request).getAccessToken());
    }

    @Test
    void suspensionCannotBeUndoneByApplicationApprovalOrVerification() {
        var request = signup();
        var account = auth.register(request).getUser();
        verify(request);
        var user = users.findById(account.getId()).orElseThrow();
        user.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(user);
        var profile = merchants.findByUserId(user.getId()).orElseThrow();
        assertThrows(ResponseStatusException.class, () -> approvals.approveMerchant(profile.getId(), null, UUID.randomUUID()));
        assertEquals(MerchantRegistrationStatus.PENDING, profile.getRegistrationStatus());
        assertEquals(AccountStatus.SUSPENDED, user.getStatus());
        assertNull(verify(request).getAccessToken());
    }

    @Test
    void incompleteMerchantSignupDoesNotCreateAnOrphanAccount() {
        var request = signup();
        request.setBusinessAddress(" ");
        assertThrows(IllegalArgumentException.class, () -> auth.register(request));
        assertTrue(users.findByEmail(request.getEmail()).isEmpty());
    }

    @Test
    void sameDecisionCannotBeRepeatedButOppositeDecisionCan() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.rejectMerchant(profile.getId(), "Test rejection", UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> approvals.rejectMerchant(profile.getId(), "Duplicate", UUID.randomUUID()));
        approvals.approveMerchant(profile.getId(), "Reconsidered", UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> approvals.approveMerchant(profile.getId(), null, UUID.randomUUID()));
    }

    @Test
    void verifiedMerchantCanBeApprovedRejectedAndReapprovedWithAFreshKeyAndNotices() {
        var request = signup();
        var account = auth.register(request).getUser();
        verify(request);
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        var reviewer = UUID.randomUUID();
        approvals.approveMerchant(profile.getId(), "Initial approval", reviewer);
        String oldKey = profile.getApiKey();
        var session = auth.login(login(request));
        approvals.rejectMerchant(profile.getId(), "Details require another review", reviewer);
        assertEquals(AccountStatus.REJECTED, users.findById(account.getId()).orElseThrow().getStatus());
        assertNull(profile.getApiKey());
        assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)));
        assertThrows(AccountNotActiveException.class, () -> auth.refreshToken(
                TokenRefreshRequest.builder().refreshToken(session.getRefreshToken()).build()));
        approvals.approveMerchant(profile.getId(), "Corrected details accepted", reviewer);
        assertEquals(AccountStatus.ACTIVE, users.findById(account.getId()).orElseThrow().getStatus());
        assertNotNull(profile.getApiKey());
        assertNotEquals(oldKey, profile.getApiKey());
        assertTrue(merchants.findByApiKey(oldKey).isEmpty());
        assertNotNull(auth.login(login(request)).getAccessToken());
        var decisions = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())
                && (e.getEventType().equals("MERCHANT_APPROVED") || e.getEventType().equals("MERCHANT_REJECTED"))).toList();
        assertEquals(3, decisions.size());
        for (var row : decisions) {
            var event = new com.swiftdrop.backend.modules.messaging.event.DomainEventMessage(row.getId(), row.getEventType(),
                    row.getAggregateType(), row.getAggregateId(), row.getCreatedAt(), mapper.readTree(row.getPayload()));
            decisionNotifications.onDecision(event);
            decisionNotifications.onDecision(event);
            rejectionConsumer.onDecision(event);
        }
        assertEquals(3, notificationService.getMyNotifications(account.getId()).size());
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(3)).send(
                org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void reapprovalDoesNotVerifyEmailOrUndoSuspension() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        approvals.rejectMerchant(profile.getId(), "Review again", UUID.randomUUID());
        approvals.approveMerchant(profile.getId(), "Reapproved", UUID.randomUUID());
        assertFalse(users.findById(account.getId()).orElseThrow().isEmailVerified());
        assertThrows(AccountNotActiveException.class, () -> auth.login(login(request)));
        approvals.rejectMerchant(profile.getId(), "Rejected again", UUID.randomUUID());
        var user = users.findById(account.getId()).orElseThrow();
        user.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(user);
        assertThrows(ResponseStatusException.class, () -> approvals.approveMerchant(profile.getId(), null, UUID.randomUUID()));
        assertEquals(AccountStatus.SUSPENDED, user.getStatus());
    }

    @Test
    void merchantsCannotBeSuspendedOrRestoredThroughUserManagement() {
        var request = signup();
        var account = auth.register(request).getUser();
        var profile = merchants.findByUserId(account.getId()).orElseThrow();
        for (var decision : new MerchantRegistrationStatus[]{MerchantRegistrationStatus.PENDING,
                MerchantRegistrationStatus.REJECTED, MerchantRegistrationStatus.APPROVED}) {
            if (decision == MerchantRegistrationStatus.REJECTED) approvals.rejectMerchant(profile.getId(), "Review again", UUID.randomUUID());
            if (decision == MerchantRegistrationStatus.APPROVED) approvals.approveMerchant(profile.getId(), "Accepted", UUID.randomUUID());
            var before = users.findById(account.getId()).orElseThrow().getStatus();
            for (var status : new AccountStatus[]{AccountStatus.ACTIVE, AccountStatus.SUSPENDED}) {
                var error = assertThrows(ResponseStatusException.class, () -> auth.updateUserStatus(account.getId(), status));
                assertEquals(409, error.getStatusCode().value());
            }
            assertEquals(before, users.findById(account.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void removingLegacyMerchantSuspensionPreservesEveryBusinessDecision() {
        for (var decision : MerchantRegistrationStatus.values()) {
            var account = auth.register(signup()).getUser();
            var profile = merchants.findByUserId(account.getId()).orElseThrow();
            if (decision == MerchantRegistrationStatus.APPROVED) approvals.approveMerchant(profile.getId(), "Accepted", UUID.randomUUID());
            if (decision == MerchantRegistrationStatus.REJECTED) approvals.rejectMerchant(profile.getId(), "Review again", UUID.randomUUID());
            var user = users.findById(account.getId()).orElseThrow();
            user.setStatus(AccountStatus.SUSPENDED);
            users.saveAndFlush(user);
            var restored = auth.updateUserStatus(account.getId(), AccountStatus.ACTIVE);
            var expected = switch (decision) {
                case APPROVED -> AccountStatus.ACTIVE;
                case PENDING -> AccountStatus.PENDING_APPROVAL;
                case REJECTED -> AccountStatus.REJECTED;
            };
            assertEquals(expected, restored.getStatus());
            assertEquals(decision, merchants.findById(profile.getId()).orElseThrow().getRegistrationStatus());
            assertFalse(users.findById(account.getId()).orElseThrow().isEmailVerified());
        }
    }

    @Test
    void genericStatusEndpointCannotReplaceTheMerchantReviewProcess() {
        var request = signup();
        var account = auth.register(request).getUser();
        for (var status : new AccountStatus[]{AccountStatus.REJECTED, AccountStatus.PENDING_APPROVAL}) {
            assertThrows(ResponseStatusException.class, () -> auth.updateUserStatus(account.getId(), status));
        }
        assertEquals(AccountStatus.PENDING_APPROVAL, users.findById(account.getId()).orElseThrow().getStatus());
    }

    @Test
    void nonmerchantOperationalAccountsStillSupportSuspensionAndRestoration() {
        for (var role : new Role[]{Role.ROLE_CUSTOMER, Role.ROLE_COURIER, Role.ROLE_DISPATCHER}) {
            var user = users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.test")
                    .fullName("Isolated status test").role(role).status(AccountStatus.ACTIVE).emailVerified(true).build());
            assertEquals(AccountStatus.SUSPENDED, auth.updateUserStatus(user.getId(), AccountStatus.SUSPENDED).getStatus());
            assertEquals(AccountStatus.ACTIVE, auth.updateUserStatus(user.getId(), AccountStatus.ACTIVE).getStatus());
            assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());
            assertTrue(merchants.findByUserId(user.getId()).isEmpty());
        }
    }
}
