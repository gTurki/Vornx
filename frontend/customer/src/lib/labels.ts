// Arabic labels for the backend's enum values, and the color ("tone") each status gets.
import type {
  AttendeeRole, BillingPeriod, CardStatus, DayOfWeek, DenyReason, EventTicketStatus, LoyaltyTier, PermitStatus,
  PermitType, ReminderOption, RequestStatus, RequestType, SubscriptionStatus, TransactionType,
} from '../api/types'
import { tr } from '../i18n'

export type Tone = 'ok' | 'soon' | 'bad' | 'mu'
export type Label = { text: string; tone: Tone }

export const keys = <K extends string>(o: Record<K, unknown>) => Object.keys(o) as K[]

export const TIER: Record<LoyaltyTier, { text: string; next: number | null }> = {
  BRONZE: { text: tr('برونزية'), next: 1000 },
  SILVER: { text: tr('فضية'), next: 2500 },
  GOLD: { text: tr('ذهبية'), next: 5000 },
  PLATINUM: { text: tr('بلاتينية'), next: null },
}

export const CARD_STATUS: Record<CardStatus, Label> = {
  ACTIVE: { text: tr('فعّالة'), tone: 'ok' },
  BLOCKED: { text: tr('موقوفة'), tone: 'bad' },
  LOST: { text: tr('مُبلّغ عن فقدها'), tone: 'bad' },
  EXPIRED: { text: tr('منتهية'), tone: 'mu' },
}

export const PERMIT_TYPE: Record<PermitType, string> = {
  HOTEL_KEY: tr('مفتاح غرفة'), WORKPLACE: tr('دوام'), CLUB: tr('نادٍ'), PARKING: tr('موقف'), TRANSIT: tr('نقل'),
  EVENT_PASS: tr('فعالية'), HOME: tr('المنزل'), CAR: tr('سيارة'),
}

/** Same short codes as the icon set, one per permit type. */
export const PERMIT_ICON: Record<PermitType, 'bed' | 'work' | 'gym' | 'parking' | 'bus' | 'ticket' | 'home' | 'car'> = {
  HOTEL_KEY: 'bed', WORKPLACE: 'work', CLUB: 'gym', PARKING: 'parking', TRANSIT: 'bus',
  EVENT_PASS: 'ticket', HOME: 'home', CAR: 'car',
}

export const PERMIT_STATUS: Record<PermitStatus, Label> = {
  PENDING: { text: tr('يبدأ لاحقًا'), tone: 'soon' },
  ACTIVE: { text: tr('فعّال'), tone: 'ok' },
  SUSPENDED: { text: tr('موقوف'), tone: 'soon' },
  EXPIRED: { text: tr('منتهٍ'), tone: 'mu' },
  REVOKED: { text: tr('ملغى'), tone: 'bad' },
}

export const DAYS: [DayOfWeek, string][] = [
  ['SATURDAY', tr('السبت')], ['SUNDAY', tr('الأحد')], ['MONDAY', tr('الاثنين')], ['TUESDAY', tr('الثلاثاء')],
  ['WEDNESDAY', tr('الأربعاء')], ['THURSDAY', tr('الخميس')], ['FRIDAY', tr('الجمعة')],
]
export const DAY: Record<DayOfWeek, string> = Object.fromEntries(DAYS) as Record<DayOfWeek, string>

/** Why a door said no, in words a customer understands. */
export const DENY: Record<DenyReason, string> = {
  CARD_INVALID: tr('البطاقة غير صالحة'),
  ACCOUNT_INACTIVE: tr('حسابك غير نشط'),
  METHOD_NOT_SUPPORTED: tr('هذا الباب لا يدعم الفتح عن بُعد'),
  ACCESS_POINT_UNAVAILABLE: tr('الباب غير متاح الآن'),
  NO_PERMIT: tr('ليس لديك تصريح لهذا الباب'),
  PERMIT_INACTIVE: tr('تصريحك غير نشط'),
  PERMIT_NOT_YET_VALID: tr('تصريحك لم يبدأ بعد'),
  PERMIT_EXPIRED: tr('انتهى تصريحك'),
  DAY_NOT_ALLOWED: tr('تصريحك لا يشمل هذا اليوم'),
  OUTSIDE_HOURS: tr('خارج الأوقات المسموحة'),
}

export const PERIOD: Record<BillingPeriod, string> = { WEEKLY: tr('أسبوع'), MONTHLY: tr('شهر'), QUARTERLY: tr('3 أشهر'), YEARLY: tr('سنة') }

export const REQUEST_TYPE: Record<RequestType, string> = { ACTIVATION: tr('اشتراك جديد'), RENEWAL: tr('تجديد') }

export const REQUEST_STATUS: Record<RequestStatus, Label> = {
  PENDING: { text: tr('قيد المراجعة'), tone: 'soon' },
  APPROVED: { text: tr('مقبول'), tone: 'ok' },
  REJECTED: { text: tr('مرفوض'), tone: 'bad' },
  CANCELLED: { text: tr('ألغيته'), tone: 'mu' },
}

export const SUB_STATUS: Record<SubscriptionStatus, Label> = {
  PENDING: { text: tr('بانتظار الموافقة'), tone: 'soon' },
  ACTIVE: { text: tr('فعّال'), tone: 'ok' },
  EXPIRED: { text: tr('منتهٍ'), tone: 'mu' },
  CANCELLED: { text: tr('ملغى'), tone: 'mu' },
  REJECTED: { text: tr('مرفوض'), tone: 'bad' },
}

export const TICKET_STATE: Record<EventTicketStatus, Label> = {
  UNDER_REVIEW: { text: tr('قيد المراجعة'), tone: 'soon' },
  CONFIRMED: { text: tr('مؤكدة'), tone: 'ok' },
  CHECKED_IN: { text: tr('حضرت'), tone: 'ok' },
  CANCELLED: { text: tr('ملغاة'), tone: 'mu' },
  REJECTED: { text: tr('مرفوضة'), tone: 'bad' },
}

export const ATTENDEE: Record<AttendeeRole, string> = {
  ATTENDEE: tr('حضور'), SPEAKER: tr('متحدث'), EXHIBITOR: tr('عارض'), FOUNDER: tr('مؤسس'), VIP: tr('كبار الضيوف'), ORGANIZER: tr('منظّم'),
}

export const REMINDER: Record<ReminderOption, string> = { ONE_HOUR: tr('قبل ساعة'), ONE_DAY: tr('قبل يوم'), ONE_WEEK: tr('قبل أسبوع') }

export const TX: Record<TransactionType, string> = {
  TOP_UP: tr('شحن المحفظة'), TRANSFER_IN: tr('تحويل وارد'), TRANSFER_OUT: tr('تحويل صادر'), PAYMENT: tr('دفع'),
  REFUND: tr('استرداد'), CASHBACK: tr('استرداد نقدي'),
}
