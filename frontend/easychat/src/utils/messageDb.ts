import type { MessageRecord } from '@/api/chat'

const STORE = 'messages'

function dbName(userId: string) {
  return `easychat-msg-${userId}`
}

function openDb(userId: string): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(dbName(userId), 1)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'id' })
        store.createIndex('conversationId', 'conversationId', { unique: false })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

function reqAs<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

/** 按时间升序返回该会话本地缓存 */
export async function getLocalMessages(userId: string, conversationId: string): Promise<MessageRecord[]> {
  const db = await openDb(userId)
  const rows = await reqAs<MessageRecord[]>(
    db.transaction(STORE, 'readonly').objectStore(STORE).index('conversationId').getAll(conversationId),
  )
  return rows.sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''))
}

export async function putLocalMessages(userId: string, records: MessageRecord[]): Promise<void> {
  const list = records.filter((m) => m.id && !m.id.startsWith('local-'))
  if (!list.length) return
  const db = await openDb(userId)
  const tx = db.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  for (const m of list) store.put(m)
  await txDone(tx)
}

export async function replaceLocalMessages(
  userId: string,
  conversationId: string,
  records: MessageRecord[],
): Promise<void> {
  const db = await openDb(userId)
  const tx = db.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  const keys = await reqAs<IDBValidKey[]>(store.index('conversationId').getAllKeys(conversationId))
  for (const key of keys) store.delete(key)
  for (const m of records.filter((r) => r.id && !r.id.startsWith('local-'))) store.put(m)
  await txDone(tx)
}

export async function clearLocalMessages(userId: string, conversationId: string): Promise<void> {
  await replaceLocalMessages(userId, conversationId, [])
}
