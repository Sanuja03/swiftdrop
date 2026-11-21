# Endpoint coverage

Generated offline from current Spring controller mappings by `node postman/validate.mjs --write-coverage`.
A prepared request is not a live test result. Auth-only rows verify a security boundary, not business behavior.

| Method | Route | Prepared cases | Coverage | Controller |
|---|---|---:|---|---|
| GET | `/api/v1/admin/merchants` | 4 | Contract/lifecycle cases | [MerchantAdminController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantAdminController.java) |
| POST | `/api/v1/admin/merchants/{merchantId}/approve` | 8 | Contract/lifecycle cases | [MerchantAdminController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantAdminController.java) |
| POST | `/api/v1/admin/merchants/{merchantId}/reject` | 7 | Contract/lifecycle cases | [MerchantAdminController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantAdminController.java) |
| GET | `/api/v1/admin/parcels/{parcelId}/proofs` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| GET | `/api/v1/admin/parcels/{parcelId}/proofs/{proofId}/photo` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| GET | `/api/v1/admin/pricing-rules` | 1 | Contract/lifecycle cases | [AdminPricingRuleController.java](../backend/src/main/java/com/swiftdrop/backend/modules/pricing/controller/AdminPricingRuleController.java) |
| PUT | `/api/v1/admin/pricing-rules/{serviceType}` | 1 | Anonymous authentication smoke only | [AdminPricingRuleController.java](../backend/src/main/java/com/swiftdrop/backend/modules/pricing/controller/AdminPricingRuleController.java) |
| GET | `/api/v1/admin/reports/daily-deliveries` | 1 | Contract/lifecycle cases | [ReportController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportController.java) |
| GET | `/api/v1/admin/reports/snapshots` | 1 | Contract/lifecycle cases | [ReportSnapshotController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportSnapshotController.java) |
| POST | `/api/v1/admin/reports/snapshots` | 1 | Anonymous authentication smoke only | [ReportSnapshotController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportSnapshotController.java) |
| GET | `/api/v1/admin/reports/snapshots/{id}` | 1 | Contract/lifecycle cases | [ReportSnapshotController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportSnapshotController.java) |
| GET | `/api/v1/admin/reports/success-rate` | 1 | Contract/lifecycle cases | [ReportController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportController.java) |
| GET | `/api/v1/admin/reports/summary` | 1 | Contract/lifecycle cases | [ReportController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportController.java) |
| GET | `/api/v1/admin/reports/test` | 1 | Contract/lifecycle cases | [ReportController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportController.java) |
| GET | `/api/v1/admin/reports/total-parcels` | 1 | Contract/lifecycle cases | [ReportController.java](../backend/src/main/java/com/swiftdrop/backend/modules/reports/controller/ReportController.java) |
| GET | `/api/v1/admin/users` | 1 | Contract/lifecycle cases | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| PATCH | `/api/v1/admin/users/{userId}/status` | 2 | Contract/lifecycle cases | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| GET | `/api/v1/admin/webhooks/deliveries` | 4 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| GET | `/api/v1/admin/webhooks/deliveries/{id}` | 1 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| POST | `/api/v1/admin/webhooks/deliveries/{id}/retry` | 2 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| GET | `/api/v1/admin/zones` | 1 | Contract/lifecycle cases | [AdminZoneController.java](../backend/src/main/java/com/swiftdrop/backend/modules/zone/controller/AdminZoneController.java) |
| POST | `/api/v1/admin/zones` | 1 | Anonymous authentication smoke only | [AdminZoneController.java](../backend/src/main/java/com/swiftdrop/backend/modules/zone/controller/AdminZoneController.java) |
| PUT | `/api/v1/admin/zones/{id}` | 1 | Anonymous authentication smoke only | [AdminZoneController.java](../backend/src/main/java/com/swiftdrop/backend/modules/zone/controller/AdminZoneController.java) |
| PATCH | `/api/v1/admin/zones/{id}/active` | 1 | Anonymous authentication smoke only | [AdminZoneController.java](../backend/src/main/java/com/swiftdrop/backend/modules/zone/controller/AdminZoneController.java) |
| POST | `/api/v1/assistant/sessions` | 1 | Contract/lifecycle cases | [AssistantChatController.java](../backend/src/main/java/com/swiftdrop/backend/modules/assistant/controller/AssistantChatController.java) |
| GET | `/api/v1/assistant/sessions/{sessionId}/messages` | 1 | Contract/lifecycle cases | [AssistantChatController.java](../backend/src/main/java/com/swiftdrop/backend/modules/assistant/controller/AssistantChatController.java) |
| POST | `/api/v1/assistant/sessions/{sessionId}/messages` | 1 | Anonymous authentication smoke only | [AssistantChatController.java](../backend/src/main/java/com/swiftdrop/backend/modules/assistant/controller/AssistantChatController.java) |
| POST | `/api/v1/auth/change-password` | 8 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/forgot-password` | 2 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/google` | 2 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/login` | 19 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/logout` | 10 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| GET | `/api/v1/auth/me` | 3 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| PUT | `/api/v1/auth/profile` | 1 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/refresh` | 4 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/resend-verification` | 2 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/reset-password` | 2 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/signup` | 6 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| POST | `/api/v1/auth/verify-email` | 4 | Contract/lifecycle cases | [AuthController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/AuthController.java) |
| GET | `/api/v1/courier/assignments` | 1 | Contract/lifecycle cases | [CourierAssignmentController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/CourierAssignmentController.java) |
| POST | `/api/v1/courier/assignments/{assignmentId}/accept` | 1 | Anonymous authentication smoke only | [CourierAssignmentController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/CourierAssignmentController.java) |
| POST | `/api/v1/courier/assignments/{assignmentId}/complete` | 1 | Anonymous authentication smoke only | [CourierAssignmentController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/CourierAssignmentController.java) |
| POST | `/api/v1/courier/assignments/{assignmentId}/reject` | 1 | Anonymous authentication smoke only | [CourierAssignmentController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/CourierAssignmentController.java) |
| GET | `/api/v1/courier/deliveries/today` | 1 | Contract/lifecycle cases | [CourierController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/CourierController.java) |
| GET | `/api/v1/courier/me` | 1 | Contract/lifecycle cases | [CourierController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/CourierController.java) |
| GET | `/api/v1/courier/parcels/{parcelId}` | 1 | Anonymous authentication smoke only | [CourierController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/CourierController.java) |
| POST | `/api/v1/courier/parcels/{parcelId}/pickup` | 1 | Anonymous authentication smoke only | [CourierController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/CourierController.java) |
| POST | `/api/v1/courier/parcels/{parcelId}/proof` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| POST | `/api/v1/courier/parcels/{parcelId}/proof/corrections` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| POST | `/api/v1/courier/parcels/{parcelId}/proof/corrections/photo` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| POST | `/api/v1/courier/parcels/{parcelId}/proof/photo` | 1 | Anonymous authentication smoke only | [DeliveryProofController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/DeliveryProofController.java) |
| PATCH | `/api/v1/courier/parcels/{parcelId}/status` | 1 | Anonymous authentication smoke only | [CourierController.java](../backend/src/main/java/com/swiftdrop/backend/modules/courier/controller/CourierController.java) |
| GET | `/api/v1/courier/test` | 1 | Anonymous authentication smoke only | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| GET | `/api/v1/customer/parcels` | 1 | Contract/lifecycle cases | [CustomerShipmentController.java](../backend/src/main/java/com/swiftdrop/backend/modules/tracking/controller/CustomerShipmentController.java) |
| GET | `/api/v1/customer/test` | 1 | Contract/lifecycle cases | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| POST | `/api/v1/dev/parcels/{trackingNumber}/status` | 0 | Development-only; intentionally excluded | [DevParcelStatusController.java](../backend/src/main/java/com/swiftdrop/backend/dev/DevParcelStatusController.java) |
| POST | `/api/v1/dispatcher/assignments` | 1 | Anonymous authentication smoke only | [DispatchController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/DispatchController.java) |
| PATCH | `/api/v1/dispatcher/assignments/{assignmentId}/cancel` | 1 | Anonymous authentication smoke only | [DispatchController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/DispatchController.java) |
| GET | `/api/v1/dispatcher/couriers/active` | 1 | Contract/lifecycle cases | [DispatchController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/DispatchController.java) |
| GET | `/api/v1/dispatcher/parcels/unassigned` | 1 | Contract/lifecycle cases | [DispatchController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/DispatchController.java) |
| GET | `/api/v1/dispatcher/workloads` | 1 | Contract/lifecycle cases | [DispatchController.java](../backend/src/main/java/com/swiftdrop/backend/modules/dispatch/controller/DispatchController.java) |
| POST | `/api/v1/merchant/api-key/regenerate` | 4 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/api-key/rotate` | 4 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/api/v1/calculate-fee` | 4 | Contract/lifecycle cases | [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantApiController.java) |
| GET | `/api/v1/merchant/api/v1/orders` | 11 | Contract/lifecycle cases | [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantApiController.java) |
| POST | `/api/v1/merchant/api/v1/orders` | 7 | Contract/lifecycle cases | [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantApiController.java) |
| GET | `/api/v1/merchant/api/v1/orders/{trackingNumber}` | 2 | Contract/lifecycle cases | [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantApiController.java) |
| POST | `/api/v1/merchant/api/v1/orders/{trackingNumber}/cancel` | 4 | Contract/lifecycle cases | [MerchantApiController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantApiController.java) |
| POST | `/api/v1/merchant/calculate-fee` | 3 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/me` | 1 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/orders` | 1 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/orders` | 3 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/orders/{trackingNumber}` | 1 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/orders/{trackingNumber}/cancel` | 2 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/profile` | 4 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/register` | 3 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/stats` | 1 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/test` | 1 | Contract/lifecycle cases | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| GET | `/api/v1/merchant/webhook` | 2 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| PUT | `/api/v1/merchant/webhook` | 6 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| GET | `/api/v1/merchant/webhook/logs` | 1 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| POST | `/api/v1/merchant/webhook/rotate-secret` | 3 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| POST | `/api/v1/merchant/webhook/test` | 11 | Contract/lifecycle cases | [MerchantController.java](../backend/src/main/java/com/swiftdrop/backend/modules/merchant/controller/MerchantController.java) |
| GET | `/api/v1/merchant/webhooks/config` | 16 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| PUT | `/api/v1/merchant/webhooks/config` | 17 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| POST | `/api/v1/merchant/webhooks/config/rotate-secret` | 3 | Contract/lifecycle cases | [WebhookConfigurationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookConfigurationController.java) |
| GET | `/api/v1/merchant/webhooks/deliveries` | 6 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| GET | `/api/v1/merchant/webhooks/deliveries/{id}` | 3 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| POST | `/api/v1/merchant/webhooks/deliveries/{id}/retry` | 5 | Contract/lifecycle cases | [WebhookDeliveryController.java](../backend/src/main/java/com/swiftdrop/backend/modules/webhook/controller/WebhookDeliveryController.java) |
| GET | `/api/v1/notifications` | 1 | Contract/lifecycle cases | [NotificationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/notifications/controller/NotificationController.java) |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 1 | Contract/lifecycle cases | [NotificationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/notifications/controller/NotificationController.java) |
| GET | `/api/v1/notifications/unread` | 1 | Contract/lifecycle cases | [NotificationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/notifications/controller/NotificationController.java) |
| GET | `/api/v1/notifications/unread-count` | 1 | Contract/lifecycle cases | [NotificationController.java](../backend/src/main/java/com/swiftdrop/backend/modules/notifications/controller/NotificationController.java) |
| POST | `/api/v1/parcels` | 8 | Contract/lifecycle cases | [BookingController.java](../backend/src/main/java/com/swiftdrop/backend/modules/booking/controller/BookingController.java) |
| GET | `/api/v1/parcels/{trackingNumber}` | 4 | Contract/lifecycle cases | [BookingController.java](../backend/src/main/java/com/swiftdrop/backend/modules/booking/controller/BookingController.java) |
| POST | `/api/v1/parcels/estimate` | 4 | Contract/lifecycle cases | [PricingController.java](../backend/src/main/java/com/swiftdrop/backend/modules/pricing/controller/PricingController.java) |
| GET | `/api/v1/pricing/services` | 1 | Contract/lifecycle cases | [ServicePriceController.java](../backend/src/main/java/com/swiftdrop/backend/modules/pricing/controller/ServicePriceController.java) |
| GET | `/api/v1/receipts/{trackingNumber}` | 1 | Contract/lifecycle cases | [ReceiptController.java](../backend/src/main/java/com/swiftdrop/backend/modules/receipt/controller/ReceiptController.java) |
| GET | `/api/v1/tracking/public/{trackingNumber}` | 3 | Contract/lifecycle cases | [PublicTrackingController.java](../backend/src/main/java/com/swiftdrop/backend/modules/tracking/controller/PublicTrackingController.java) |
| GET | `/api/v1/users/me` | 1 | Contract/lifecycle cases | [UserController.java](../backend/src/main/java/com/swiftdrop/backend/modules/auth/controller/UserController.java) |
| GET | `/api/v1/zones` | 2 | Contract/lifecycle cases | [ZoneController.java](../backend/src/main/java/com/swiftdrop/backend/modules/zone/controller/ZoneController.java) |
