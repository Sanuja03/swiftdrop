"""Render an offline Kubernetes bundle from tracked manifests and current repository SQL.

No network calls, credentials, kubectl calls or database connections are made.
The .yaml sources intentionally use JSON syntax (a YAML 1.2 subset).
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import migrations

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SOURCES = ['namespace.yaml', 'configmap.yaml', 'postgres.yaml', 'rabbitmq.yaml',
           'db-init.yaml', 'backend.yaml', 'frontend.yaml']
SQL_SOURCES = {
    '001-schema.sql': ROOT / 'backend/src/main/resources/schema.sql',
    'legacy-proof-source.sql': ROOT / 'backend/src/main/resources/db/scripts/delivery_proofs_use_user_ids.sql',
    'legacy-proof-body.sql': HERE / 'db/migrations/proof-user-ids-body.sql',
    'preflight.sql': HERE / 'db/preflight.sql',
    'initialize.sql': HERE / 'db/initialize.sql',
    'wait-for-schema.sh': HERE / 'db/wait-for-schema.sh',
    **{name: HERE / 'db/migrations' / name for name in migrations.MIGRATIONS},
}


def read_json(path):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'Duplicate key {key!r} in {path}')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique_keys)


def objects(document):
    return document['items'] if document.get('kind') == 'List' else [document]


def pod_spec(resource):
    return resource['spec']['template']['spec']


def validate_image(value):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._:/@-]*', value):
        raise argparse.ArgumentTypeError('Use a concrete registry/name:tag or registry/name@sha256:digest')
    if any(word in value.upper() for word in ('REPLACE', 'CHANGE_ME')):
        raise argparse.ArgumentTypeError('Replace the image placeholder with a real build tag')
    if '@' in value:
        if not re.search(r'@sha256:[0-9a-f]{64}$', value):
            raise argparse.ArgumentTypeError('Image digest must be sha256 with 64 lowercase hex characters')
    else:
        tagged_name = value.rsplit('/', 1)[-1]
        if ':' not in tagged_name or value.endswith(':latest'):
            raise argparse.ArgumentTypeError('Specify an explicit release/commit tag; latest is not accepted')
        if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', tagged_name.split(':', 1)[1]):
            raise argparse.ArgumentTypeError('Image tag is empty or invalid')
    return value


def render(args):
    resources = [obj for source in SOURCES for obj in objects(read_json(HERE / source))]
    config = next(obj for obj in resources if obj['kind'] == 'ConfigMap')
    config['data']['DB_REVIEWED_PROOF_CONVERSION'] = str(args.reviewed_proof_conversion).lower()
    if args.google_client_id is not None:
        config['data']['GOOGLE_CLIENT_ID'] = args.google_client_id
    scripts = {key: path.read_text(encoding='utf-8').replace('\r\n', '\n') for key, path in SQL_SOURCES.items()}
    scripts['assert-catalog.sql'] = migrations.contract_sql(migrations.catalog(scripts['001-schema.sql']))
    scripts['migrate.sql'], migration_checksums = migrations.runner(scripts)
    job_template = read_json(HERE / 'db-init.yaml')
    # Salt a Job for intentional reruns without deleting completed Jobs or touching volumes.
    identity = {'scripts': scripts, 'job': job_template, 'postgresImage': args.postgres_image,
                'imagePullSecret': args.image_pull_secret,
                'reviewedProofConversion': args.reviewed_proof_conversion,
                'database': config['data']['POSTGRES_DB'], 'revision': args.revision}
    bundle_hash = hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    config_name = 'swiftdrop-db-' + bundle_hash[:12]
    job_name = 'swiftdrop-db-init-' + bundle_hash[:12]
    scripts['bundle-sha256'] = bundle_hash
    script_config = {
        'apiVersion': 'v1', 'kind': 'ConfigMap',
        'metadata': {'name': config_name, 'namespace': args.namespace,
                     'labels': {'app.kubernetes.io/name': 'db-init', 'app.kubernetes.io/part-of': 'swiftdrop'}},
        'immutable': True, 'data': scripts,
    }

    def replace_script_ref(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key in ('configMap', 'configMapKeyRef') and child.get('name') == 'swiftdrop-db-scripts':
                    child['name'] = config_name
                replace_script_ref(child)
        elif isinstance(value, list):
            for child in value:
                replace_script_ref(child)

    config_hash = hashlib.sha256(json.dumps(config['data'], sort_keys=True).encode()).hexdigest()
    nginx_source = (ROOT / 'frontend/nginx.conf').read_text(encoding='utf-8').replace('\r\n', '\n')
    if nginx_source.count('listen 80;') != 1:
        raise ValueError('Frontend Nginx listen contract changed; review the Kubernetes override')
    nginx = nginx_source.replace('listen 80;', 'listen 80;\n    client_max_body_size 6m;')
    nginx_hash = hashlib.sha256(nginx.encode()).hexdigest()
    frontend_config = {'apiVersion': 'v1', 'kind': 'ConfigMap',
                       'metadata': {'name': 'swiftdrop-frontend-config', 'namespace': args.namespace,
                                    'labels': {'app.kubernetes.io/part-of': 'swiftdrop'}},
                       'data': {'default.conf': nginx}}
    for obj in resources:
        if obj['kind'] == 'Namespace':
            obj['metadata']['name'] = args.namespace
        else:
            obj['metadata']['namespace'] = args.namespace
        if obj['kind'] == 'Job':
            obj['metadata']['name'] = job_name
        replace_script_ref(obj)
        if obj['kind'] in ('Deployment', 'StatefulSet', 'Job'):
            spec = pod_spec(obj)
            if args.image_pull_secret:
                spec['imagePullSecrets'] = [{'name': args.image_pull_secret}]
            for container in spec.get('initContainers', []) + spec['containers']:
                image = container['image']
                if image.startswith('postgres:'):
                    container['image'] = args.postgres_image
                elif image.startswith('rabbitmq:'):
                    container['image'] = args.rabbitmq_image
                elif container['name'] == 'backend':
                    container['image'] = args.backend_image
                elif container['name'] == 'frontend':
                    container['image'] = args.frontend_image
            if obj['kind'] == 'Deployment' and obj['metadata']['name'] == 'backend':
                obj['spec']['template']['metadata']['annotations'] = {'swiftdrop/config-checksum': config_hash}
            if obj['kind'] == 'Deployment' and obj['metadata']['name'] == 'frontend':
                obj['spec']['template']['metadata']['annotations'] = {
                    'swiftdrop/nginx-checksum': nginx_hash,
                    'swiftdrop/google-client-id': config['data']['GOOGLE_CLIENT_ID'],
                }
            if obj['kind'] == 'StatefulSet' and args.storage_class:
                for claim in obj['spec']['volumeClaimTemplates']:
                    claim['spec']['storageClassName'] = args.storage_class

    resources.append(script_config)
    resources.append(frontend_config)
    order = {'Namespace': 0, 'ConfigMap': 1, 'Service': 2, 'StatefulSet': 3, 'Job': 4, 'Deployment': 5}
    resources.sort(key=lambda obj: (order[obj['kind']], obj['metadata']['name']))
    return {'apiVersion': 'v1', 'kind': 'List', 'items': resources}, {
        'namespace': args.namespace, 'schemaBundleSha256': bundle_hash,
        'databaseJob': job_name, 'databaseScriptsConfigMap': config_name,
        'backendImage': args.backend_image, 'frontendImage': args.frontend_image,
        'postgresImage': args.postgres_image, 'rabbitmqImage': args.rabbitmq_image,
        'migrationChecksums': migration_checksums,
        'reviewedProofConversion': args.reviewed_proof_conversion,
        'proofSourceMatchesFrozen': migrations.proof_source_matches(scripts['legacy-proof-body.sql'], scripts['legacy-proof-source.sql']),
        'frontendBuild': {'dockerfile': 'k8s/frontend.Dockerfile', 'context': 'frontend',
                          'args': {'VITE_GOOGLE_CLIENT_ID': config['data']['GOOGLE_CLIENT_ID']}},
    }


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('--backend-image', required=True, type=validate_image)
    result.add_argument('--frontend-image', required=True, type=validate_image)
    result.add_argument('--postgres-image', default='postgres:17-alpine', type=validate_image)
    result.add_argument('--rabbitmq-image', default='rabbitmq:4.4.0-management', type=validate_image)
    result.add_argument('--namespace', default='swiftdrop')
    result.add_argument('--storage-class', help='Omit to use the cluster default StorageClass')
    result.add_argument('--image-pull-secret', help='Existing registry Secret name in the selected namespace')
    result.add_argument('--revision', default='', help='Explicit salt for a reviewed schema Job rerun')
    result.add_argument('--google-client-id', help='Public Google client ID; frontend image must be built with this same value')
    result.add_argument('--reviewed-proof-conversion', action='store_true',
                        help='Operator attests legacy proof identities/backups/maintenance window were reviewed; never deletes rows')
    return result


def main():
    args = parser().parse_args()
    if len(args.namespace) > 63 or not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]*[a-z0-9])?', args.namespace):
        raise SystemExit('namespace must be a DNS label of at most 63 characters')
    manifest, summary = render(args)
    target = HERE / '.generated'
    target.mkdir(exist_ok=True)
    (target / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    (target / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))
    print('Prepared locally only. No cluster, database, image build or registry was contacted.')


if __name__ == '__main__':
    main()
