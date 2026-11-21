package com.swiftdrop.backend.modules.notifications.event;

import java.util.UUID;

/** IDs only: consumers resolve the current parcel and account details. */
public record AssignmentNoticePayload(UUID assignmentId, UUID parcelId, UUID courierId,
                                      UUID dispatcherId, String reason) {}
