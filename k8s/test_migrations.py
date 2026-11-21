"""Offline contracts only. No SQL is executed and no database is contacted."""
import re
import unittest

import migrations
import prepare


class MigrationTests(unittest.TestCase):
    def setUp(self):
        self.args = prepare.parser().parse_args(['--backend-image','backend:test','--frontend-image','frontend:test'])
        self.manifest, self.summary = prepare.render(self.args)
        self.scripts = next(x for x in self.manifest['items'] if x['metadata']['name']==self.summary['databaseScriptsConfigMap'])['data']

    def test_sql_splitter_preserves_strings_comments_and_do_blocks(self):
        self.assertEqual(len(migrations.statements("-- comment;\nSELECT ';'; DO $x$ BEGIN RAISE NOTICE 'semi;'; END; $x$; SELECT 2;")),3)
        with self.assertRaises(ValueError): migrations.statements('DO $open$ BEGIN;')
        with self.assertRaises(ValueError): migrations.statements('SELECT 1')

    def test_current_contract_includes_all_merged_modules(self):
        contract = migrations.catalog(self.scripts['001-schema.sql'])
        fields = {(x['table_name'],x['column_name']) for x in contract['columns']}
        for pair in [('merchants','business_name'),('merchants','registration_status'),('merchants','api_key'),
                     ('parcels','merchant_id'),('delivery_proofs','previous_proof_id'),('courier_assignments','scheduled_date'),
                     ('chat_messages','tool_result'),('notifications','event_id'),('report_snapshots','data_json')]:
            self.assertIn(pair,fields)
        self.assertEqual(len({x['table_name'] for x in contract['columns']}),19)
        self.assertEqual(self.scripts['assert-catalog.sql'],migrations.contract_sql(contract))

    def test_every_current_column_has_an_additive_ddl_path(self):
        actual = migrations.catalog(self.scripts['001-schema.sql'])
        available = {'columns': [column for name in migrations.MIGRATIONS for column in migrations.catalog(re.sub(r'^\\[^\n]*', '', self.scripts[name], flags=re.M))['columns']]}
        self.assertLessEqual({(x['table_name'],x['column_name']) for x in actual['columns']},
                             {(x['table_name'],x['column_name']) for x in available['columns']})

    def test_jpa_columns_exist_in_current_schema_contract(self):
        fields = {(x['table_name'],x['column_name']) for x in migrations.catalog(self.scripts['001-schema.sql'])['columns']}
        entities = 0
        for path in (prepare.ROOT/'backend/src/main/java').rglob('*.java'):
            text = path.read_text(encoding='utf-8-sig')
            table = re.search(r'@Table\(name\s*=\s*"([^"]+)"',text)
            if not table or '@Entity' not in text: continue
            entities += 1
            for match in re.finditer(r'private\s+(?!static\b)(?:final\s+)?[\w<>?,.]+\s+(\w+)\s*(?:=[^;]*)?;',text):
                prefix = text[text.rfind(';',0,match.start())+1:match.start()]
                if '@Transient' in prefix: continue
                explicit = re.findall(r'@(?:Column|JoinColumn)\([^)]*\bname\s*=\s*"([^"]+)"',prefix)
                name = explicit[-1] if explicit else re.sub(r'(?<!^)(?=[A-Z])','_',match[1]).lower()
                self.assertIn((table[1],name),fields,f'{path.name}: {match[1]} absent from schema')
        self.assertGreater(entities,10)

    def test_existing_schema_never_replays_baseline_or_seeds(self):
        driver = self.scripts['initialize.sql']
        fresh = driver[driver.index('\\if :bootstrap_new_database'):driver.index('\\else')]
        existing = driver[driver.index('\\else'):driver.index('\\endif')]
        self.assertIn('/db/001-schema.sql',fresh)
        self.assertNotIn('/db/001-schema.sql',existing)
        self.assertIn('preflight.sql',existing)
        self.assertNotRegex(migrations.without_comments(self.scripts['001-additive-schema.sql']),
                            r'\b(?:INSERT INTO|UPDATE\s+\w|DELETE FROM|TRUNCATE|DROP TABLE|DROP COLUMN)\b')

    def test_merchant_approval_names_and_secrets_are_not_invented(self):
        sql = migrations.without_comments(self.scripts['001-additive-schema.sql'])
        self.assertNotIn('UPDATE merchants',sql)
        self.assertNotIn('UPDATE users',sql)
        self.assertIn('width < 255',sql)
        self.assertIn('Legacy NULL values',self.scripts['003-integrity.sql'])
        self.assertIn('supply reviewed real values',self.scripts['003-integrity.sql'])
        self.assertLess(self.scripts['003-integrity.sql'].index('values are not logged'),self.scripts['003-integrity.sql'].index("EXECUTE format('CREATE UNIQUE INDEX"))

    def test_old_customer_links_and_proof_history_are_preserved(self):
        sql = self.scripts['001-additive-schema.sql']
        self.assertIn('customer_id DROP NOT NULL',sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS previous_proof_id UUID',sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500)',sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS scheduled_date DATE;',sql)

    def test_steps_and_ledger_are_transactional_and_ordered(self):
        runner = self.scripts['migrate.sql']
        positions = [runner.index('/db/'+name) for name in migrations.MIGRATIONS]
        self.assertEqual(positions,sorted(positions))
        for name in migrations.MIGRATIONS:
            entry = runner[runner.rfind('BEGIN;',0,runner.index('/db/'+name)):]
            self.assertLess(entry.index('/db/'+name),entry.index('INSERT INTO public.swiftdrop_schema_migrations'))
            self.assertLess(entry.index('INSERT INTO public.swiftdrop_schema_migrations'),entry.index('COMMIT;'))
        self.assertIn('checksum mismatch',runner)

    def test_marker_follows_current_catalog_assertion(self):
        driver = self.scripts['initialize.sql']
        self.assertLess(driver.index('migrate.sql'),driver.index('assert-catalog.sql'))
        self.assertLess(driver.index('assert-catalog.sql'),driver.index('INSERT INTO public.swiftdrop_deployment_schema'))
        self.assertIn('pg_advisory_lock',driver)
        self.assertIn("lock_timeout = '10s'",driver)

    def test_conversion_is_explicit_and_uses_canonical_script(self):
        guard = self.scripts['002-proof-user-ids.sql']
        self.assertIn("IS DISTINCT FROM 'true'",guard)
        self.assertIn('known_links <> 1',guard)
        self.assertIn('Unmapped proof courier identity',guard)
        body = self.scripts['legacy-proof-body.sql']
        self.assertNotIn('BEGIN;',body.splitlines())
        self.assertNotIn('COMMIT;',body.splitlines())
        self.assertIn('UPDATE public.delivery_proofs',body)
        self.assertEqual(body,migrations.proof_body(self.scripts['legacy-proof-source.sql']))

    def test_frozen_proof_body_change_changes_step_checksum(self):
        scripts = dict(self.scripts)
        _,before = migrations.runner(scripts)
        scripts['legacy-proof-body.sql'] += '\n-- reviewed source change\n'
        _,after = migrations.runner(scripts)
        self.assertNotEqual(before['002-proof-user-ids.sql'],after['002-proof-user-ids.sql'])
        self.assertEqual(before['001-additive-schema.sql'],after['001-additive-schema.sql'])

    def test_changed_transaction_wrapper_is_rejected(self):
        for sql in ['SELECT 1;','BEGIN;\nSELECT 1;','COMMIT;']:
            with self.assertRaises(ValueError): migrations.proof_body(sql)

    def test_review_flag_changes_job_not_immutable_migrations(self):
        self.args.reviewed_proof_conversion = True
        _,enabled = prepare.render(self.args)
        self.assertFalse(self.summary['reviewedProofConversion'])
        self.assertTrue(enabled['reviewedProofConversion'])
        self.assertNotEqual(self.summary['databaseJob'],enabled['databaseJob'])
        self.assertEqual(self.summary['migrationChecksums'],enabled['migrationChecksums'])

    def test_google_backend_and_frontend_build_use_same_public_id(self):
        self.args.google_client_id = 'test.apps.googleusercontent.com'
        manifest,summary = prepare.render(self.args)
        config = next(x for x in manifest['items'] if x['metadata']['name']=='swiftdrop-config')['data']
        self.assertEqual(config['GOOGLE_CLIENT_ID'],summary['frontendBuild']['args']['VITE_GOOGLE_CLIENT_ID'])
        self.assertEqual(self.summary['databaseJob'],summary['databaseJob'])

    def test_frontend_override_tracks_proxy_source_and_upload_limit(self):
        config = next(x for x in self.manifest['items'] if x['metadata']['name']=='swiftdrop-frontend-config')['data']['default.conf']
        source = (prepare.ROOT/'frontend/nginx.conf').read_text().replace('\r\n','\n')
        self.assertEqual(config,source.replace('listen 80;','listen 80;\n    client_max_body_size 6m;'))
        self.assertIn('location /ws',config)

    def test_private_secrets_never_enter_public_config(self):
        template = prepare.read_json(prepare.HERE/'secret.example.yaml')
        self.assertTrue(all(value=='' for value in template['stringData'].values()))
        self.assertIn('AI_API_KEY',template['stringData'])
        config = next(x for x in self.manifest['items'] if x['metadata']['name']=='swiftdrop-config')['data']
        for key in ['AI_API_KEY','JWT_SECRET','WEBHOOK_SECRET']: self.assertNotIn(key,config)


if __name__=='__main__': unittest.main()
