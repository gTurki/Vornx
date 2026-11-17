import type { AuthResponse, ProblemDetail } from './types'
import { tr } from '../i18n'

/**
 * The one place that talks HTTP to the backend.
 *
 * Tokens:
 *  - access token  -> kept in MEMORY only (gone on reload; that's fine, we refresh).
 *  - refresh token -> localStorage, so a reload keeps you logged in.
 *    (Trade-off: a script injected into the page could read it. React escapes everything we render,
 *     and we add no third-party scripts. A stronger option later: an httpOnly cookie set by the API.)
 *
 * On a 401 we refresh ONCE and retry. If 3 requests fail at the same moment, they all wait for the
 * SAME refresh call - sending 3 refreshes with one token would look like token theft to the backend.
 */

export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const REFRESH_KEY = 'vornx.customer.refreshToken'

let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onSessionEnded: (() => void) | null = null

export const session = {
  hasRefreshToken: () => !!safeGet(REFRESH_KEY),
  /** The current access token (memory only), e.g. to log in the WebSocket. */
  accessToken: () => accessToken,
  refreshToken: () => safeGet(REFRESH_KEY),
  save(auth: AuthResponse) {
    accessToken = auth.accessToken
    safeSet(REFRESH_KEY, auth.refreshToken)
  },
  clear() {
    accessToken = null
    safeRemove(REFRESH_KEY)
  },
  /** Called when the session can't be refreshed any more (expired, revoked, account suspended). */
  onEnded(cb: () => void) {
    onSessionEnded = cb
  },
}

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail
  constructor(status: number, problem?: ProblemDetail) {
    super(arabicMessage(status, problem))
    this.status = status
    this.problem = problem
  }
  get code() {
    return this.problem?.code
  }
}

type Options = { method?: string; body?: unknown; auth?: boolean }

export async function api<T>(path: string, { method = 'GET', body, auth = true }: Options = {}): Promise<T> {
  const send = () =>
    fetch(API_URL + path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(auth && accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

  let res: Response
  try {
    res = await send()
  } catch {
    throw new ApiError(0, { status: 0, detail: tr('تعذّر الاتصال بالخادم. تحقق من الإنترنت ثم حاول مجددًا.') })
  }

  if (res.status === 401 && auth) {
    if (await refresh()) {
      res = await send()
    } else {
      onSessionEnded?.()
      throw new ApiError(401, { status: 401, detail: tr('انتهت الجلسة. سجّل الدخول مرة أخرى.') })
    }
  }

  if (!res.ok) {
    throw new ApiError(res.status, await res.json().catch(() => undefined))
  }
  if (res.status === 204) {
    return undefined as T
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/**
 * Exchange the refresh token for a new pair. Only one refresh runs at a time - in this tab AND across tabs.
 *
 * Why across tabs: all tabs share ONE refresh token (localStorage). If 2 tabs send it at the same moment,
 * the server accepts the first and answers 401 to the second. Without the lock, the second tab would then
 * delete the brand-new token the first tab just saved, and every tab would be logged out.
 * navigator.locks (Web Locks API) makes the tabs take turns; each one reads the LATEST token inside the lock.
 */
export function refresh(): Promise<boolean> {
  if (!refreshing) {
    const run = () => doRefresh()
    refreshing = (navigator.locks ? navigator.locks.request(REFRESH_KEY, run) : run()).finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

async function doRefresh(): Promise<boolean> {
  const rt = safeGet(REFRESH_KEY)
  if (!rt) return false
  try {
    const res = await fetch(API_URL + '/api/public/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt }),
    })
    if (!res.ok) {
      // Only forget the token if no other tab replaced it in the meantime.
      if (safeGet(REFRESH_KEY) === rt) session.clear()
      return false
    }
    session.save(await res.json())
    return true
  } catch {
    return false // network error: keep the refresh token, maybe the server is just restarting
  }
}


// ---------- Arabic messages for the error codes the backend sends ----------

const CODES: Record<string, string> = {
  EMAIL_TAKEN: tr('هذا البريد مسجّل مسبقًا. سجّل الدخول أو استعد كلمة المرور.'),
  PHONE_TAKEN: tr('رقم الجوال مسجّل مسبقًا.'),
  PASSWORD_MISMATCH: tr('كلمتا المرور غير متطابقتين.'),
  WRONG_PASSWORD: tr('كلمة المرور الحالية غير صحيحة.'),
  SAME_PASSWORD: tr('كلمة المرور الجديدة يجب أن تختلف عن الحالية.'),
  RESET_LINK_INVALID: tr('رابط إعادة التعيين غير صالح أو منتهي أو مستخدم.'),
  INSUFFICIENT_FUNDS: tr('رصيد محفظتك لا يكفي.'),
  SELF_TRANSFER: tr('لا يمكنك التحويل لنفسك.'),
  TOP_UP_DISABLED: tr('شحن المحفظة غير متاح حاليًا.'),
  TOO_MANY_PENDING: tr('لديك عمليات شحن لم تكتمل. حاول بعد قليل.'),
  PAYMENT_PROVIDER_ERROR: tr('خدمة الدفع لا تستجيب الآن. حاول مرة أخرى.'),
  CARD_NOT_ACTIVE: tr('يمكن الإبلاغ عن فقد البطاقة النشطة فقط.'),
  ALREADY_REGISTERED: tr('لديك تذكرة لهذه الفعالية مسبقًا.'),
  EVENT_FULL: tr('اكتمل عدد المقاعد في هذه الفعالية.'),
  REGISTRATION_CLOSED: tr('أُغلق التسجيل: بدأت الفعالية.'),
  CANNOT_CANCEL: tr('لا يمكن إلغاء هذا الآن.'),
  PLAN_UNAVAILABLE: tr('هذه الباقة لم تعد متاحة.'),
  REQUEST_PENDING: tr('لديك طلب قيد المراجعة لهذا الاشتراك.'),
  CANNOT_RENEW: tr('يمكن تجديد الاشتراك النشط أو المنتهي فقط.'),
  REQUEST_NOT_PENDING: tr('تم البت في هذا الطلب مسبقًا.'),
  ACCOUNT_INACTIVE: tr('حسابك غير نشط. تواصل مع الدعم.'),
  CODE_INVALID: tr('الرمز غير صحيح.'),
  CODE_EXPIRED: tr('انتهت صلاحية الرمز. اطلب رمزًا جديدًا.'),
  CODE_RESEND_TOO_SOON: tr('انتظر قليلًا قبل طلب رمز جديد.'),
  CODE_RESEND_LIMIT: tr('وصلت للحد الأقصى من الرموز. سجّل الدخول من جديد.'),
  LOGIN_CHALLENGE_INVALID: tr('انتهت خطوة التحقق. ابدأ من جديد.'),
  CODE_RESEND_NOT_AVAILABLE: tr('استخدم الرمز من تطبيق المصادقة أو رمز استرداد.'),
  PHONE_NOT_VERIFIED: tr('أكّد رقم جوالك أولًا.'),
  PHONE_ALREADY_VERIFIED: tr('رقم جوالك مؤكَّد مسبقًا.'),
  PHONE_VERIFICATION_OFF: tr('تأكيد الجوال غير متاح حاليًا.'),
  PHONE_INVALID: tr('لا يمكن إرسال رمز إلى رقم جوالك. تواصل معنا.'),
  PHONE_CHANGED: tr('تغيّر رقم جوالك. اطلب رمزًا جديدًا.'),
  WHATSAPP_UNAVAILABLE: tr('تعذّر إرسال رسالة واتساب الآن. حاول بعد قليل.'),
  TOTP_ALREADY_ON: tr('تطبيق المصادقة مفعّل مسبقًا.'),
  TOTP_SETUP_NEEDED: tr('ابدأ الإعداد من جديد.'),
  TOTP_CODE_INVALID: tr('الرمز غير صحيح. تأكد أن وقت جوالك مضبوط تلقائيًا.'),
  PHONE_MISMATCH: tr('رقم الجوال لا يطابق رقم حسابك.'),
  PHONE_VERIFICATION_FAILED: tr('تعذّر التحقق من رقم الجوال. حاول مجددًا أو اطلب الرمز بالبريد.'),
  SMS_LOGIN_DISABLED: tr('الدخول بالرسائل النصية غير متاح الآن. اطلب الرمز بالبريد.'),
}

function arabicMessage(status: number, p?: ProblemDetail): string {
  if (p?.code === 'CODE_INVALID' && p.attemptsLeft) return tr('الرمز غير صحيح. المحاولات المتبقية: {0}', { 0: p.attemptsLeft })
  if (p?.code && CODES[p.code]) return CODES[p.code]
  if (status === 429) return tr('محاولات كثيرة. انتظر بضع دقائق ثم حاول مجددًا.')
  if (p?.errors) return Object.values(p.errors).join(' · ')
  if (status === 401) return p?.detail === 'Invalid email or password' ? tr('البريد أو كلمة المرور غير صحيحة.') : tr('سجّل الدخول للمتابعة.')
  if (status === 403) return p?.detail?.includes('suspended') ? tr('الحساب أو الجهة معلّقة.') : tr('ليست لديك صلاحية لهذا الإجراء.')
  if (status === 404) return p?.detail?.startsWith('Customer') ? tr('لا يوجد عميل بهذا الرقم.') : tr('العنصر غير موجود.')
  if (status >= 500) return tr('حدث خطأ في الخادم. حاول لاحقًا.')
  return p?.detail ?? tr('حدث خطأ غير متوقع.')
}

// localStorage can throw (private mode, blocked storage): never let that break the app.
function safeGet(k: string) {
  try { return localStorage.getItem(k) } catch { return null }
}
function safeSet(k: string, v: string) {
  try { localStorage.setItem(k, v) } catch { /* ignore */ }
}
function safeRemove(k: string) {
  try { localStorage.removeItem(k) } catch { /* ignore */ }
}
