/**
 * 测试：单方面删除聊天记录 + 对方记录保留
 * 用法：node test-cleared-at.js
 */
const BASE = process.env.API_BASE || 'http://localhost:8080'
const USER_A = { email: 'www12354jkl@outlook.com', password: 'www12354jkl' }
const USER_B = { email: '3376837436@qq.com', password: 'www12354jkl' }

async function login({ email, password }) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const json = await res.json()
  if (json.code !== 200) throw new Error(`login ${email} failed: ${JSON.stringify(json)}`)
  return json.data
}

async function api(token, path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 200) throw new Error(`${options.method || 'GET'} ${path} => ${JSON.stringify(json)}`)
  return json.data
}

function convIdFor(aId, bId) {
  return aId < bId ? `${aId}_${bId}` : `${bId}_${aId}`
}

async function getMessages(token, conversationId) {
  const page = await api(token, `/api/chat/messages?conversationId=${encodeURIComponent(conversationId)}&page=1&size=50`)
  return page.records || []
}

function assert(cond, msg) {
  if (!cond) throw new Error(`ASSERT FAIL: ${msg}`)
}

;(async () => {
  console.log('=== cleared_at API test ===')
  console.log('API base:', BASE)

  const a = await login(USER_A)
  const b = await login(USER_B)
  const aId = a.userInfo.userId
  const bId = b.userInfo.userId
  const convId = convIdFor(aId, bId)
  console.log('conversation:', convId)

  const marker = `CLEAR_TEST_${Date.now()}`
  await api(a.accessToken, '/api/chat/message', {
    method: 'POST',
    body: { conversationId: convId, content: marker, contentType: 1 },
  })
  console.log('sent marker:', marker)

  const beforeA = await getMessages(a.accessToken, convId)
  const beforeB = await getMessages(b.accessToken, convId)
  assert(beforeA.some((m) => m.content === marker), 'A should see marker before clear')
  assert(beforeB.some((m) => m.content === marker), 'B should see marker before clear')
  console.log('before clear: A records=', beforeA.length, 'B records=', beforeB.length)

  await api(a.accessToken, `/api/chat/conversation/${encodeURIComponent(convId)}/messages`, {
    method: 'DELETE',
  })
  console.log('A cleared history')

  const afterA = await getMessages(a.accessToken, convId)
  const afterB = await getMessages(b.accessToken, convId)
  assert(!afterA.some((m) => m.content === marker), 'A should NOT see marker after clear')
  assert(afterB.some((m) => m.content === marker), 'B should still see marker after A cleared')
  console.log('after clear: A records=', afterA.length, 'B records=', afterB.length)

  const convsA = await api(a.accessToken, '/api/chat/conversations')
  const convsB = await api(b.accessToken, '/api/chat/conversations')
  const rowA = convsA.find((c) => c.id === convId)
  const rowB = convsB.find((c) => c.id === convId)
  assert(rowA && (!rowA.lastMessage || rowA.lastMessage === ''), `A conversation lastMessage should be empty, got: ${rowA?.lastMessage}`)
  assert(rowB && rowB.lastMessage, 'B conversation lastMessage should remain')
  console.log('conversation list: A lastMessage=', JSON.stringify(rowA?.lastMessage), 'B lastMessage=', JSON.stringify(rowB?.lastMessage))

  // A 清空后再发一条，双方应只看到清空后的新消息（对 A 而言）
  const marker2 = `CLEAR_TEST_AFTER_${Date.now()}`
  await api(a.accessToken, '/api/chat/message', {
    method: 'POST',
    body: { conversationId: convId, content: marker2, contentType: 1 },
  })
  const afterSendA = await getMessages(a.accessToken, convId)
  const afterSendB = await getMessages(b.accessToken, convId)
  assert(afterSendA.some((m) => m.content === marker2), 'A should see new message after clear')
  assert(!afterSendA.some((m) => m.content === marker), 'A should still not see old marker')
  assert(afterSendB.some((m) => m.content === marker2), 'B should see new message')
  assert(afterSendB.some((m) => m.content === marker), 'B should still see old marker')
  console.log('post-clear send OK: A=', afterSendA.length, 'B=', afterSendB.length)

  console.log('\n✅ ALL API TESTS PASSED')
})().catch((e) => {
  console.error('\n❌ TEST FAILED:', e.message || e)
  process.exit(1)
})
