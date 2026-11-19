import assert from 'node:assert/strict'
import test from 'node:test'
import { homePathFor } from './session.js'

for (const role of ['ADMIN', 'ROLE_ADMIN']) {
  test(`${role} lands at the shared operations entry`, () => {
    assert.equal(homePathFor({ role }), '/admin')
  })
}

for (const role of ['DISPATCHER', 'ROLE_DISPATCHER']) {
  test(`${role} lands in dispatch`, () => {
    assert.equal(homePathFor({ role }), '/dispatch')
  })
}

for (const role of ['CUSTOMER', 'ROLE_CUSTOMER']) {
  test(`${role} keeps the customer home`, () => {
    assert.equal(homePathFor({ role }), '/app')
  })
}

for (const role of ['COURIER', 'ROLE_COURIER']) {
  test(`${role} lands at the courier deliveries page`, () => {
    assert.equal(homePathFor({ role }), '/courier/deliveries')
  })
}

for (const role of ['MERCHANT', 'ROLE_MERCHANT']) {
  test(`${role} lands at the merchant portal`, () => {
    assert.equal(homePathFor({ role }), '/merchant')
  })
}

for (const role of ['UNKNOWN']) {
  test(`${role} keeps the profile fallback`, () => {
    assert.equal(homePathFor({ role }), '/profile')
  })
}

test('an absent user keeps the profile fallback', () => {
  assert.equal(homePathFor(null), '/profile')
})
