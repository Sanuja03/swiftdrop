"""Offline syntax and SwiftDrop manifest-contract checks; NOT Kubernetes OpenAPI validation."""
import argparse
import base64
import json
import re
from pathlib import Path

from prepare import HERE, SOURCES, SQL_SOURCES, objects, pod_spec, read_json
import migrations


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--secrets', type=Path, help='Optionally check a private local Secret file without printing values')
    args = parser.parse_args()
    for source in SOURCES + ['secret.example.yaml']:
        read_json(HERE / source)
    secret = read_json(HERE / 'secret.example.yaml')
    require(secret['kind'] == 'Secret' and secret['type'] == 'Opaque', 'Invalid Secret template')
    require(all(value == '' for value in secret['stringData'].values()), 'Secret template must contain no credential values')
    manifest = read_json(HERE / '.generated/manifest.json')
    summary = read_json(HERE / '.generated/summary.json')
    if args.secrets:
        private = read_json(args.secrets)
        require(private.get('kind') == 'Secret' and private['metadata']['name'] == 'swiftdrop-secrets', 'Private Secret identity mismatch')
        require(private['metadata'].get('namespace') == summary['namespace'], 'Private Secret namespace mismatch')
        values = private.get('stringData', {})
        require(all(isinstance(value, str) for value in values.values()), 'Private Secret stringData values must be strings')
        for key in ('POSTGRES_USER', 'POSTGRES_PASSWORD', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD', 'JWT_SECRET'):
            require(isinstance(values.get(key), str) and values[key].strip(), f'Fill private Secret field {key}')
        require(values['RABBITMQ_USERNAME'] != 'guest', 'Use a named RabbitMQ user, not guest')
        try:
            jwt_bytes = base64.b64decode(values['JWT_SECRET'], validate=True)
        except (ValueError, base64.binascii.Error):
            raise ValueError('JWT_SECRET must be Base64; secret values are not displayed') from None
        require(len(jwt_bytes) >= 32, 'JWT_SECRET must decode to at least 32 random bytes')
        for left, right in [('SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD'), ('MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY')]:
            require(bool(values.get(left)) == bool(values.get(right)), f'Provide both optional {left}/{right}, or neither')
    resources = objects(manifest)
    identities = [(obj['kind'], obj['metadata']['name']) for obj in resources]
    require(len(identities) == len(set(identities)), 'Duplicate resource identity')
    lookup = dict(zip(identities, resources))
    configs = {obj['metadata']['name']: obj['data'] for obj in resources if obj['kind'] == 'ConfigMap'}
    require(all(isinstance(value, str) for data in configs.values() for value in data.values()), 'ConfigMap data values must be strings')
    require(all(sum(len(key.encode()) + len(value.encode()) for key,value in data.items()) < 1024*1024 for data in configs.values()), 'ConfigMap exceeds the 1 MiB data limit')
    expected_api = {'Namespace': 'v1', 'ConfigMap': 'v1', 'Service': 'v1', 'StatefulSet': 'apps/v1', 'Deployment': 'apps/v1', 'Job': 'batch/v1'}
    require(not any(obj['kind'] == 'Secret' for obj in resources), 'Rendered public bundle must not contain Secrets')
    container_count = 0
    for obj in resources:
        kind, name = obj['kind'], obj['metadata']['name']
        require(obj['apiVersion'] == expected_api[kind], f'Unexpected API: {kind}/{name}')
        if kind != 'Namespace':
            require(obj['metadata']['namespace'] == summary['namespace'], f'Namespace mismatch: {name}')
        if kind not in ('Deployment', 'StatefulSet', 'Job'):
            continue
        spec = pod_spec(obj)
        require(spec['automountServiceAccountToken'] is False, f'Unexpected API token mount: {name}')
        labels = obj['spec']['template']['metadata']['labels']
        if kind != 'Job':
            require(obj['spec']['replicas'] == 1, f'Default must be single-replica: {name}')
            selector = obj['spec']['selector']['matchLabels']
            require(all(labels.get(k) == v for k, v in selector.items()), f'Pod selector mismatch: {name}')
        volumes = {volume['name']: volume for volume in spec.get('volumes', [])}
        volumes.update({claim['metadata']['name']: {} for claim in obj['spec'].get('volumeClaimTemplates', [])})
        for volume in spec.get('volumes', []):
            if 'configMap' in volume:
                require(volume['configMap']['name'] in configs, f'Missing volume ConfigMap: {name}')
        for container in spec.get('initContainers', []) + spec['containers']:
            container_count += 1
            require(not any(token in container['image'].upper() for token in ('REPLACE', 'CHANGE_ME')), f'Image placeholder in {name}')
            require(container.get('imagePullPolicy') == 'IfNotPresent', f'Unexpected pull policy: {name}')
            for category in ('requests', 'limits'):
                require(set(container['resources'][category]) == {'cpu', 'memory'}, f'Missing resources: {name}/{container["name"]}')
            if container in spec['containers'] and kind != 'Job':
                for key in ('startupProbe', 'readinessProbe', 'livenessProbe'):
                    probe = container[key]
                    require(sum(action in probe for action in ('exec', 'httpGet', 'tcpSocket')) == 1, f'Invalid {key}: {name}')
                    require(probe['timeoutSeconds'] > 0 and probe['failureThreshold'] > 0, f'Invalid probe timing: {name}')
            seen_env = set()
            for source in container.get('envFrom', []):
                require(source['configMapRef']['name'] in configs, f'Missing envFrom ConfigMap in {name}')
                seen_env.update(configs[source['configMapRef']['name']])
            for env in container.get('env', []):
                ref = env.get('valueFrom', {})
                if 'configMapKeyRef' in ref:
                    require(ref['configMapKeyRef']['key'] in configs.get(ref['configMapKeyRef']['name'], {}), f'Missing ConfigMap env key in {name}')
                if 'secretKeyRef' in ref:
                    require(ref['secretKeyRef']['name'] == 'swiftdrop-secrets' and ref['secretKeyRef']['key'] in secret['stringData'], f'Missing Secret template key in {name}')
                for dependency in re.findall(r'\$\(([A-Z_]+)\)', env.get('value', '')):
                    require(dependency in seen_env, f'Environment expansion order invalid: {dependency} in {name}')
                seen_env.add(env['name'])
            for mount in container.get('volumeMounts', []):
                require(mount['name'] in volumes, f'Unknown mount: {name}/{mount["name"]}')
        if kind == 'StatefulSet':
            require(obj['spec']['persistentVolumeClaimRetentionPolicy'] == {'whenDeleted': 'Retain', 'whenScaled': 'Retain'}, f'PVCs are not retained: {name}')
            require(('Service', obj['spec']['serviceName']) in lookup, f'Governing Service missing: {name}')
            for claim in obj['spec']['volumeClaimTemplates']:
                require(claim['spec']['accessModes'] == ['ReadWriteOnce'], f'Invalid storage mode: {name}')
                require(re.fullmatch(r'[1-9][0-9]*Gi', claim['spec']['resources']['requests']['storage']), f'Invalid storage request: {name}')

    for obj in resources:
        if obj['kind'] != 'Service':
            continue
        name = obj['metadata']['name']
        selector = obj['spec']['selector']
        targets = [candidate for candidate in resources if candidate['kind'] in ('StatefulSet', 'Deployment')
                   and all(candidate['spec']['template']['metadata']['labels'].get(k) == v for k, v in selector.items())]
        require(len(targets) == 1, f'Service must select one workload: {name}')
        ports = {p['name']: p['containerPort'] for c in pod_spec(targets[0])['containers'] for p in c.get('ports', [])}
        for port in obj['spec']['ports']:
            require(ports.get(port['targetPort']) == port['port'], f'Service targetPort mismatch: {name}')
        require(obj['spec'].get('type', 'ClusterIP') == 'ClusterIP', f'Unexpected public service: {name}')
    for name, port in [('postgres', 5432), ('rabbitmq', 5672), ('backend', 8080), ('frontend', 80)]:
        require(any(p['port'] == port for p in lookup['Service', name]['spec']['ports']), f'Application service/port contract mismatch: {name}')

    config = configs['swiftdrop-config']
    require(config['SPRING_JPA_HIBERNATE_DDL_AUTO'] == 'none' and config['SPRING_SQL_INIT_MODE'] == 'never', 'Automatic Hibernate/SQL initialization must stay disabled')
    require(config['RABBITMQ_HOST'] == 'rabbitmq' and config['RABBITMQ_PORT'] == '5672', 'Broker settings mismatch')
    require(config['MANAGEMENT_ENDPOINT_HEALTH_GROUP_LIVENESS_INCLUDE'] == 'livenessState', 'Liveness must not depend on external services')
    scripts = configs[summary['databaseScriptsConfigMap']]
    for key, path in SQL_SOURCES.items():
        require(scripts[key] == path.read_text(encoding='utf-8').replace('\r\n', '\n'), f'Stale SQL/script bundle: {key}; rerun prepare.py')
    require(scripts['bundle-sha256'] == summary['schemaBundleSha256'], 'Bundle marker mismatch')
    driver = scripts['initialize.sql']
    require(driver.index('\\if :bootstrap_new_database') < driver.index('\\ir /db/001-schema.sql') < driver.index('\\else'), 'Baseline must be gated to a fresh schema')
    require(driver.index('preflight.sql') < driver.index('migrate.sql') < driver.index('assert-catalog.sql') < driver.index('INSERT INTO public.swiftdrop_deployment_schema'), 'Migration/catalog/marker order invalid')
    require('pg_advisory_lock' in driver and '\\set ON_ERROR_STOP on' in driver, 'Migration lock/error handling missing')
    expected_runner, checksums = migrations.runner(scripts)
    require(scripts['migrate.sql'] == expected_runner and summary['migrationChecksums'] == checksums, 'Migration runner/checksums are stale')
    contract = migrations.catalog(scripts['001-schema.sql'])
    require(scripts['assert-catalog.sql'] == migrations.contract_sql(contract), 'Catalog gate is stale')
    require(summary['proofSourceMatchesFrozen'] == migrations.proof_source_matches(scripts['legacy-proof-body.sql'], scripts['legacy-proof-source.sql']), 'Proof source comparison mismatch')
    require('BEGIN;' not in scripts['legacy-proof-body.sql'].splitlines() and 'COMMIT;' not in scripts['legacy-proof-body.sql'].splitlines(), 'Nested migration transactions')
    additive = migrations.without_comments(scripts['001-additive-schema.sql'])
    require(not re.search(r'\b(?:INSERT\s+INTO|UPDATE\s+\w|DELETE\s+FROM|TRUNCATE|DROP\s+(?:TABLE|COLUMN|SCHEMA|DATABASE))\b', additive, re.I), 'Additive migration contains unsafe row/destructive operations')
    require("IS DISTINCT FROM 'true'" in scripts['002-proof-user-ids.sql'], 'Legacy identity review gate missing')
    require(config['DB_REVIEWED_PROOF_CONVERSION'] == str(summary['reviewedProofConversion']).lower(), 'Proof review flag mismatch')
    require(config['SPRING_PROFILES_ACTIVE'] == 'default' and config['APP_AUTH_GOOGLE_MOCK_TOKENS_ENABLED'] == 'false', 'Permissive development authentication must stay disabled')
    require(config['SWIFTDROP_WEBHOOKS_DIRECT_DELIVERY_ENABLED'] == 'false', 'Duplicate direct webhook delivery must remain disabled')
    job = lookup['Job', summary['databaseJob']]
    require(job['spec']['backoffLimit'] == 2 and pod_spec(job)['restartPolicy'] == 'Never', 'Job retry policy mismatch')
    backend = pod_spec(lookup['Deployment', 'backend'])
    require(backend['initContainers'][0]['command'] == ['sh', '/db/wait-for-schema.sh'], 'Backend schema startup gate missing')
    application = backend['containers'][0]
    supplied = set(config) | {entry['name'] for entry in application['env']}
    properties = (HERE.parent / 'backend/src/main/resources/application.properties').read_text()
    placeholders = set(re.findall(r'\$\{([A-Z][A-Z0-9_]*)', properties))
    require(placeholders - supplied <= {'SUPABASE_DB_PASSWORD', 'OPENAI_API_KEY', 'GEMINI_API_KEY'}, 'Current application property has an undocumented/unwired environment variable')
    for key in ('MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY', 'MINIO_PROOF_BUCKET'):
        require(key in supplied, f'Proof storage environment missing: {key}')
    require(not any(re.search(r'(?:PASSWORD|SECRET|API_KEY)$', key) for key in config), 'Sensitive settings must be Secret references')
    frontend = pod_spec(lookup['Deployment', 'frontend'])
    require('VITE_GOOGLE_CLIENT_ID' not in {entry['name'] for entry in frontend['containers'][0].get('env', [])}, 'Vite client ID is build-time, not runtime')
    require(summary['frontendBuild']['args']['VITE_GOOGLE_CLIENT_ID'] == config['GOOGLE_CLIENT_ID'], 'Frontend/backend Google client ID mismatch')
    require(lookup['Deployment', 'frontend']['spec']['template']['metadata']['annotations']['swiftdrop/google-client-id'] == config['GOOGLE_CLIENT_ID'], 'Frontend build declaration mismatch')
    nginx = configs['swiftdrop-frontend-config']['default.conf']
    require('client_max_body_size 6m;' in nginx and 'proxy_pass http://backend:8080;' in nginx, 'Nginx upload/proxy configuration mismatch')
    require(frontend['containers'][0]['volumeMounts'][0]['subPath'] == 'default.conf', 'Frontend proxy override must mount one file only')
    require('ARG VITE_GOOGLE_CLIENT_ID' in (HERE / 'frontend.Dockerfile').read_text(), 'Kubernetes frontend build does not support Google client ID')
    require('proxy_pass http://backend:8080;' in (HERE.parent / 'frontend/nginx.conf').read_text(), 'Frontend proxy contract changed; review Services')
    print(json.dumps({'sourceFilesParsed': len(SOURCES) + 1, 'renderedResources': len(resources),
                      'containersIncludingInit': container_count, 'catalogTables': len({column['table_name'] for column in contract['columns']}),
                      'catalogColumns': len(contract['columns']), 'versionedMigrations': len(migrations.MIGRATIONS),
                      'proofSourceMatchesFrozen': summary['proofSourceMatchesFrozen'],
                      'checks': 'syntax, APIs, references, selectors, ports, env order, probes, resources, retained PVCs, source environment, build configuration and migration safety/gating: PASS',
                      'credentials': 'blank template only; no Secret in rendered bundle',
                      'notValidated': 'Kubernetes OpenAPI/admission, image pulls, SQL execution, PVC provisioning, runtime probes and rollout; no live commands executed'}, indent=2))


if __name__ == '__main__':
    main()
