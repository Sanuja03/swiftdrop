-- Existing databases must have the established identity/base-parcel contract.
-- Generated catalog contract; read-only unless enforce=true.
DO $contract$
DECLARE spec jsonb := '{"columns":[{"table_name":"users","column_name":"id","data_type":"uuid","max_length":null,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"email","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"password_hash","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":false},{"table_name":"users","column_name":"full_name","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"phone","data_type":"character varying","max_length":50,"precision":null,"scale":null,"required":false},{"table_name":"users","column_name":"role","data_type":"character varying","max_length":50,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"status","data_type":"character varying","max_length":50,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"provider","data_type":"character varying","max_length":50,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"provider_id","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":false},{"table_name":"users","column_name":"email_verified","data_type":"boolean","max_length":null,"precision":null,"scale":null,"required":true},{"table_name":"users","column_name":"created_at","data_type":"timestamp with time zone","max_length":null,"precision":null,"scale":null,"required":false},{"table_name":"users","column_name":"updated_at","data_type":"timestamp with time zone","max_length":null,"precision":null,"scale":null,"required":false},{"table_name":"parcels","column_name":"id","data_type":"uuid","max_length":null,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"tracking_number","data_type":"character varying","max_length":32,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"customer_id","data_type":"uuid","max_length":null,"precision":null,"scale":null,"required":false},{"table_name":"parcels","column_name":"sender_name","data_type":"character varying","max_length":120,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"recipient_name","data_type":"character varying","max_length":120,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"pickup_address","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"pickup_city","data_type":"character varying","max_length":80,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"dropoff_address","data_type":"character varying","max_length":255,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"dropoff_city","data_type":"character varying","max_length":80,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"service_type","data_type":"character varying","max_length":20,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"weight_kg","data_type":"numeric","max_length":null,"precision":8,"scale":2,"required":true},{"table_name":"parcels","column_name":"fee","data_type":"numeric","max_length":null,"precision":12,"scale":2,"required":true},{"table_name":"parcels","column_name":"status","data_type":"character varying","max_length":30,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"estimated_delivery_at","data_type":"timestamp with time zone","max_length":null,"precision":null,"scale":null,"required":false},{"table_name":"parcels","column_name":"created_at","data_type":"timestamp with time zone","max_length":null,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"updated_at","data_type":"timestamp with time zone","max_length":null,"precision":null,"scale":null,"required":true},{"table_name":"parcels","column_name":"version","data_type":"bigint","max_length":null,"precision":null,"scale":null,"required":true}],"foreign_keys":[],"unique":[],"indexes":[]}'::jsonb;
        expected jsonb; actual record; constraint_row record; bad_rows boolean;
        rel regclass; att smallint; target_att smallint; matching boolean; fk_name text;
        enforce boolean := false;
BEGIN
    FOR expected IN SELECT * FROM jsonb_array_elements(spec->'columns') LOOP
        SELECT * INTO actual FROM information_schema.columns
        WHERE table_schema='public' AND table_name=expected->>'table_name' AND column_name=expected->>'column_name';
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Missing required column %.%; add a reviewed migration', expected->>'table_name', expected->>'column_name';
        END IF;
        IF actual.data_type <> expected->>'data_type'
           AND NOT (expected->>'data_type'='character varying' AND actual.data_type='text') THEN
            RAISE EXCEPTION 'Incompatible type for %.%; manual review required', expected->>'table_name', expected->>'column_name';
        END IF;
        IF expected->>'max_length' IS NOT NULL AND actual.character_maximum_length IS NOT NULL
           AND actual.character_maximum_length < (expected->>'max_length')::int THEN
            RAISE EXCEPTION 'Column %.% is too short; review a widening migration', expected->>'table_name', expected->>'column_name';
        END IF;
        IF expected->>'precision' IS NOT NULL AND actual.numeric_precision IS NOT NULL
           AND (actual.numeric_scale < (expected->>'scale')::int OR
                actual.numeric_precision-actual.numeric_scale < (expected->>'precision')::int-(expected->>'scale')::int) THEN
            RAISE EXCEPTION 'Numeric range too small for %.%; manual review required', expected->>'table_name', expected->>'column_name';
        END IF;
        IF (expected->>'required')::boolean AND actual.is_nullable='YES' THEN
            IF NOT enforce THEN
                RAISE EXCEPTION 'Missing NOT NULL on %.%; review migration', expected->>'table_name', expected->>'column_name';
            END IF;
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I WHERE %I IS NULL)', expected->>'table_name', expected->>'column_name') INTO bad_rows;
            IF bad_rows THEN
                RAISE EXCEPTION 'Legacy NULL values in %.%; supply reviewed real values before retrying (no values were invented)', expected->>'table_name', expected->>'column_name';
            END IF;
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I SET NOT NULL', expected->>'table_name', expected->>'column_name');
        END IF;
    END LOOP;
    FOR expected IN SELECT * FROM jsonb_array_elements(spec->'foreign_keys') LOOP
        rel := format('public.%I', expected->>'table_name')::regclass;
        SELECT attnum INTO att FROM pg_attribute WHERE attrelid=rel AND attname=expected->>'column_name' AND NOT attisdropped;
        SELECT attnum INTO target_att FROM pg_attribute WHERE attrelid=format('public.%I', expected->>'target_table')::regclass AND attname=expected->>'target_column' AND NOT attisdropped;
        matching := false;
        FOR constraint_row IN SELECT * FROM pg_constraint WHERE conrelid=rel AND contype='f' AND conkey=ARRAY[att] LOOP
            IF constraint_row.confrelid <> format('public.%I', expected->>'target_table')::regclass
               OR constraint_row.confkey <> ARRAY[target_att]
               OR constraint_row.confdeltype::text <> CASE expected->>'on_delete' WHEN 'CASCADE' THEN 'c' WHEN 'RESTRICT' THEN 'r' WHEN 'SET NULL' THEN 'n' ELSE 'a' END THEN
                RAISE EXCEPTION 'Incompatible foreign key %.%; reconcile identity/delete policy manually', expected->>'table_name', expected->>'column_name';
            END IF;
            matching := true;
            IF NOT constraint_row.convalidated THEN
                IF NOT enforce THEN RAISE EXCEPTION 'Unvalidated foreign key on %', rel; END IF;
                EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', rel, constraint_row.conname);
            END IF;
        END LOOP;
        IF NOT matching THEN
            IF NOT enforce THEN RAISE EXCEPTION 'Missing foreign key %.%', expected->>'table_name', expected->>'column_name'; END IF;
            fk_name := 'k8s_fk_' || (expected->>'table_name') || '_' || (expected->>'column_name');
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.%I (%I) ON DELETE %s NOT VALID', rel, fk_name, expected->>'column_name', expected->>'target_table', expected->>'target_column', expected->>'on_delete');
            EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', rel, fk_name);
        END IF;
    END LOOP;
    FOR expected IN SELECT * FROM jsonb_array_elements(spec->'unique') LOOP
        rel := format('public.%I', expected->>'table_name')::regclass;
        SELECT attnum INTO att FROM pg_attribute WHERE attrelid=rel AND attname=expected->>'column_name' AND NOT attisdropped;
        SELECT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid=rel AND i.indisunique AND i.indisvalid
                       AND i.indkey::text=att::text AND i.indexprs IS NULL
                       AND (i.indpred IS NULL OR regexp_replace(pg_get_expr(i.indpred,i.indrelid),'[()[:space:]]','','g')=(expected->>'column_name')||'ISNOTNULL')) INTO matching;
        IF NOT matching THEN
            IF NOT enforce THEN RAISE EXCEPTION 'Missing uniqueness %.%', expected->>'table_name', expected->>'column_name'; END IF;
            -- Check first so PostgreSQL does not print a duplicate API key in index-error DETAIL.
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM %s WHERE %I IS NOT NULL GROUP BY %I HAVING count(*) > 1)', rel, expected->>'column_name', expected->>'column_name') INTO bad_rows;
            IF bad_rows THEN RAISE EXCEPTION 'Duplicate values in %.%; reconcile manually (values are not logged)', expected->>'table_name', expected->>'column_name'; END IF;
            EXECUTE format('CREATE UNIQUE INDEX %I ON %s (%I)', 'k8s_uq_'||(expected->>'table_name')||'_'||(expected->>'column_name'), rel, expected->>'column_name');
        END IF;
    END LOOP;
    FOR expected IN SELECT * FROM jsonb_array_elements(spec->'indexes') LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                       WHERE n.nspname='public' AND c.relname=expected->>'name' AND i.indisvalid
                       AND i.indrelid=format('public.%I',expected->>'table_name')::regclass AND i.indisunique=(expected->>'unique')::boolean) THEN
            RAISE EXCEPTION 'Missing/invalid index %; manual review required', expected->>'name';
        END IF;
    END LOOP;
END;
$contract$;
