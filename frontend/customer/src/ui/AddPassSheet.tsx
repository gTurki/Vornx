import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { Pass } from '../api/types'
import { guessColor, scanFrame, scanImage, type PassFormat, type Scanned } from '../lib/barcode'
import { tr } from '../i18n'
import { CodeImage } from './AdaptiveCard'
import { Drawer } from './Drawer'
import { useToast } from './Toast'

const COLORS = ['#1000B4', '#0B1224', '#7A6420', '#3FD98B', '#B3261E', '#6D28D9', '#0E7490', '#9CA3AF']
const MANUAL_FORMATS: [PassFormat, string][] = [['QR_CODE', 'QR'], ['CODE_128', 'Code 128'], ['EAN_13', 'EAN-13'], ['CODE_39', 'Code 39'], ['PDF417', 'PDF417'], ['AZTEC', 'Aztec']]

/** A short code that looks like a card number is also printed under the barcode. */
const looksLikeNumber = (s: string) => /^[A-Za-z0-9 -]{4,24}$/.test(s)

/**
 * "إضافة بطاقة": scan a loyalty card / membership / ticket with the camera, from a screenshot (e.g. of an Apple Wallet
 * pass), or type its number. Then name it, pick its color, and optionally say where it's used (to show it there).
 */
export function AddPassSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [step, setStep] = useState<'pick' | 'camera' | 'form'>('pick')
  const [scanned, setScanned] = useState<Scanned | null>(null)
  const [color, setColor] = useState(COLORS[0])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const file = useRef<HTMLInputElement>(null)

  useEffect(() => { if (open) { setStep('pick'); setScanned(null); setError(null); setColor(COLORS[0]) } }, [open])

  async function fromImage(f: File | undefined) {
    if (!f) return
    setBusy(true); setError(null)
    try {
      const [s, c] = await Promise.all([scanImage(f), guessColor(f)])
      if (!s) { setError(tr('لم نجد رمزًا في الصورة. جرّب صورة أوضح للرمز، أو أدخل الرقم يدويًا.')); return }
      setScanned(s); setColor(c); setStep('form')
    } catch {
      setError(tr('تعذّرت قراءة الصورة.'))
    } finally {
      setBusy(false)
      if (file.current) file.current.value = ''
    }
  }

  return (
    <Drawer open={open} onClose={onClose} title={tr('إضافة بطاقة')}>
      {step === 'pick' && (
        <div className="form">
          <p className="mu" style={{ margin: 0 }}>{tr('بطاقة ولاء، عضوية، أو تذكرة: نحفظ رمزها، ويظهر تحت بطاقة VORNX عندما تكون في المكان.')}</p>
          <button className="btn wide" onClick={() => setStep('camera')}>📷 {tr('امسح بالكاميرا')}</button>
          <button className="btn gh wide" disabled={busy} onClick={() => file.current?.click()}>🖼️ {busy ? tr('جارٍ القراءة…') : tr('من صورة أو لقطة شاشة')}</button>
          <input ref={file} type="file" accept="image/*" hidden onChange={(e) => fromImage(e.target.files?.[0])} />
          <button className="btn gh wide" onClick={() => { setScanned({ code: '', format: 'QR_CODE' }); setStep('form') }}>⌨️ {tr('أدخل الرقم يدويًا')}</button>
          <small className="mu">{tr('لقطة شاشة لبطاقة في Apple Wallet تعمل أيضًا.')}</small>
          {error && <div className="error-box">{error}</div>}
        </div>
      )}
      {step === 'camera' && <CameraScan onFound={(s) => { setScanned(s); setStep('form') }} onCancel={() => setStep('pick')} />}
      {step === 'form' && scanned && <PassForm scanned={scanned} initialColor={color} onDone={onClose} onBack={() => setStep('pick')} />}
    </Drawer>
  )
}

/** The back camera, read a few times a second until a code is found. */
function CameraScan({ onFound, onCancel }: { onFound: (s: Scanned) => void; onCancel: () => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let stream: MediaStream | null = null, timer = 0, stopped = false
    const canvas = document.createElement('canvas')
    ;(async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
        if (stopped) { stream.getTracks().forEach((t) => t.stop()); return }
        const v = video.current!
        v.srcObject = stream
        await v.play()
        const tick = async () => {
          if (stopped) return
          if (v.videoWidth) {
            const w = Math.min(960, v.videoWidth), h = Math.round((v.videoHeight * w) / v.videoWidth)
            canvas.width = w; canvas.height = h
            const g = canvas.getContext('2d', { willReadFrequently: true })!
            g.drawImage(v, 0, 0, w, h)
            const found = await scanFrame(g.getImageData(0, 0, w, h)).catch(() => null)
            if (found && !stopped) { navigator.vibrate?.(40); onFound(found); return }
          }
          timer = window.setTimeout(tick, 250)
        }
        tick()
      } catch {
        setError(tr('لم نتمكن من فتح الكاميرا. اسمح للتطبيق باستخدامها، أو اختر صورة بدلًا منها.'))
      }
    })()
    return () => { stopped = true; clearTimeout(timer); stream?.getTracks().forEach((t) => t.stop()) }
  }, [onFound])

  return (
    <div className="form">
      <div className="cam"><video ref={video} playsInline muted /><i className="cam-frame" aria-hidden="true" /></div>
      <p className="mu" style={{ margin: 0, textAlign: 'center' }}>{tr('وجّه الكاميرا إلى الرمز (QR أو باركود)')}</p>
      {error && <div className="error-box">{error}</div>}
      <button className="btn gh wide" onClick={onCancel}>{tr('رجوع')}</button>
    </div>
  )
}

/** Name, color, where it's used. Manual entry also asks for the code and its kind. */
function PassForm({ scanned, initialColor, onDone, onBack }: { scanned: Scanned; initialColor: string; onDone: () => void; onBack: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const manual = scanned.code === ''
  const [code, setCode] = useState(scanned.code)
  const [format, setFormat] = useState<PassFormat>(scanned.format)
  const [name, setName] = useState('')
  const [number, setNumber] = useState(looksLikeNumber(scanned.code) ? scanned.code : '')
  const [color, setColor] = useState(initialColor)
  const [place, setPlace] = useState<{ lat: number; lng: number } | null>(null)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function here() {
    if (!('geolocation' in navigator)) { setError(tr('هذا الجهاز لا يدعم تحديد الموقع.')); return }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (p) => { setPlace({ lat: p.coords.latitude, lng: p.coords.longitude }); setLocating(false) },
      () => { setError(tr('لم نتمكن من تحديد موقعك.')); setLocating(false) },
      { enableHighAccuracy: true, timeout: 15_000 },
    )
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      await endpoints.addPass({ name: name.trim(), code: code.trim(), format, shownNumber: number.trim() || null, color,
        placeName: place ? name.trim() : null, latitude: place?.lat ?? null, longitude: place?.lng ?? null })
      qc.invalidateQueries({ queryKey: ['passes'] })
      toast(tr('أُضيفت البطاقة'))
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : tr('تعذّر الحفظ'))
    } finally {
      setBusy(false)
    }
  }

  const preview: Pass | null = code.trim() ? { id: 0, name, code: code.trim(), format, shownNumber: null, color, placeName: null, latitude: null, longitude: null, createdAt: '' } : null
  return (
    <form className="form" onSubmit={save}>
      {preview && <div className="code-panel" style={{ ['--pc' as string]: color }}><CodeImage pass={preview} /></div>}
      {manual && (
        <div className="row2">
          <label className="field"><span>{tr('الرقم أو محتوى الرمز')}</span>
            <input className="input" dir="ltr" required maxLength={2048} value={code} onChange={(e) => { setCode(e.target.value); setNumber(e.target.value) }} /></label>
          <label className="field"><span>{tr('نوع الرمز')}</span>
            <select className="input" value={format} onChange={(e) => setFormat(e.target.value as PassFormat)}>
              {MANUAL_FORMATS.map(([f, l]) => <option key={f} value={f}>{l}</option>)}
            </select></label>
        </div>
      )}
      <label className="field"><span>{tr('اسم البطاقة')}</span>
        <input className="input" required maxLength={80} placeholder={tr('مثال: Lavin، بيت الثقافة')} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label className="field"><span>{tr('الرقم الظاهر تحت الرمز (اختياري)')}</span>
        <input className="input" dir="ltr" maxLength={80} value={number} onChange={(e) => setNumber(e.target.value)} /></label>
      <div className="field"><span>{tr('اللون')}</span>
        <div className="swatches">
          {[...new Set([initialColor, ...COLORS])].map((c) => (
            <button type="button" key={c} className={c === color ? 'on' : ''} style={{ background: c }} aria-label={c} onClick={() => setColor(c)} />
          ))}
        </div>
      </div>
      <div className="field"><span>{tr('أين تستخدمها؟ (ليظهر رمزها تلقائيًا هناك)')}</span>
        {place
          ? <div className="li"><span className="ok">📍 {tr('تم حفظ موقعك الحالي')}</span><button type="button" className="link" onClick={() => setPlace(null)}>{tr('إزالة')}</button></div>
          : <button type="button" className="btn gh" disabled={locating} onClick={here}>📍 {locating ? tr('جارٍ تحديد موقعك…') : tr('أنا في المكان الآن: استخدم موقعي')}</button>}
      </div>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn" disabled={busy || !name.trim() || !code.trim()}>{busy ? tr('جارٍ الحفظ…') : tr('حفظ البطاقة')}</button>
        <button type="button" className="btn gh" onClick={onBack}>{tr('رجوع')}</button>
      </div>
    </form>
  )
}
