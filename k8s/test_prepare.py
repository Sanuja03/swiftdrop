"""Focused offline rendering tests. These do not test Kubernetes or execute SQL."""
import argparse
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import prepare


class PreparationTests(unittest.TestCase):
    def args(self, *extra):
        return prepare.parser().parse_args(['--backend-image', 'swiftdrop-backend:test-1',
                                           '--frontend-image', 'swiftdrop-frontend:test-1', *extra])

    def test_same_input_is_reproducible(self):
        self.assertEqual(prepare.render(self.args()), prepare.render(self.args()))

    def test_application_image_update_does_not_replay_database_job(self):
        first, first_summary = prepare.render(self.args())
        args = self.args()
        args.backend_image = 'ghcr.io/test/swiftdrop-backend:test-2'
        args.frontend_image = 'ghcr.io/test/swiftdrop-frontend:test-2'
        second, second_summary = prepare.render(args)
        self.assertEqual(first_summary['databaseJob'], second_summary['databaseJob'])
        self.assertNotEqual(first, second)
        backend = next(x for x in second['items'] if x['kind'] == 'Deployment' and x['metadata']['name'] == 'backend')
        self.assertEqual(prepare.pod_spec(backend)['containers'][0]['image'], args.backend_image)

    def test_sql_change_creates_new_job_and_marker(self):
        _, before = prepare.render(self.args())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'merchant.sql'
            path.write_text(prepare.SQL_SOURCES['003-merchant-webhook.sql'].read_text() + '\n-- Reviewed additive migration revision\n')
            with patch.dict(prepare.SQL_SOURCES, {'003-merchant-webhook.sql': path}):
                manifest, after = prepare.render(self.args())
        self.assertNotEqual(before['schemaBundleSha256'], after['schemaBundleSha256'])
        config = next(x for x in manifest['items'] if x['metadata']['name'] == after['databaseScriptsConfigMap'])
        self.assertTrue(config['immutable'])
        self.assertIn('Reviewed additive migration revision', config['data']['003-merchant-webhook.sql'])

    def test_revision_allows_reviewed_rerun_without_deletion(self):
        manifest, before = prepare.render(self.args())
        revised, after = prepare.render(self.args('--revision', 'reviewed-2'))
        self.assertNotEqual(before['databaseJob'], after['databaseJob'])
        old = next(x for x in manifest['items'] if x['metadata']['name'] == before['databaseScriptsConfigMap'])
        new = next(x for x in revised['items'] if x['metadata']['name'] == after['databaseScriptsConfigMap'])
        self.assertEqual(old['data']['001-schema.sql'], new['data']['001-schema.sql'])

    def test_job_template_change_uses_a_new_job_identity(self):
        _, before = prepare.render(self.args())
        _, after = prepare.render(self.args('--image-pull-secret', 'private-registry'))
        self.assertNotEqual(before['databaseJob'], after['databaseJob'])

    def test_namespace_registry_secret_and_storage_class_propagate(self):
        manifest, summary = prepare.render(self.args('--namespace', 'swiftdrop-test', '--image-pull-secret', 'ghcr-pull', '--storage-class', 'demo-storage'))
        for obj in manifest['items']:
            if obj['kind'] == 'Namespace':
                self.assertEqual(obj['metadata']['name'], 'swiftdrop-test')
            else:
                self.assertEqual(obj['metadata']['namespace'], 'swiftdrop-test')
            if obj['kind'] in ('StatefulSet', 'Deployment', 'Job'):
                self.assertEqual(prepare.pod_spec(obj)['imagePullSecrets'], [{'name': 'ghcr-pull'}])
            if obj['kind'] == 'StatefulSet':
                self.assertEqual(obj['spec']['volumeClaimTemplates'][0]['spec']['storageClassName'], 'demo-storage')

    def test_infrastructure_image_pin_reaches_wait_and_migration_containers(self):
        image = 'postgres@sha256:' + 'a' * 64
        manifest, summary = prepare.render(self.args('--postgres-image', image))
        postgres_containers = [c for obj in manifest['items'] if obj['kind'] in ('Job', 'Deployment', 'StatefulSet')
                               for c in prepare.pod_spec(obj).get('initContainers', []) + prepare.pod_spec(obj)['containers']
                               if c['name'] in ('postgres', 'wait-for-schema', 'wait-for-postgres', 'apply-schema')]
        self.assertEqual(len(postgres_containers), 4)
        self.assertTrue(all(c['image'] == image for c in postgres_containers))

    def test_public_render_has_no_secrets_and_copies_source_sql(self):
        manifest, summary = prepare.render(self.args())
        self.assertFalse(any(obj['kind'] == 'Secret' for obj in manifest['items']))
        scripts = next(obj for obj in manifest['items'] if obj['metadata']['name'] == summary['databaseScriptsConfigMap'])['data']
        for key, path in prepare.SQL_SOURCES.items():
            self.assertEqual(scripts[key], path.read_text(encoding='utf-8').replace('\r\n', '\n'))

    def test_unversioned_or_placeholder_images_are_rejected(self):
        for image in ['swiftdrop-backend', 'swiftdrop-backend:latest', 'repo:', 'repo:REPLACE_WITH_TAG', 'repo@sha256:bad', 'repo:bad tag']:
            with self.subTest(image=image):
                with self.assertRaises(argparse.ArgumentTypeError):
                    prepare.validate_image(image)


if __name__ == '__main__':
    unittest.main()
