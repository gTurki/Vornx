import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { dateTime, money } from '../lib/format'
import { TX } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { useToast } from '../ui/Toast'

/** "المحفظة": balance, history, top up, and send money to another VORNX customer. */
export function WalletPage() {
  const [page, setPage] = useState(0)
  const [sheet, setSheet] = useState<'topup' | 'send' | null>(null)
  const wallet = useQuery({ queryKey: ['wallet'], queryFn: endpoints.wallet })
  const txs = useQuery({ queryKey: ['transactions', page], queryFn: () => endpoints.transactions(page) })

  return (
    <div className="cols">
      <section>
        <div className="panel" style={{ background: 'linear-gradient(145deg,var(--card2),var(--card1))', color: '#fff', border: 0 }}>
          <small style={{ opacity: .8 }}>الرصيد المتاح</small>
          <div><span className="big">{money(wallet.data?.balance)}</span> ر.س</div>
          <small style={{ opacity: .8 }}>IBAN <span className="num">{wallet.data?.iban}</span></small>
        </div>
        <ReturnFromPayment />
        <div className="form-actions" style={{ marginTop: 14 }}>
          <button className="btn" onClick={() => setSheet('topup')}><Icon name="plus" />شحن</button>
          <button className="btn soft" onClick={() => setSheet('send')}><Icon name="send" />تحويل</button>
        </div>
        {wallet.error && <ErrorBox error={wallet.error} />}
      </section>

      <section>
        <h2>العمليات</h2>
        {txs.isLoading && <Loading />}
        {txs.data?.content.length === 0 && <Empty>لا توجد عمليات بعد.</Empty>}
        <div className="panel" hidden={!txs.data?.content.length}>
          {txs.data?.content.map((t) => (
            <div key={t.reference} className="li">
              <span className="ent">
                <b>{t.partnerName ?? t.counterpartyName ?? TX[t.type]}</b>
                <small>{TX[t.type]}{t.description ? ` · ${t.description}` : ''} · {dateTime(t.createdAt)}</small>
              </span>
              <span className={t.direction === 'IN' ? 'amt-in' : ''} style={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                <Num>{t.direction === 'IN' ? '+' : '−'}{money(t.amount)}</Num>
              </span>
            </div>
          ))}
        </div>
        {txs.data && <Pager page={txs.data.page} totalPages={txs.data.totalPages} onChange={setPage} />}
      </section>

      <Drawer open={sheet !== null} onClose={() => setSheet(null)} title={sheet === 'topup' ? 'شحن المحفظة' : 'تحويل لعميل VORNX'}>
        {sheet === 'topup' && <TopUp onDone={() => setSheet(null)} />}
        {sheet === 'send' && <Send onDone={() => setSheet(null)} />}
      </Drawer>
    </div>
  )
}

function useRefresh() {
  const qc = useQueryClient()
  return () => { qc.invalidateQueries({ queryKey: ['wallet'] }); qc.invalidateQueries({ queryKey: ['transactions'] }) }
}

/**
 * Card top-up: the backend creates a Moyasar invoice, and we send the customer to Moyasar's own payment page
 * (mada, Visa, Mastercard, Apple Pay...). The card number is typed there, never on our site.
 * Moyasar then sends them back to /wallet?topup=TU-..., where <ReturnFromPayment> shows the result.
 */
function TopUp({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const refresh = useRefresh()
  const options = useQuery({ queryKey: ['top-up-options'], queryFn: endpoints.topUpOptions, staleTime: 5 * 60_000 })
  const [amount, setAmount] = useState('100')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const o = options.data

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (o?.cardEnabled) {
        const t = await endpoints.startTopUp(Number(amount))
        if (!t.checkoutUrl) throw new Error()
        window.location.assign(t.checkoutUrl) // leave for Moyasar's page; busy stays on
        return
      }
      await endpoints.topUp(Number(amount)) // dev only: simulated money
      refresh()
      toast('شُحنت محفظتك (تجريبي)')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر بدء الدفع')
    }
    setBusy(false)
  }

  if (options.isLoading) return <Loading />
  if (o && !o.cardEnabled && !o.simulatedEnabled) {
    return <div className="notice">الشحن بالبطاقة غير متاح حاليًا. سنفعّله قريبًا.</div>
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="chips">
        {['50', '100', '200', '500'].map((v) => (
          <button type="button" key={v} className={`chip${amount === v ? ' on' : ''}`} onClick={() => setAmount(v)}>{v} ر.س</button>
        ))}
      </div>
      <label className="field"><span>المبلغ (ر.س)</span>
        <input className="input" type="number" dir="ltr" required min={o?.min ?? 10} max={o?.max ?? 10000} step="0.01"
               value={amount} onChange={(e) => setAmount(e.target.value)} />
      </label>
      {o?.cardEnabled ? (
        <p className="mu" style={{ margin: 0 }}>
          ستنتقل إلى صفحة الدفع الآمنة من ميسّر (مدى، فيزا، ماستركارد، Apple Pay). لا نرى ولا نحفظ بيانات بطاقتك.
          {o.testMode && <><br /><b className="soon">وضع تجريبي: لن تُخصم أموال حقيقية، استخدم بطاقة اختبار من ميسّر.</b></>}
        </p>
      ) : (
        <p className="mu" style={{ margin: 0 }}>بيئة التطوير: شحن تجريبي فوري بدون بطاقة.</p>
      )}
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>
        {busy ? 'جارٍ التحويل لصفحة الدفع…' : o?.cardEnabled ? `ادفع ${money(Number(amount) || 0)} ر.س` : `شحن ${money(Number(amount) || 0)} ر.س`}
      </button>
    </form>
  )
}

/**
 * Back from Moyasar: /wallet?topup=TU-...
 * We never trust the URL: we ask OUR backend, which asks Moyasar. While still pending we ask again every 2 s
 * (a payment can take a few seconds to settle), for up to a minute.
 */
function ReturnFromPayment() {
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const ref = params.get('topup')
  const [startedAt] = useState(() => Date.now())
  const status = useQuery({
    queryKey: ['top-up', ref],
    queryFn: () => endpoints.topUpStatus(ref!),
    enabled: !!ref,
    retry: false,
    refetchInterval: (q) => (q.state.data?.status === 'PENDING' && Date.now() - startedAt < 60_000 ? 2000 : false),
  })
  const t = status.data
  const done = t && t.status !== 'PENDING'

  useEffect(() => {
    if (t?.status === 'PAID') {
      qc.invalidateQueries({ queryKey: ['wallet'] })
      qc.invalidateQueries({ queryKey: ['transactions'] })
      qc.invalidateQueries({ queryKey: ['notifications'] })
    }
  }, [t?.status, qc])

  if (!ref) return null
  const close = () => setParams({}, { replace: true }) // drop ?topup=... from the address bar

  return (
    <div className="notice" style={{ marginTop: 14 }} role="status">
      {status.error && <span className="bad">تعذّر التحقق من عملية الدفع {ref}.</span>}
      {!status.error && !done && <span><span className="pulse" />نتحقق من الدفع مع ميسّر…</span>}
      {t?.status === 'PAID' && <b className="ok">✓ تم شحن <Num>{money(t.amount)}</Num> ر.س في محفظتك.</b>}
      {t?.status === 'FAILED' && <b className="bad">لم يكتمل الدفع، ولم يُخصم شيء من رصيدك. يمكنك المحاولة مرة أخرى.</b>}
      {t?.status === 'EXPIRED' && <b className="bad">انتهت مهلة الدفع لهذه العملية.</b>}
      {t?.status === 'PENDING' && Date.now() - startedAt >= 60_000 && (
        <span className="mu"> ما زالت قيد المعالجة. ستصلك رسالة فور اكتمالها، ويُضاف المبلغ تلقائيًا.</span>
      )}
      <div className="form-actions" style={{ marginTop: 8 }}>
        {t?.status === 'PENDING' && t.checkoutUrl && <a className="btn sm" href={t.checkoutUrl}>إكمال الدفع</a>}
        <button className="btn gh sm" onClick={close}>إغلاق</button>
      </div>
    </div>
  )
}

function Send({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const refresh = useRefresh()
  const [to, setTo] = useState('')
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!confirming) { setConfirming(true); return } // money leaves the wallet: always confirm first
    setBusy(true)
    try {
      await endpoints.transfer(to.trim().toUpperCase(), Number(amount), note.trim() || undefined)
      refresh()
      toast('تم التحويل')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر التحويل')
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>رقم VORNX للمستلم</span>
        <input className="input" dir="ltr" required placeholder="VX0000000000" value={to} onChange={(e) => { setTo(e.target.value); setConfirming(false) }} />
      </label>
      <label className="field"><span>المبلغ (ر.س، حتى 5,000)</span>
        <input className="input" type="number" dir="ltr" required min={1} max={5000} step="0.01" value={amount} onChange={(e) => { setAmount(e.target.value); setConfirming(false) }} />
      </label>
      <label className="field"><span>ملاحظة (اختياري)</span><input className="input" maxLength={100} value={note} onChange={(e) => setNote(e.target.value)} /></label>
      {error && <div className="error-box">{error}</div>}
      {confirming && <div className="notice" style={{ margin: 0 }}>ستحوّل <b><Num>{money(Number(amount))}</Num> ر.س</b> إلى <b><Num>{to.trim().toUpperCase()}</Num></b>. لا يمكن التراجع.</div>}
      <button className={`btn wide${confirming ? ' danger' : ''}`} disabled={busy}>{confirming ? 'تأكيد التحويل' : 'متابعة'}</button>
    </form>
  )
}
