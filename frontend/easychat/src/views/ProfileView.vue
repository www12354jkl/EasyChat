<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { getProfile, updateProfile, uploadAvatar } from '@/api/user'
import RegionPicker, { type RegionValue } from '@/components/RegionPicker.vue'
import { authInputClass, authPrimaryBtnClass } from '@/components/auth/auth-ui'
import { ecAvatarStyle } from '@/utils/ec-ui'
import AvatarCropperModal from '@/components/AvatarCropperModal.vue'

const auth = useAuthStore()
const toast = useToastStore()

const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const avatar = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const cropperFile = ref<File | null>(null)

function triggerFileSelect() {
  fileInput.value?.click()
}

// 初始值：进入页面后从 GET /api/user/profile 拉取真实数据
const form = reactive({
  nickname: auth.user?.nickname ?? '',
  gender: 0,
  region: '',
  regionCode: '',
  signature: '',
})

// 桥接：RegionPicker 输出 { region, regionCode } 对象，form 拆开存
const regionValue = computed<RegionValue | null>({
  get: () => (form.region || form.regionCode ? { region: form.region, regionCode: form.regionCode } : null),
  set: (val) => {
    form.region = val?.region ?? ''
    form.regionCode = val?.regionCode ?? ''
  },
})

onMounted(async () => {
  try {
    const p = await getProfile()
    avatar.value = p.avatar ?? ''
    form.nickname = p.nickname
    form.gender = p.gender ?? 0
    form.region = p.region ?? ''
    form.regionCode = p.regionCode ?? ''
    form.signature = p.signature ?? ''
    // 同步 auth store（内存 + localStorage），导航栏头像/昵称保持一致
    auth.updateUser({ nickname: p.nickname, avatar: p.avatar ?? '' })
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '资料加载失败')
  }
})

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
    toast.error('只支持 jpg/png/gif/webp 格式')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    toast.error('文件大小不能超过2MB')
    return
  }

  cropperFile.value = file
}

async function handleCropped(file: File) {
  cropperFile.value = null
  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    avatar.value = url
    auth.updateUser({ avatar: url })
    toast.success('头像已更新')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '头像上传失败')
  } finally {
    uploading.value = false
  }
}

const genderOptions = [
  { label: '保密', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
]

const genderLabel = (v: number) => genderOptions.find((o) => o.value === v)?.label ?? '保密'

function startEdit() {
  form.nickname = auth.user?.nickname ?? ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function save() {
  if (!form.nickname.trim()) {
    toast.error('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname.trim(),
      gender: form.gender,
      region: form.region,
      regionCode: form.regionCode,
      signature: form.signature,
    })
    if (auth.user) {
      auth.user.nickname = form.nickname.trim()
    }
    editing.value = false
    toast.success('资料已保存')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function changeAvatar() {
  toast.info('头像上传功能开发中')
}
</script>

<template>
  <div class="ec-page">
    <header class="ec-page-header">
      <h1 class="ec-page-title">个人信息</h1>
    </header>

    <div class="flex-1 overflow-y-auto p-6">
      <div class="mx-auto max-w-[640px] ec-card !p-0 overflow-hidden">
        <!-- 头像 -->
        <div class="flex items-center gap-4 border-b border-[var(--ec-border)] px-5 py-4">
          <div
            class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-full text-2xl font-medium text-white"
            :style="ecAvatarStyle()"
          >
            <img
              v-if="avatar"
              :src="avatar"
              class="h-full w-full object-cover"
              alt="头像"
            />
            <span v-else>{{ auth.user?.nickname?.charAt(0) ?? '?' }}</span>
          </div>
          <div class="flex-1">
            <p class="text-[15px] font-medium text-[var(--ec-text)]">
              {{ auth.user?.nickname ?? '未设置昵称' }}
            </p>
            <p class="mt-0.5 text-xs text-[var(--ec-text-faint)]">{{ auth.user?.email }}</p>
          </div>
          <button
            v-if="!editing"
            type="button"
            :disabled="uploading"
            class="cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-border)] px-3 py-1.5 text-sm text-[var(--ec-primary)] transition-colors duration-150 hover:bg-[var(--ec-bg-subtle)] disabled:opacity-60"
            @click="triggerFileSelect"
          >
            {{ uploading ? '上传中…' : '更换头像' }}
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            class="hidden"
            @change="handleAvatarChange"
          />
        </div>

        <!-- 查看模式 -->
        <div v-if="!editing" class="divide-y divide-[var(--ec-border)]">
          <div class="flex items-center px-5 py-3.5">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">昵称</span>
            <span class="flex-1 text-sm text-[var(--ec-text)]">{{ form.nickname }}</span>
          </div>
          <div class="flex items-center px-5 py-3.5">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">邮箱</span>
            <span class="flex-1 text-sm text-[var(--ec-text)]">{{ auth.user?.email }}</span>
          </div>
          <div class="flex items-center px-5 py-3.5">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">性别</span>
            <span class="flex-1 text-sm text-[var(--ec-text)]">{{ genderLabel(form.gender) }}</span>
          </div>
          <div class="flex items-center px-5 py-3.5">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">地区</span>
            <span class="flex-1 text-sm text-[var(--ec-text)]">{{ form.region || '未设置' }}</span>
          </div>
          <div class="flex items-center px-5 py-3.5">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">个性签名</span>
            <span class="flex-1 text-sm text-[var(--ec-text)]">{{ form.signature || '未设置' }}</span>
          </div>

          <div class="px-5 py-4">
            <button
              type="button"
              :class="authPrimaryBtnClass"
              @click="startEdit"
            >
              编辑资料
            </button>
          </div>
        </div>

        <!-- 编辑模式 -->
        <form v-else class="space-y-4 px-5 py-4" @submit.prevent="save">
          <div class="flex items-center">
            <label class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]" for="nickname">昵称</label>
            <input
              id="nickname"
              v-model="form.nickname"
              type="text"
              maxlength="20"
              :class="authInputClass + ' flex-1'"
            />
          </div>

          <div class="flex items-center">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">性别</span>
            <div class="flex gap-4">
              <label
                v-for="opt in genderOptions"
                :key="opt.value"
                class="flex cursor-pointer items-center gap-1.5 text-sm text-[var(--ec-text)]"
              >
                <input
                  v-model="form.gender"
                  type="radio"
                  name="gender"
                  :value="opt.value"
                  class="h-4 w-4 accent-[var(--ec-primary)]"
                />
                {{ opt.label }}
              </label>
            </div>
          </div>

          <div class="flex items-center">
            <span class="w-24 shrink-0 text-sm text-[var(--ec-text-muted)]">地区</span>
            <RegionPicker v-model="regionValue" />
          </div>

          <div class="flex items-start">
            <label class="w-24 shrink-0 pt-2 text-sm text-[var(--ec-text-muted)]" for="signature">个性签名</label>
            <div class="flex-1">
              <textarea
                id="signature"
                v-model="form.signature"
                rows="2"
                maxlength="30"
                placeholder="介绍一下自己吧"
                class="ec-input resize-none"
              />
              <p class="mt-1 text-right text-xs text-[var(--ec-text-faint)]">{{ form.signature.length }}/30</p>
            </div>
          </div>

          <div class="flex gap-3 pt-1">
            <button
              type="button"
              class="flex-1 cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-border)] py-2 text-sm text-[var(--ec-text-muted)] transition-colors duration-150 hover:bg-[var(--ec-bg-subtle)]"
              @click="cancelEdit"
            >
              取消
            </button>
            <button
              type="submit"
              :disabled="saving"
              :class="authPrimaryBtnClass + ' flex-1'"
            >
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </form>
      </div>
    </div>
    <AvatarCropperModal
      v-if="cropperFile"
      :file="cropperFile"
      @close="cropperFile = null"
      @cropped="handleCropped"
    />
  </div>
</template>

<style scoped></style>
