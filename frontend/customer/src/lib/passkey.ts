// Passkeys = sign in with Face ID / Touch ID / fingerprint / Windows Hello (WebAuthn).
// The server sends the options as JSON with base64url strings; the browser API wants bytes. These helpers convert
// both ways, so they also work on browsers without PublicKeyCredential.parseCreationOptionsFromJSON (older iOS).

/** The browser can do passkeys at all (any modern browser on https or localhost). */
export function passkeySupported(): boolean {
  return typeof window !== 'undefined' && 'PublicKeyCredential' in window && !!navigator.credentials
}

/** This device has Face ID / a fingerprint / Windows Hello (not only a USB security key). */
export async function deviceHasBiometrics(): Promise<boolean> {
  if (!passkeySupported()) return false
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

/** Face ID / Touch ID on Apple, "البصمة" elsewhere: the label people recognise on this device. */
export function biometricName(): 'faceid' | 'fingerprint' | 'hello' {
  const ua = navigator.userAgent
  if (/iPhone|iPad|Macintosh/.test(ua)) return 'faceid'
  if (/Windows/.test(ua)) return 'hello'
  return 'fingerprint'
}

const toBytes = (b64url: string): ArrayBuffer => {
  const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((b64url.length + 3) % 4)
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out.buffer
}

const toB64 = (buf: ArrayBuffer | null | undefined): string | null => {
  if (!buf) return null
  const bytes = new Uint8Array(buf)
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

type Json = Record<string, any> // eslint-disable-line @typescript-eslint/no-explicit-any

const descriptors = (list?: Json[]) => list?.map((d) => ({ ...d, id: toBytes(d.id) }))

/** "حسابي" > add this device: Face ID, then the new public key goes to the server (as a JSON string). */
export async function createPasskey(optionsJson: string): Promise<string> {
  const pk: Json = JSON.parse(optionsJson).publicKey
  const publicKey = {
    ...pk,
    challenge: toBytes(pk.challenge),
    user: { ...pk.user, id: toBytes(pk.user.id) },
    excludeCredentials: descriptors(pk.excludeCredentials),
  } as PublicKeyCredentialCreationOptions
  const cred = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential | null
  if (!cred) throw new DOMException('cancelled', 'NotAllowedError')
  const r = cred.response as AuthenticatorAttestationResponse
  return JSON.stringify({
    id: cred.id,
    rawId: toB64(cred.rawId),
    type: cred.type,
    authenticatorAttachment: cred.authenticatorAttachment ?? undefined,
    response: {
      clientDataJSON: toB64(r.clientDataJSON),
      attestationObject: toB64(r.attestationObject),
      transports: typeof r.getTransports === 'function' ? r.getTransports() : undefined,
    },
    clientExtensionResults: cred.getClientExtensionResults(),
  })
}

/** Sign in: the phone lists its VORNX passkeys, Face ID, then it signs the server's challenge. */
export async function getPasskey(optionsJson: string): Promise<string> {
  const pk: Json = JSON.parse(optionsJson).publicKey
  const publicKey = {
    ...pk,
    challenge: toBytes(pk.challenge),
    allowCredentials: descriptors(pk.allowCredentials),
  } as PublicKeyCredentialRequestOptions
  const cred = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential | null
  if (!cred) throw new DOMException('cancelled', 'NotAllowedError')
  const r = cred.response as AuthenticatorAssertionResponse
  return JSON.stringify({
    id: cred.id,
    rawId: toB64(cred.rawId),
    type: cred.type,
    authenticatorAttachment: cred.authenticatorAttachment ?? undefined,
    response: {
      clientDataJSON: toB64(r.clientDataJSON),
      authenticatorData: toB64(r.authenticatorData),
      signature: toB64(r.signature),
      userHandle: toB64(r.userHandle),
    },
    clientExtensionResults: cred.getClientExtensionResults(),
  })
}

/** The person closed the Face ID sheet (or it timed out): not an error worth showing. */
export function wasCancelled(e: unknown): boolean {
  return e instanceof DOMException && (e.name === 'NotAllowedError' || e.name === 'AbortError')
}

/** create() refused because this device already has a VORNX passkey (excludeCredentials). */
export function alreadyOnDevice(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'InvalidStateError'
}
