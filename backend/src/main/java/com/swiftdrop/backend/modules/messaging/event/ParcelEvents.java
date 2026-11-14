package com.swiftdrop.backend.modules.messaging.event;

import java.util.Locale;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

/**
 * Event names and routing keys for parcel events.
 *
 * <p>Routing keys look like {@code parcel.status.delivered}, so a consumer can bind to
 * {@code parcel.#} for everything, or {@code parcel.status.failed} for one status.
 */
public final class ParcelEvents {

    public static final String AGGREGATE_TYPE = "parcel";
    public static final String STATUS_CHANGED = "parcel.status_changed";

    private ParcelEvents() {
    }

    public static String statusRoutingKey(ParcelStatus status) {
        return "parcel.status." + status.name().toLowerCase(Locale.ROOT);
    }
}
