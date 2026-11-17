import { lang, tr } from '../i18n'
// SMS login codes through Firebase phone auth.
//
// Firebase sends the SMS and checks the code. Then it gives us an "ID token" (signed by Google) that we hand
// to our API, which checks it is for the customer's own number. We never keep a Firebase session: the
// persistence is in-memory and we sign out right after.
//
// The Firebase SDK is big, so it is loaded only when an SMS login starts (dynamic import).
// The config values are public (they identify the project, they are not secrets) and baked in at build time.

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
}

/** False when the app was built without the Firebase variables: the login then continues by email. */
export const smsAvailable = Boolean(config.apiKey && config.authDomain && config.projectId && config.appId)

export type SmsSession = {
  /** Checks the typed code with Firebase and returns the ID token for our API. */
  confirm: (code: string) => Promise<string>
  /** Forget the Firebase session and the reCAPTCHA widget. */
  dispose: () => void
}

/**
 * Sends the SMS. `container` is an empty element for the invisible reCAPTCHA (Firebase's bot check:
 * usually nobody sees it; a suspicious visitor gets a picture puzzle).
 */
export async function sendSms(phoneE164: string, container: HTMLElement): Promise<SmsSession> {
  const { initializeApp, getApps } = await import('firebase/app')
  const { initializeAuth, getAuth, inMemoryPersistence, RecaptchaVerifier, signInWithPhoneNumber, signOut } =
    await import('firebase/auth')

  const existing = getApps().find((a) => a.name === 'vornx-sms')
  const app = existing ?? initializeApp(config, 'vornx-sms')
  const auth = existing ? getAuth(app) : initializeAuth(app, { persistence: inMemoryPersistence })
  auth.languageCode = lang // the SMS text and the reCAPTCHA in the app's language

  container.innerHTML = '' // a fresh widget every time (Firebase requires it after a failure)
  const verifier = new RecaptchaVerifier(auth, container, { size: 'invisible' })
  const dispose = () => {
    verifier.clear()
    signOut(auth).catch(() => undefined)
  }
  try {
    const confirmation = await signInWithPhoneNumber(auth, phoneE164, verifier)
    return {
      confirm: async (code) => {
        const result = await confirmation.confirm(code)
        return result.user.getIdToken()
      },
      dispose,
    }
  } catch (e) {
    dispose()
    throw e
  }
}

/** Firebase error -> Arabic message, and whether trying again by SMS makes sense. */
export function smsError(e: unknown): { text: string; useEmail: boolean } {
  const code = (e as { code?: string })?.code ?? ''
  switch (code) {
    case 'auth/invalid-verification-code':
      return { text: tr('الرمز غير صحيح.'), useEmail: false }
    case 'auth/code-expired':
      return { text: tr('انتهت صلاحية الرمز. اطلب رمزًا جديدًا.'), useEmail: false }
    case 'auth/too-many-requests':
    case 'auth/quota-exceeded':
      return { text: tr('تعذّر إرسال الرسائل النصية الآن.'), useEmail: true }
    case 'auth/network-request-failed':
      return { text: tr('تعذّر الاتصال. تحقق من الإنترنت ثم حاول مجددًا.'), useEmail: false }
    default:
      return { text: tr('تعذّر التحقق عبر الرسائل النصية.'), useEmail: true }
  }
}
