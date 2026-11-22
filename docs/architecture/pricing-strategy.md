# Pricing: Strategy pattern

Owner: Booking & Pricing. Code: `backend/src/main/java/com/swiftdrop/backend/modules/pricing`.

## Why a strategy

SwiftDrop sells three services (`ServiceType`: `STANDARD`, `EXPRESS`, `SAME_DAY`). They share one
fee formula but differ in their rules: how soon they deliver, and whether a route is allowed at
all (Same Day only works inside one zone). Each service's rules live in its own class, so a new
service is a new class instead of another branch in a `switch` (Open/Closed principle).

## The classes

| Class | Role |
|---|---|
| `strategy/PricingStrategy` | The interface. `supports()` names the `ServiceType` it prices, `calculate(PricingContext)` returns a `FeeBreakdown`, `estimateDeliveryDate(LocalDate)` returns the promised delivery date. `INCLUDED_WEIGHT_KG` (1 kg) is the weight the base fee already covers. |
| `strategy/AbstractPricingStrategy` | The shared formula in `breakdown(...)`, used by all three strategies (no duplicated fee logic). |
| `strategy/StandardPricingStrategy` | `STANDARD`: delivery in 3 working days. |
| `strategy/ExpressPricingStrategy` | `EXPRESS`: delivery on the next working day. |
| `strategy/SameDayPricingStrategy` | `SAME_DAY`: delivery the same day; throws `PricingException.sameDayAcrossZones()` when pickup and drop-off zones differ. |
| `strategy/DeliveryCalendar` | Working days (Monday to Friday) in `Asia/Colombo`; delivery is promised by 18:00 on the delivery day. |
| `dto/PricingContext` | Input for a strategy: the `PricingRule`, pickup and drop-off `Zone`, weight and booking date. |
| `dto/FeeBreakdown` | Output: base fee, weight charge, zone charge, total, currency (LKR) and estimated delivery time. |
| `service/PricingService` | The context class that chooses and runs a strategy. |

The prices themselves are data, not code: each service has a `PricingRule` row (`pricing_rules`
table: base fee, per kg fee, inter-zone fee, active) and each `Zone` has a surcharge. Admins
change both at runtime (`AdminPricingRuleController`, `AdminZoneController`), so a price change
never needs a new strategy.

## The formula (`AbstractPricingStrategy.breakdown`)

```
weight charge = max(weight - 1 kg, 0) x per kg fee
zone charge   = drop-off zone surcharge                      (same zone)
              = inter-zone fee + drop-off zone surcharge      (different zones)
total         = base fee + weight charge + zone charge        (rounded to 2 decimals)
```

## How an estimate picks a strategy

1. Spring creates every `@Component` that implements `PricingStrategy`.
2. `PricingService`'s constructor receives them as a `List<PricingStrategy>` and indexes them in a
   `Map<ServiceType, PricingStrategy>` by `supports()`.
3. `PricingService.estimate(EstimateRequest)` checks the weight against the parcel size, loads
   the two active zones and the active `PricingRule` for the requested service type, then calls
   `strategies.get(serviceType).calculate(...)`. A service with no active rule or no strategy is
   refused with `PricingException.inactiveRule(...)`.
4. `PricingService.servicePrices()` uses the same map to show each service's starting price.

Every price in the system goes through this one path: the customer estimate
(`POST /api/v1/parcels/estimate`, `PricingController`), the service list
(`GET /api/v1/pricing/services`, `ServicePriceController`), customer bookings (`BookingService`),
merchant orders and fee quotes (`MerchantPricingService`, `MerchantOrderService`) and the AI
assistant (`AssistantToolServiceImpl`). So the price a customer sees before confirming is the
price that is saved.

## Adding a new service type

Example: an `OVERNIGHT` service.

1. Add `OVERNIGHT` to `ServiceType` (`modules/parcel/entity/ServiceType.java`) with its label.
2. Add `OvernightPricingStrategy extends AbstractPricingStrategy`, annotated `@Component`, with
   `supports()` returning `ServiceType.OVERNIGHT`, `calculate(...)` (call `breakdown(context)`
   after any service-specific checks) and `estimateDeliveryDate(...)`.
3. Add its starting price to `pricing_rules` in `schema.sql` with an idempotent
   `INSERT ... ON CONFLICT DO NOTHING` (admins can then change it in the app).
4. Add the option to the booking form (`frontend/src/shared/domain/serviceOptions.js`).

`PricingService` and the controllers do not change.

## Tests

`PricingStrategiesTest` (each strategy's fee and delivery date), `PricingServiceTest` and
`ServicePricesTest` (strategy selection, inactive rules and zones), `PricingControllerTest`.
