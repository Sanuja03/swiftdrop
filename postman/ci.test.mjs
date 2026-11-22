import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { Script, runInNewContext } from 'node:vm'
import { buildCICollection, selectedNames, sourceCollectionPath } from './ci.mjs'

test('CI smoke reuses existing methods, bodies, headers and response assertions', () => {
  const source = JSON.parse(readFileSync(sourceCollectionPath, 'utf8'))
  const flatten = items => items.flatMap(item => item.item ? flatten(item.item) : [item])
  const originals = new Map(flatten(source.item).map(item => [item.name, item]))
  const ci = buildCICollection(source)
  assert.equal(ci.item.length, 20)
  assert.equal(new Set(selectedNames).size, 20)
  for (const item of ci.item) {
    const original = originals.get(item.name)
    assert.equal(item.request.url, original.request.url.replace('{{baseUrl}}', 'http://127.0.0.1:8080'))
    assert.deepEqual(item.request.body, original.request.body)
    assert.deepEqual(item.request.header, original.request.header)
    assert.equal(item.request.method, original.request.method)
    assert.equal(item.request.auth.type, 'noauth')
    assert.deepEqual(item.event.filter(e => e.listen === 'test'), original.event.filter(e => e.listen === 'test'))
  }
})

test('CI scripts compile without optional Postman skipping or hidden network calls', () => {
  const ci = buildCICollection()
  for (const item of [ci, ...ci.item]) for (const event of item.event ?? []) {
    const code = event.script.exec.join('\n')
    new Script(code)
    assert.doesNotMatch(code, /pm\.execution\.(skipRequest|runRequest)|pm\.sendRequest/)
  }
})

test('isolated-CI opt-in is required and all selected prerequisites execute without credentials', () => {
  const ci = buildCICollection()
  const global = ci.event.find(e => e.listen === 'prerequest').script.exec.join('\n')
  function prepare(enabled, item) {
    const values = { ciIsolatedDatabase: enabled }
    const pm = { variables: { get: key => values[key], set: (key, value) => { values[key] = value },
      replaceIn: () => '12345678-1234-4234-8234-123456789abc' },
      request: { body: item.request.body ? { raw: item.request.body.raw, update() {} } : undefined } }
    runInNewContext(global, { pm })
    for (const event of item.event.filter(e => e.listen === 'prerequest')) runInNewContext(event.script.exec.join('\n'), { pm, console })
    assert.ok([200, 204, 400, 401].includes(values.__expectedStatus))
    return values
  }
  assert.throws(() => prepare('false', ci.item[0]), /isolated CI/)
  for (const item of ci.item) prepare('true', item)
})

test('source drift, authenticated requests and unexpected optional workflows fail export', () => {
  const original = JSON.parse(readFileSync(sourceCollectionPath, 'utf8'))
  const flatten = items => items.flatMap(item => item.item ? flatten(item.item) : [item])
  const source = () => ({ ...original, item: structuredClone(flatten(original.item)) })
  const missing = source(); missing.item = missing.item.filter(i => i.name !== selectedNames[0])
  assert.throws(() => buildCICollection(missing), /no longer exists/)
  const authenticated = source(); authenticated.item.find(i => i.name === selectedNames[0]).request.auth = { type: 'bearer' }
  assert.throws(() => buildCICollection(authenticated), /stored credentials/)
  const skipped = source(); skipped.item.find(i => i.name === selectedNames[0]).event[0].script.exec.push('pm.execution.skipRequest();')
  assert.throws(() => buildCICollection(skipped), /optional workflow/)
})
