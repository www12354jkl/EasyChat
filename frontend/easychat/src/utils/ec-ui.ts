/** 品牌色，用于内联 style（头像占位等） */
export const EC_BRAND = '#07c160'

export function ecAvatarStyle(avatar?: string): Record<string, string> {
  return { backgroundColor: avatar?.startsWith('#') ? avatar : EC_BRAND }
}
