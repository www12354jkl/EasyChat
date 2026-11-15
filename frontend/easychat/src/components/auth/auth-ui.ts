/** 认证页共享样式（极简绿白） */
export const authInputClass =
  'auth-input w-full rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-3.5 py-2.5 text-sm text-[var(--ec-text)] outline-none transition-[border-color,box-shadow] duration-200 placeholder:text-[var(--ec-text-faint)] focus:border-[var(--ec-primary)] focus:ring-[3px] focus:ring-[var(--ec-primary-ring)]'

export const authLabelClass = 'mb-1.5 block text-[13px] font-medium text-[var(--ec-text-muted)]'

export const authPrimaryBtnClass =
  'auth-primary-btn w-full cursor-pointer rounded-[var(--ec-radius)] bg-[var(--ec-primary)] py-2.5 text-sm font-medium text-white transition-[background-color,transform] duration-200 hover:bg-[var(--ec-primary-hover)] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-55'

export const authGhostBtnClass =
  'cursor-pointer rounded-[var(--ec-radius)] border border-[var(--ec-border)] bg-[var(--ec-bg)] px-3 py-2.5 text-sm font-medium text-[var(--ec-primary)] transition-[border-color,background-color] duration-200 hover:border-[var(--ec-primary)] hover:bg-[var(--ec-primary-soft)] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50'

export const authLinkClass =
  'cursor-pointer text-[13px] font-medium text-[var(--ec-text-muted)] transition-colors duration-200 hover:text-[var(--ec-primary)]'

export const authAlertSuccessClass =
  'rounded-[var(--ec-radius)] border border-[var(--ec-primary-muted)] bg-[var(--ec-primary-soft)] px-3.5 py-2.5 text-sm text-[#047857]'

export const authAlertErrorClass =
  'rounded-[var(--ec-radius)] border border-red-100 bg-red-50 px-3.5 py-2.5 text-sm text-red-600'
