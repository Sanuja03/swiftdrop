package com.swiftdrop.backend.modules.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.zone.dto.CreateZoneRequest;
import com.swiftdrop.backend.modules.zone.dto.UpdateZoneRequest;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

class ZoneAdminServiceTest {
    private final ZoneRepository repository = mock(ZoneRepository.class);
    private final ZoneAdminService service = new ZoneAdminService(repository);

    @BeforeEach
    void saveReturnsTheEntity() {
        when(repository.saveAndFlush(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsActiveAndInactiveZones() {
        var inactive = Zone.create("ZONE_B", "Zone B", BigDecimal.ZERO);
        inactive.setActive(false);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(Zone.create("ZONE_A", "Zone A", BigDecimal.TEN), inactive));

        var zones = service.listAll();

        assertThat(zones).extracting(zone -> zone.code()).containsExactly("ZONE_A", "ZONE_B");
        assertThat(zones).extracting(zone -> zone.active()).containsExactly(true, false);
    }

    @Test
    void createsActiveZoneWithUpperCaseCode() {
        var created = service.create(new CreateZoneRequest(" north_1 ", "  North Zone  ", new BigDecimal("75.50")));

        var saved = ArgumentCaptor.forClass(Zone.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isNotNull();
        assertThat(created.code()).isEqualTo("NORTH_1");
        assertThat(created.name()).isEqualTo("North Zone");
        assertThat(created.surcharge()).isEqualByComparingTo("75.50");
        assertThat(created.active()).isTrue();
    }

    @Test
    void duplicateCodeIsRejectedWithConflict() {
        when(repository.existsByCode("NORTH_1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateZoneRequest("north_1", "North Zone", BigDecimal.ZERO)))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateCodeCreatedAtTheSameMomentIsAlsoAConflict() {
        when(repository.saveAndFlush(any(Zone.class))).thenThrow(new DataIntegrityViolationException("unique code"));

        assertThatThrownBy(() -> service.create(new CreateZoneRequest("NORTH_1", "North Zone", BigDecimal.ZERO)))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updatesNameAndSurchargeButNeverTheCode() {
        var zone = Zone.create("NORTH_1", "North Zone", BigDecimal.ZERO);
        when(repository.findById(zone.getId())).thenReturn(Optional.of(zone));

        var updated = service.update(zone.getId(), new UpdateZoneRequest(" North Zone 2 ", new BigDecimal("120")));

        assertThat(updated.code()).isEqualTo("NORTH_1");
        assertThat(updated.name()).isEqualTo("North Zone 2");
        assertThat(updated.surcharge()).isEqualByComparingTo("120");
    }

    @Test
    void deactivatesAndReactivatesWithoutDeleting() {
        var zone = Zone.create("NORTH_1", "North Zone", BigDecimal.ZERO);
        when(repository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(repository.countByActiveTrue()).thenReturn(2L);

        assertThat(service.setActive(zone.getId(), false).active()).isFalse();
        assertThat(service.setActive(zone.getId(), true).active()).isTrue();
        verify(repository, never()).delete(any());
    }

    @Test
    void lastActiveZoneCannotBeDeactivated() {
        var zone = Zone.create("NORTH_1", "North Zone", BigDecimal.ZERO);
        when(repository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(repository.countByActiveTrue()).thenReturn(1L);

        assertThatThrownBy(() -> service.setActive(zone.getId(), false))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(zone.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lastActiveZoneCanStillBeEditedAndConfirmedActive() {
        var zone = Zone.create("NORTH_1", "North Zone", BigDecimal.ZERO);
        when(repository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(repository.countByActiveTrue()).thenReturn(1L);

        assertThat(service.update(zone.getId(), new UpdateZoneRequest("North Zone", BigDecimal.TEN)).surcharge())
                .isEqualByComparingTo("10");
        assertThat(service.setActive(zone.getId(), true).active()).isTrue();
    }

    @Test
    void inactiveZoneCanBeReactivatedWhenNoneAreActive() {
        var zone = Zone.create("NORTH_1", "North Zone", BigDecimal.ZERO);
        zone.setActive(false);
        when(repository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(repository.countByActiveTrue()).thenReturn(0L);

        assertThat(service.setActive(zone.getId(), true).active()).isTrue();
    }

    @Test
    void unknownZoneIsNotFound() {
        var id = UUID.fromString("40000000-0000-4000-8000-000000000099");
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateZoneRequest("Name", BigDecimal.ZERO)))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.setActive(id, false))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
