package com.swiftdrop.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

class DemoDataSeederTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("70000000-0000-4000-8000-000000000002");

    private final ParcelRepository parcelRepository = mock(ParcelRepository.class);
    private final ParcelStatusService parcelStatusService = mock(ParcelStatusService.class);
    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private final PricingRuleRepository pricingRuleRepository = mock(PricingRuleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DemoDataSeeder seeder = new DemoDataSeeder(parcelRepository, parcelStatusService, zoneRepository,
            pricingRuleRepository, userRepository, passwordEncoder,
            Clock.fixed(Instant.parse("2026-11-21T04:00:00Z"), ZoneOffset.UTC));

    @Test
    void happyPathNeverChoosesCancelledOrFailed() {
        assertThat(DemoDataSeeder.stepsTo(ParcelStatus.CREATED)).isEmpty();
        assertThat(DemoDataSeeder.stepsTo(ParcelStatus.OUT_FOR_DELIVERY))
                .containsExactly(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        assertThatThrownBy(() -> DemoDataSeeder.stepsTo(ParcelStatus.CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedsTestAccountsAndGivesDemoParcelsToTheTestCustomer() {
        ReflectionTestUtils.setField(seeder, "configuredPassword", "");
        when(zoneRepository.count()).thenReturn(1L);
        when(pricingRuleRepository.count()).thenReturn(1L);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getRole() == Role.ROLE_CUSTOMER) {
                user.setId(CUSTOMER_ID);
            }
            return user;
        });
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(null);

        var users = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(4)).save(users.capture());
        assertThat(users.getAllValues()).extracting(User::getRole).containsExactly(
                Role.ROLE_CUSTOMER, Role.ROLE_COURIER, Role.ROLE_DISPATCHER, Role.ROLE_ADMIN);
        assertThat(users.getAllValues()).extracting(User::getEmail).allMatch(email -> email.endsWith("@example.com"));

        var parcels = ArgumentCaptor.forClass(Parcel.class);
        verify(parcelRepository, org.mockito.Mockito.times(3)).save(parcels.capture());
        assertThat(parcels.getAllValues()).extracting(Parcel::getCustomerId).containsOnly(CUSTOMER_ID);
        assertThat(parcels.getAllValues()).extracting(Parcel::getRecipientName)
                .allMatch(name -> name.startsWith("Test Recipient"));
        verify(parcelStatusService, never()).updateStatus(any(), eq(ParcelStatus.CANCELLED), any(), any());
        verify(parcelStatusService, org.mockito.Mockito.times(5)).updateStatus(any(), any(), isNull(), isNull());
    }

    @Test
    void existingAccountsAndParcelsAreLeftAlone() {
        User existing = User.builder().id(CUSTOMER_ID).email(DemoDataSeeder.CUSTOMER_EMAIL).fullName("Test Customer")
                .role(Role.ROLE_CUSTOMER).build();
        when(zoneRepository.count()).thenReturn(1L);
        when(pricingRuleRepository.count()).thenReturn(1L);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(existing));
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.of(mock(Parcel.class)));

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(parcelRepository, never()).save(any());
    }
}
