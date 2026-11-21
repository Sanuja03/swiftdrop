-- Upgrade the existing Supabase delivery_proofs table before deploying the new backend.
-- The original courier_id foreign key pointed to couriers.id.
BEGIN;

LOCK TABLE public.delivery_proofs IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    old_constraint name;
BEGIN
    SELECT c.conname INTO old_constraint
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'courier_id'
    WHERE c.conrelid = 'public.delivery_proofs'::regclass
      AND c.confrelid = 'public.couriers'::regclass
      AND c.contype = 'f'
      AND c.conkey = ARRAY[a.attnum];

    IF old_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.delivery_proofs DROP CONSTRAINT %I', old_constraint);

        UPDATE public.delivery_proofs AS proof
        SET courier_id = courier.user_id
        FROM public.couriers AS courier
        WHERE proof.courier_id = courier.id;

        ALTER TABLE public.delivery_proofs
            ADD CONSTRAINT delivery_proofs_courier_id_fkey
            FOREIGN KEY (courier_id) REFERENCES public.users(id);
    END IF;
END;
$$;

COMMIT;
