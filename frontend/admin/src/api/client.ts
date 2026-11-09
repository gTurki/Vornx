import type { AuthResponse, ProblemDetail } from './types'

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
const REFRESH_KEY = 'vornx.admin.refreshToken'

let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onSessionEnded: (() => void) | null = null

export const session = {
  hasRefreshToken: () => !!safeGet(REFRESH_KEY),
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
    throw new ApiError(0, { status: 0, detail: 'تعذّر الاتصال بالخادم. تحقق من الإنترنت ثم حاول مجددًا.' })
  }

  if (res.status === 401 && auth) {
    if (await refresh()) {
      res = await send()
    } else {
      onSessionEnded?.()
      throw new ApiError(401, { status: 401, detail: 'انتهت الجلسة. سجّل الدخول مرة أخرى.' })
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

/** Exchange the refresh token for a new pair. Only one refresh runs at a time. */
export function refresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const rt = safeGet(REFRESH_KEY)
      if (!rt) return false
      try {
        const res = await fetch(API_URL + '/api/public/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: rt }),
        })
        if (!res.ok) {
          session.clear()
          return false
        }
        session.save(await res.json())
        return true
      } catch {
        return false // network error: keep the refresh token, maybe the server is just restarting
      }
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

// ---------- Arabic messages for the error codes the backend sends ----------

const CODES: Record<string, string> = {
  CR_TAKEN: 'يوجد شريك مسجّل بهذا السجل التجاري.',
  EMAIL_TAKEN: 'هذا البريد مسجّل مسبقًا.',
  PARTNER_SUSPENDED: 'لا يمكن دعوة موظفين لجهة معلّقة.',
  NOT_ACCOUNT_MANAGER: 'هذا المستخدم لا يمكن أن يكون مدير حساب.',
  CONTRACT_EXISTS: 'لهذه الجهة عقد سارٍ. جدّده بدلًا من إنشاء عقد جديد.',
  CONTRACT_TERMINATED: 'لا يمكن تجديد عقد منتهٍ بالإلغاء.',
  INVALID_PERIOD: 'تاريخ النهاية يجب أن يكون بعد تاريخ البداية.',
  ALREADY_PAID: 'هذه الفاتورة مدفوعة مسبقًا.',
  WRONG_PASSWORD: 'كلمة المرور الحالية غير صحيحة.',
  SAME_PASSWORD: 'كلمة المرور الجديدة يجب أن تختلف عن الحالية.',
  PASSWORD_MISMATCH: 'كلمتا المرور غير متطابقتين.',
  RESET_LINK_INVALID: 'رابط إعادة التعيين غير صالح أو منتهي أو مستخدم.',
}

function arabicMessage(status: number, p?: ProblemDetail): string {
  if (p?.code && CODES[p.code]) return CODES[p.code]
  if (p?.errors) return Object.values(p.errors).join(' · ')
  if (status === 401) return p?.detail === 'Invalid email or password' ? 'البريد أو كلمة المرور غير صحيحة.' : 'سجّل الدخول للمتابعة.'
  if (status === 403) return p?.detail?.includes('suspended') ? 'الحساب أو الجهة معلّقة.' : 'ليست لديك صلاحية لهذا الإجراء.'
  if (status === 404) return 'العنصر غير موجود.'
  if (status >= 500) return 'حدث خطأ في الخادم. حاول لاحقًا.'
  return p?.detail ?? 'حدث خطأ غير متوقع.'
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
