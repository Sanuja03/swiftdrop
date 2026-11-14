-- Run after the shared users and parcels tables exist.
-- Agree on the current-assignment mapping with the dispatch module first.
BEGIN;

CREATE TABLE IF NOT EXISTS public.couriers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES public.users(id)
);

CREATE TABLE IF NOT EXISTS public.courier_assignments (
    parcel_id UUID PRIMARY KEY REFERENCES public.parcels(id),
    courier_id UUID NOT NULL REFERENCES public.couriers(id),
    scheduled_date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_courier_assignments_schedule
    ON public.courier_assignments (courier_id, scheduled_date, parcel_id);

CREATE TABLE IF NOT EXISTS public.delivery_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id UUID NOT NULL REFERENCES public.parcels(id),
    courier_id UUID NOT NULL REFERENCES public.couriers(id),
    recipient_name VARCHAR(150),
    photo_object_key VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_proof_id UUID REFERENCES public.delivery_proofs(id),
    change_reason VARCHAR(500),
    CONSTRAINT chk_proof_content CHECK (
        NULLIF(BTRIM(recipient_name), '') IS NOT NULL
        OR NULLIF(BTRIM(photo_object_key), '') IS NOT NULL
    ),
    CONSTRAINT chk_proof_change_reason CHECK (
        previous_proof_id IS NULL OR NULLIF(BTRIM(change_reason), '') IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_delivery_proofs_parcel_created
    ON public.delivery_proofs (parcel_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_proofs_initial
    ON public.delivery_proofs (parcel_id) WHERE previous_proof_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_proofs_successor
    ON public.delivery_proofs (previous_proof_id) WHERE previous_proof_id IS NOT NULL;

ALTER TABLE public.couriers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courier_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_proofs ENABLE ROW LEVEL SECURITY;

COMMIT;
