const { chromium } = require('D:/Node.js/node_global/node_modules/@playwright/mcp/node_modules/playwright');

const BASE = 'http://localhost:5173';
const CONV_ID = '36b3ffb5dfbcabb6a65a5e905d4a819d_866d73991b5c87abdbf2e965facc5fe3';

async function login(email, password) {
  const res = await fetch('http://127.0.0.1:8081/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const json = await res.json();
  if (json.code !== 200) throw new Error('login failed: ' + JSON.stringify(json));
  return json.data;
}

(async () => {
  const a = await login('www12354jkl@outlook.com', 'www12354jkl');
  const b = await login('3376837436@qq.com', 'www12354jkl');

  const browser = await chromium.launch({ headless: true });

  async function makePage(session) {
    const ctx = await browser.newContext();
    await ctx.addInitScript(({ token, refresh, user }) => {
      localStorage.setItem('easychat_access_token', token);
      localStorage.setItem('easychat_refresh_token', refresh);
      localStorage.setItem('easychat_user', JSON.stringify(user));
    }, { token: session.accessToken, refresh: session.refreshToken, user: session.userInfo });
    const page = await ctx.newPage();
    return { ctx, page };
  }

  const A = await makePage(a);
  const B = await makePage(b);

  await A.page.goto(`${BASE}/chat?conversationId=${CONV_ID}`, { waitUntil: 'domcontentloaded' });
  await B.page.goto(`${BASE}/chat?conversationId=${CONV_ID}`, { waitUntil: 'domcontentloaded' });

  await A.page.waitForSelector('input[placeholder="输入消息..."]', { timeout: 15000 });
  await B.page.waitForSelector('input[placeholder="输入消息..."]', { timeout: 15000 });
  await A.page.waitForTimeout(1500);
  await B.page.waitForTimeout(1500);

  const text = 'PW_TEST_' + Date.now();
  await A.page.fill('input[placeholder="输入消息..."]', text);
  await A.page.getByRole('button', { name: '发送' }).click();

  try {
    await B.page.getByText(text).first().waitFor({ state: 'visible', timeout: 10000 });
    console.log('PUSH_OK message=' + text);
  } catch (e) {
    console.log('PUSH_FAIL message=' + text);
    console.log('B body:', (await B.page.textContent('body')).slice(0, 2000));
  }

  await A.page.screenshot({ path: 'C:/tmp/chat-A.png' });
  await B.page.screenshot({ path: 'C:/tmp/chat-B.png' });
  await browser.close();
})().catch((e) => {
  console.error('SCRIPT_FAIL', e);
  process.exit(1);
});
