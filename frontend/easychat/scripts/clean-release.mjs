import { rmSync } from 'node:fs'
import { resolve } from 'node:path'

const target = resolve('release')

try {
  rmSync(target, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 })
  console.log('cleaned:', target)
} catch (err) {
  const code = err && typeof err === 'object' && 'code' in err ? String(err.code) : ''
  if (code === 'EBUSY' || code === 'EPERM') {
    console.error('\n无法删除 release 目录：文件被占用。')
    console.error('请先关闭正在运行的 EasyChat（release/win-unpacked/EasyChat.exe）')
    console.error('或 electron:dev 窗口，再重新执行打包。\n')
    process.exit(1)
  }
  throw err
}
