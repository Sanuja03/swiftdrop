-- Version 002. The driver owns the transaction and advisory lock.
-- Only the repository's explicit couriers.id -> users.id migration may rewrite IDs.
LOCK TABLE public.delivery_proofs IN ACCESS EXCLUSIVE MODE;
DO $review$
DECLARE links integer; old_links integer; known_links integer;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE c.confrelid='public.couriers'::regclass),
           count(*) FILTER (WHERE c.confrelid IN ('public.users'::regclass, 'public.couriers'::regclass))
      INTO links, old_links, known_links
    FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='courier_id'
    WHERE c.conrelid='public.delivery_proofs'::regclass AND c.contype='f'
      AND c.conkey=ARRAY[a.attnum];
    IF links <> 1 OR known_links <> 1 THEN
        RAISE EXCEPTION 'Delivery-proof courier identity has no single known foreign key; reconcile it manually (do not guess IDs)';
    END IF;
    IF old_links = 1 THEN
        IF current_setting('swiftdrop.reviewed_proof_conversion', true) IS DISTINCT FROM 'true' THEN
            RAISE EXCEPTION 'Legacy proof FK references couriers.id. Back up/review identities, stop old writers, then rerender with --reviewed-proof-conversion';
        END IF;
        IF EXISTS (SELECT 1 FROM public.couriers GROUP BY user_id HAVING count(*) > 1) THEN
            RAISE EXCEPTION 'Duplicate courier/user links require manual review before proof conversion';
        END IF;
        IF EXISTS (SELECT 1 FROM public.delivery_proofs p LEFT JOIN public.couriers c ON c.id=p.courier_id
                   LEFT JOIN public.users u ON u.id=c.user_id WHERE c.id IS NULL OR u.id IS NULL) THEN
            RAISE EXCEPTION 'Unmapped proof courier identity: conversion stopped before any IDs were changed';
        END IF;
    END IF;
END;
$review$;

-- The frozen canonical body omits only its outer BEGIN/COMMIT.
-- Its table lock, guarded UPDATE and FK replacement remain within THIS step's transaction.
\ir /db/legacy-proof-body.sql
