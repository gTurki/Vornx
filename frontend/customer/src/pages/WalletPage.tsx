import { useState, type FormEvent } from 'react'
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

function TopUp({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const refresh = useRefresh()
  const [amount, setAmount] = useState('100')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await endpoints.topUp(Number(amount))
      refresh()
      toast('شُحنت محفظتك')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الشحن')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="chips">
        {['50', '100', '200', '500'].map((v) => (
          <button type="button" key={v} className={`chip${amount === v ? ' on' : ''}`} onClick={() => setAmount(v)}>{v} ر.س</button>
        ))}
      </div>
      <label className="field"><span>المبلغ (ر.س)</span>
        <input className="input" type="number" dir="ltr" required min={1} max={10000} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
      </label>
      <p className="mu" style={{ margin: 0 }}>الدفع بالبطاقة البنكية قيد الربط. حاليًا الشحن تجريبي في بيئة التطوير فقط.</p>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>شحن {money(Number(amount) || 0)} ر.س</button>
    </form>
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
