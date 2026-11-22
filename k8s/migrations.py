"""Offline SQL packaging and catalog contracts. Never connects to a database.

Versioned migrations are immutable snapshots; current schema.sql is used only for
fresh bootstrap and a read-only post-migration catalog assertion.
"""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
MIGRATIONS = ['001-additive-schema.sql', '002-proof-user-ids.sql', '003-integrity.sql']


def without_comments(sql):
    token = r"'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|--[^\n]*|/\*[\s\S]*?\*/"
    return re.sub(token, lambda m: '' if m[0].startswith(('--', '/*')) else m[0], sql)


def statements(sql):
    """Split top-level SQL, preserving quoted strings and dollar-quoted DO bodies."""
    tokens = re.compile(r"--[^\n]*|/\*[\s\S]*?\*/|'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|\$(?:[A-Za-z_]\w*)?\$|;")
    start, dollar, result = 0, None, []
    for match in tokens.finditer(sql):
        token = match[0]
        if dollar:
            if token == dollar:
                dollar = None
        elif token.startswith('$'):
            dollar = token
        elif token == ';':
            item = without_comments(sql[start:match.end()]).strip()
            if item:
                result.append(item)
            start = match.end()
    if dollar:
        raise ValueError('Unclosed SQL dollar quote')
    if without_comments(sql[start:]).strip():
        raise ValueError('SQL statement is missing a final semicolon')
    return result


def catalog(sql):
    columns, foreign, unique, indexes = {}, [], [], []

    def column(table, name, definition):
        kind = re.match(r'(UUID|VARCHAR\((\d+)\)|NUMERIC\((\d+),\s*(\d+)\)|TIMESTAMP WITH TIME ZONE|TIMESTAMPTZ|BIGINT|INTEGER|INT|BOOLEAN|DATE|TEXT)', definition, re.I)
        if not kind:
            raise ValueError(f'Unrecognized SQL type: {table}.{name}: {definition}')
        raw = kind[1].upper()
        dtype = ('character varying' if raw.startswith('VARCHAR') else 'numeric' if raw.startswith('NUMERIC')
                 else 'timestamp with time zone' if raw in ('TIMESTAMPTZ', 'TIMESTAMP WITH TIME ZONE')
                 else 'integer' if raw in ('INT', 'INTEGER') else raw.lower())
        item = {'table_name': table, 'column_name': name, 'data_type': dtype,
                'max_length': int(kind[2]) if kind[2] else None,
                'precision': int(kind[3]) if kind[3] else None, 'scale': int(kind[4]) if kind[4] else None,
                'required': bool(re.search(r'\b(?:NOT NULL|PRIMARY KEY)\b', definition))}
        columns.setdefault((table, name), item)
        reference = re.search(r'REFERENCES\s+(?:public\.)?(\w+)\s*\((\w+)\)(?:\s+ON DELETE\s+(CASCADE|RESTRICT|SET NULL))?', definition, re.I)
        if reference:
            fk = {'table_name': table, 'column_name': name, 'target_table': reference[1], 'target_column': reference[2],
                  'on_delete': (reference[3] or 'NO ACTION').upper()}
            if fk not in foreign:
                foreign.append(fk)
        if 'UNIQUE' in definition or 'PRIMARY KEY' in definition:
            unique.append({'table_name': table, 'column_name': name})

    for statement in statements(sql):
        create = re.match(r'CREATE TABLE IF NOT EXISTS (?:public\.)?(\w+)\s*\(([\s\S]*)\);', statement, re.I)
        if create:
            for line in create[2].splitlines():
                match = re.match(r'\s*(\w+)\s+(UUID|VARCHAR|NUMERIC|TIMESTAMP|TIMESTAMPTZ|BIGINT|INTEGER|INT|BOOLEAN|DATE|TEXT)\b(.*)', line, re.I)
                if match:
                    column(create[1], match[1], match[2] + match[3].rstrip(','))
        add = re.match(r'ALTER TABLE (?:public\.)?(\w+) ADD COLUMN IF NOT EXISTS (\w+)\s+([\s\S]*);', statement, re.I)
        if add:
            column(add[1], add[2], add[3])
        index = re.match(r'CREATE (UNIQUE )?INDEX IF NOT EXISTS (\w+)\s+ON (?:public\.)?(\w+)', statement, re.I)
        if index:
            indexes.append({'name': index[2], 'table_name': index[3], 'unique': bool(index[1])})
    return {'columns': list(columns.values()), 'foreign_keys': foreign, 'unique': unique, 'indexes': indexes}


def contract_sql(contract, enforce=False):
    """Generate SQL that rejects incompatible catalogs; optionally add missing constraints.

    No rows, prices, credentials, approval states or ownership links are inferred.
    The enforcing form is frozen in migration 003; runtime assertion is read-only.
    """
    literal = json.dumps(contract, separators=(',', ':')).replace("'", "''")
    action = 'true' if enforce else 'false'
    return f"""-- Generated catalog contract; read-only unless enforce=true.
DO $contract$
DECLARE spec jsonb := '{literal}'::jsonb;
        expected jsonb; actual record; constraint_row record; bad_rows boolean;
        rel regclass; att smallint; target_att smallint; matching boolean; fk_name text;
        enforce boolean := {action};
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
"""


def proof_body(source):
    if len(re.findall(r'^BEGIN;[ \t]*$', source, re.M)) != 1 or len(re.findall(r'^COMMIT;[ \t]*$', source, re.M)) != 1:
        raise ValueError('Review changed delivery-proof transaction boundaries')
    result = re.sub(r'^BEGIN;\s*$', '', source, flags=re.M)
    result = re.sub(r'^COMMIT;\s*$', '', result, flags=re.M)
    if result == source or re.search(r'^(?:BEGIN|COMMIT);', result, re.M):
        raise ValueError('Review changed delivery-proof transaction boundaries')
    return result


def proof_source_matches(frozen_body, source):
    """Ignore boundary whitespace for review only; checksums still use exact SQL."""
    return frozen_body.strip() == proof_body(source).strip()


def runner(scripts):
    result = ["-- Generated immutable migration runner; each step and its ledger insert commit together."]
    checksums = {}
    for name in MIGRATIONS:
        dependencies = scripts[name] + (scripts['legacy-proof-body.sql'] if name == '002-proof-user-ids.sql' else '')
        checksum = hashlib.sha256(dependencies.encode()).hexdigest()
        checksums[name] = checksum
        version = name[:3]
        result.append(f"""SELECT EXISTS (SELECT 1 FROM public.swiftdrop_schema_migrations WHERE version='{version}') AS applied \\gset
\\if :applied
SELECT checksum='{checksum}' AS checksum_matches FROM public.swiftdrop_schema_migrations WHERE version='{version}' \\gset
\\if :checksum_matches
\\echo 'Migration {version} already applied with matching checksum.'
\\else
DO $error$ BEGIN RAISE EXCEPTION 'Migration {version} checksum mismatch: restore the applied file; add a new migration instead'; END $error$;
\\endif
\\else
BEGIN;
\\ir /db/{name}
INSERT INTO public.swiftdrop_schema_migrations(version, checksum) VALUES ('{version}','{checksum}');
COMMIT;
\\endif""")
    return '\n'.join(result) + '\n', checksums
