package com.swiftdrop.backend.modules.tracking.websocket;

/**
 * WebSocket destinations used for live tracking.
 */
public final class TrackingTopics {

    public static final String PREFIX = "/topic";

    private TrackingTopics() {
    }

    public static String forTrackingNumber(String trackingNumber) {
        return PREFIX + "/tracking/" + trackingNumber;
    }
}
