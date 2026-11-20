const os = require('node:os');
const path = require('node:path');

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name} (e.g. ${name}=<placeholder>)`);
  }
  return value;
}

const BASE = process.env.WEB_BASE || 'http://localhost:5173';
const API = process.env.API_BASE || 'http://127.0.0.1:8081';
const USER_A = {
  email: requireEnv('TEST_USER_A_EMAIL'),
  password: requireEnv('TEST_USER_A_PASSWORD'),
};
const USER_B = {
  email: requireEnv('TEST_USER_B_EMAIL'),
  password: requireEnv('TEST_USER_B_PASSWORD'),
};
const CONV_ID = requireEnv('TEST_CONV_ID');

const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');

async function login(email, password) {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const json = await res.json();
  if (json.code !== 200) throw new Error('login failed: ' + JSON.stringify(json));
  return json.data;
}

(async () => {
  const a = await login(USER_A.email, USER_A.password);
  const b = await login(USER_B.email, USER_B.password);

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

  await A.page.screenshot({ path: path.join(os.tmpdir(), 'chat-A.png') });
  await B.page.screenshot({ path: path.join(os.tmpdir(), 'chat-B.png') });
  await browser.close();
})().catch((e) => {
  console.error('SCRIPT_FAIL', e);
  process.exit(1);
});
