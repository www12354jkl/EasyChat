/**
 * 浏览器测试：IndexedDB 本地缓存 + 清空后当前聊天窗清空
 */
const { chromium } = require('playwright')

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const USER_A = { email: 'www12354jkl@outlook.com', password: 'www12354jkl' }
const USER_B = { email: '3376837436@qq.com', password: 'www12354jkl' }

async function login(email, password) {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const json = await res.json()
  if (json.code !== 200) throw new Error('login failed: ' + JSON.stringify(json))
  return json.data
}

function convIdFor(aId, bId) {
  return aId < bId ? `${aId}_${bId}` : `${bId}_${aId}`
}

async function makePage(browser, session) {
  const ctx = await browser.newContext()
  await ctx.addInitScript(({ token, refresh, user }) => {
    localStorage.setItem('easychat_access_token', token)
    localStorage.setItem('easychat_refresh_token', refresh)
    localStorage.setItem('easychat_user', JSON.stringify(user))
  }, { token: session.accessToken, refresh: session.refreshToken, user: session.userInfo })
  return { ctx, page: await ctx.newPage() }
}

async function idbCount(page, userId, conversationId) {
  return page.evaluate(async ({ userId, conversationId }) => {
    const dbName = `easychat-msg-${userId}`
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(dbName)
      req.onerror = () => reject(req.error)
      req.onsuccess = () => {
        const db = req.result
        if (!db.objectStoreNames.contains('messages')) {
          resolve(0)
          db.close()
          return
        }
        const tx = db.transaction('messages', 'readonly')
        const store = tx.objectStore('messages').index('conversationId')
        const getAll = store.getAll(conversationId)
        getAll.onsuccess = () => {
          resolve(getAll.result.length)
          db.close()
        }
        getAll.onerror = () => reject(getAll.error)
      }
    })
  }, { userId, conversationId })
}

;(async () => {
  console.log('=== IndexedDB + UI clear test ===')
  const a = await login(USER_A.email, USER_A.password)
  const b = await login(USER_B.email, USER_B.password)
  const convId = convIdFor(a.userInfo.userId, b.userInfo.userId)

  const marker = `IDB_TEST_${Date.now()}`
  await fetch(`${API}/api/chat/message`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${a.accessToken}`,
    },
    body: JSON.stringify({ conversationId: convId, content: marker, contentType: 1 }),
  })

  const browser = await chromium.launch({ headless: true })
  const A = await makePage(browser, a)
  const B = await makePage(browser, b)

  await A.page.goto(`${BASE}/chat?conversationId=${encodeURIComponent(convId)}`, { waitUntil: 'networkidle' })
  await A.page.waitForSelector('textarea[placeholder="输入消息..."]', { timeout: 20000 })
  await A.page.waitForTimeout(2000)

  const countBefore = await idbCount(A.page, a.userInfo.userId, convId)
  if (countBefore < 1) throw new Error(`IndexedDB should have messages, got ${countBefore}`)
  await A.page.getByText(marker).first().waitFor({ state: 'visible', timeout: 10000 })
  console.log('A UI shows marker, IndexedDB count=', countBefore)

  // 打开详情侧栏并清空聊天记录
  await A.page.locator('button[title="群设置"]').click()
  await A.page.getByRole('button', { name: '删除聊天记录' }).click()
  await A.page.getByRole('button', { name: '删除', exact: true }).click()
  await A.page.waitForTimeout(1500)

  const countAfter = await idbCount(A.page, a.userInfo.userId, convId)
  if (countAfter !== 0) throw new Error(`IndexedDB should be empty after clear, got ${countAfter}`)
  const markerVisible = await A.page.getByText(marker).count()
  if (markerVisible > 0) throw new Error('A chat window should not show marker after clear')

  await B.page.goto(`${BASE}/chat?conversationId=${encodeURIComponent(convId)}`, { waitUntil: 'networkidle' })
  await B.page.waitForSelector('textarea[placeholder="输入消息..."]', { timeout: 20000 })
  await B.page.waitForTimeout(2000)
  await B.page.getByText(marker).first().waitFor({ state: 'visible', timeout: 10000 })
  console.log('B still sees marker after A cleared')

  await browser.close()
  console.log('\n✅ BROWSER TESTS PASSED')
})().catch((e) => {
  console.error('\n❌ BROWSER TEST FAILED:', e.message || e)
  process.exit(1)
})
