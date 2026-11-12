'use strict'
// Offline reference vectors. Only Node's built-in crypto/zlib/assert modules are used.
// Wire layout: api-enhanced util/crypto.js @ 4a7d557326d16d2f1e5172ee69c9471eb3a50173.
// Run: node <this-file>; no server, credentials, npm install or network access needed.
const crypto = require('node:crypto')
const zlib = require('node:zlib')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const b = (text) => Buffer.from(text)
const hex = (text) => Buffer.from(text, 'hex')
const eapiKey = b('e82ckenh8dichen8')
const staticKey = hex('ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84')
const signKey = 'mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g=='
const publicKey = `-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----`
const sequence = (start, size) => Buffer.from(Array.from({ length: size }, (_, i) => (start + i) & 255))
function aes(data, key, mode = 'ecb', iv = Buffer.alloc(0)) {
  const c = crypto.createCipheriv(`aes-${key.length * 8}-${mode}`, key, iv)
  return Buffer.concat([c.update(data), c.final()])
}
function hmac(data, key) { return crypto.createHmac('sha256', key).update(data).digest() }
function privateKey(raw) {
  return crypto.createPrivateKey({ key: Buffer.concat([hex('302e020100300506032b656e04220420'), raw]), format: 'der', type: 'pkcs8' })
}
function xPublic(raw) { return Buffer.from(crypto.createPublicKey(privateKey(raw)).export({ format: 'der', type: 'spki' })).subarray(-32) }
function peerKey(raw) { return crypto.createPublicKey({ key: Buffer.concat([hex('302a300506032b656e032100'), raw]), format: 'der', type: 'spki' }) }
function plaintext(uri, data, options = {}) {
  const fields = {}
  const contentType = options.contentType || 'application/x-www-form-urlencoded;charset=utf-8'
  const mediaType = contentType.split(';', 1)[0].toLowerCase()
  if (mediaType !== 'application/x-www-form-urlencoded') fields.contentType = contentType
  const method = (options.method || 'POST').toUpperCase()
  if (method !== 'POST') fields.method = method
  const url = new URL(uri, 'https://interface.music.163.com')
  if (url.search) fields.queryString = url.search.slice(1)
  if (data !== undefined && data !== null) {
    const bodyData = { ...data }; delete bodyData.e_r
    const body = b(new URLSearchParams(bodyData).toString())
    if (body.length) {
      if (mediaType === 'application/x-www-form-urlencoded') fields.content = body.toString()
      else fields.body = body.toString('base64')
    }
  }
  fields.queryString = fields.queryString ? fields.queryString + '&e_r=true' : 'e_r=true'
  return JSON.stringify(fields)
}
function weapi(json, secret) {
  const iv = b('0102030405060708')
  const first = aes(b(json), b('0CoJUm6Qyw8W8jud'), 'cbc', iv).toString('base64')
  const block = Buffer.alloc(128); b(secret).reverse().copy(block, 112)
  return {
    params: aes(b(first), b(secret), 'cbc', iv).toString('base64'),
    encSecKey: crypto.publicEncrypt({ key: publicKey, padding: crypto.constants.RSA_NO_PADDING }, block).toString('hex'),
  }
}
function eapi(path, json) {
  const digest = crypto.createHash('md5').update(`nobody${path}use${json}md5forencrypt`).digest('hex')
  return { params: aes(b(`${path}-36cd479b6b5-${json}-36cd479b6b5-${digest}`), eapiKey).toString('hex').toUpperCase() }
}
function xeapi(text, state, options = {}) {
  let offset = 0
  const random = (size) => { const bytes = sequence(offset, size); offset += size; return bytes }
  const key = options.sessionKey ? b(options.sessionKey) : random(16)
  const inner = aes(b(text), staticKey)
  const mask = random(16)
  const xored = Buffer.from(inner.map((value, i) => value ^ mask[i & 15]))
  const rotation = (mask[0] & 15) % xored.length
  const c = aes(Buffer.concat([mask, xored.subarray(rotation), xored.subarray(0, rotation)]), key)
  const ephemeralPrivate = random(32)
  const ephemeralPublic = xPublic(ephemeralPrivate)
  const shared = crypto.diffieHellman({ privateKey: privateKey(ephemeralPrivate), publicKey: peerKey(Buffer.from(state.publicKey, 'base64')) })
  const aesKey = hmac(Buffer.concat([ephemeralPublic, Buffer.from([1])]), hmac(shared, Buffer.alloc(32))).subarray(0, 16)
  const nonce = random(12)
  const sPlain = b(`${key.toString('base64')}|${options.os || 'android'}|${state.sk || ''}`)
  const cipher = crypto.createCipheriv('aes-128-gcm', aesKey, nonce)
  const sealed = Buffer.concat([cipher.update(sPlain), cipher.final(), cipher.getAuthTag()])
  return {
    C: c.toString('base64url'),
    S: Buffer.concat([ephemeralPublic, nonce, sealed]).toString('base64'),
    R: aes(b(`${state.version}|${options.sessionKey ? (options.sessionId || '') : ''}`), staticKey).toString('base64'),
    randomBytesConsumed: offset,
    ephemeralPublic: ephemeralPublic.toString('hex'), sharedSecret: shared.toString('hex'), aesKey: aesKey.toString('hex'),
  }
}
const json = JSON.stringify({ id: 347230, text: '网易云 🎵', empty: '', nested: { a: true } })
const path = '/api/song/enhance/player/url/v1'
const bobPrivate = hex('5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb')
const state = { publicKey: xPublic(bobPrivate).toString('base64'), version: '3', sk: 'server-token|test' }
const data = { ids: '[347230]', level: 'lossless', keyword: '网易云 +/~*', e_r: 'true' }
const xeapiText = plaintext('/api/song/enhance/player/url/v1?existing=a%20b#fragment', data)
const alternateText = plaintext('/api/test', { value: 'a+b', e_r: 'false' }, { method: 'patch', contentType: 'application/json; charset=utf-8' })
const response = b('{"code":200,"message":"ok"}')
const keyJson = JSON.stringify(state)
const vectors = {
  json, path, weapiSecret: 'abcdefghijklmnop', weapi: weapi(json, 'abcdefghijklmnop'), eapi: eapi(path, json),
  eapiEmpty: eapi('/api/test', '{}'),
  xeapiState: state, xeapiPlaintext: xeapiText, alternatePlaintext: alternateText,
  emptyPlaintext: plaintext('/api/test', { e_r: 'true' }),
  xeapi: xeapi(xeapiText, state),
  xeapiSession: xeapi(xeapiText, state, { sessionKey: '0123456789abcdef', sessionId: 'session-123', os: 'pc' }),
  xeapiSign: hmac(b('1700000000123nonce-123'), b(signKey)).toString('base64'),
  publicKeyJson: keyJson, encryptedPublicKey: aes(b(keyJson), staticKey).toString('base64'),
  response: response.toString(), encryptedResponse: aes(response, eapiKey).toString('hex'),
  gzipResponse: zlib.gzipSync(response, { mtime: 0 }).toString('hex'),
  encryptedGzipResponse: aes(zlib.gzipSync(response, { mtime: 0 }), eapiKey).toString('hex'),
}
// Optional: execute the PINNED upstream file's actual xeapi/HMAC/response functions inside a VM.
// CryptoJS/forge are deliberately unavailable; these functions are native Node crypto already.
// This executes no service code and still requires no dependencies.
if (process.argv[2]) {
  let offset = 0
  const sandboxCrypto = { ...crypto,
    randomBytes: (size) => { const out = sequence(offset, size); offset += size; return out },
    generateKeyPairSync: (kind) => { assert.equal(kind, 'x25519'); const secret = sequence(offset, 32); offset += 32; const priv = privateKey(secret); return { privateKey: priv, publicKey: crypto.createPublicKey(priv) } },
  }
  const sandbox = { require: (name) => {
    if (name === 'crypto') return sandboxCrypto
    if (name === 'zlib') return zlib
    if (['crypto-js', 'node-forge', './zstd'].includes(name)) return {}
    throw new Error(`Unexpected require ${name}`)
  }, Buffer, TextDecoder, URL, URLSearchParams, console, module: { exports: {} } }
  vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), sandbox, { filename: 'pinned-crypto.js' })
  const original = sandbox.module.exports
  const actual = original.xeapi('/api/song/enhance/player/url/v1?existing=a%20b#fragment', data, { publicKeyState: state })
  for (const field of ['C', 'S', 'R']) assert.equal(actual[field], vectors.xeapi[field], `upstream ${field}`)
  assert.equal(offset, 76)
  offset = 0
  const session = original.xeapi('/api/song/enhance/player/url/v1?existing=a%20b#fragment', data, { publicKeyState: state, sessionKey: '0123456789abcdef', sessionId: 'session-123', os: 'pc' })
  for (const field of ['C', 'S', 'R']) assert.equal(session[field], vectors.xeapiSession[field], `upstream session ${field}`)
  assert.equal(original.xeapiSign(1700000000123, 'nonce-123'), vectors.xeapiSign)
  assert.equal(JSON.stringify(original.xeapiDecryptPublicKey(vectors.encryptedPublicKey)), keyJson)
  assert.equal(JSON.stringify(original.xeapiResDecrypt(hex(vectors.encryptedGzipResponse))), response.toString())
  console.error('Pinned upstream xeapi/C/S/R, session, HMAC, registration and gzip-response assertions passed.')
}
console.log(JSON.stringify(vectors, null, 2))
