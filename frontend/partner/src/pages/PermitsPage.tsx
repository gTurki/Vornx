import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AccessLevel, DayOfWeek, Permit, PermitType } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { dateTime, fromRiyadhInput, hhmm, riyadhInputIn, toRiyadhInput } from '../lib/format'
import { DAY, keys, LEVEL, PERMIT_STATUS, PERMIT_TYPE } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { AccessPointChecks, DayChecks, SiteSelect } from '../ui/pickers'
import { useToast } from '../ui/Toast'

/** "التصاريح والمفاتيح": who may open which doors, and until when. */
export function PermitsPage() {
  const can = useCan()
  const [siteId, setSiteId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const [issuing, setIssuing] = useState(false)
  const params = { siteId: siteId ?? undefined, page }
  const list = useQuery({ queryKey: ['permits', params], queryFn: () => endpoints.permits(params) })

  return (
    <>
      <p className="lead">
        التصريح هو المفتاح الرقمي: يربط عميل VORNX بموقعك وأبوابه لفترة محددة. يفتح العميل بالبطاقة أو الجوال،
        ويُلغى المفتاح تلقائيًا عند انتهاء الفترة.
      </p>
      <div className="toolbar">
        <SiteSelect all value={siteId} onChange={(v) => { setSiteId(v); setPage(0) }} />
        <span className="grow" />
        {can('operate') && <button className="btn" onClick={() => setIssuing(true)}><Icon name="plus" />إصدار تصريح</button>}
      </div>

      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>صاحب التصريح</th><th>النوع</th><th>الموقع</th><th>صالح حتى</th><th>الحالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((p) => (
                <tr key={p.id} className="click" onClick={() => setOpenId(p.id)}>
                  <td className="ent"><b>{p.holderName}</b><small><Num>{p.holderVornxId}</Num> · <Num>{p.permitNumber}</Num></small></td>
                  <td>{PERMIT_TYPE[p.type]}</td>
                  <td className="ent"><b>{p.siteName}</b><small>{p.wholeSite ? 'كل الموقع' : p.accessPoints.map((a) => a.name).join('، ')}</small></td>
                  <td>{dateTime(p.validUntil)}</td>
                  <td><Tag label={PERMIT_STATUS[p.status]} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد تصاريح بعد.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={openId !== null} onClose={() => setOpenId(null)} title="التصريح">
        {openId !== null && <PermitDetails id={openId} />}
      </Drawer>
      <Drawer open={issuing} onClose={() => setIssuing(false)} title="إصدار تصريح جديد">
        {issuing && <IssueForm onDone={(id) => { setIssuing(false); setOpenId(id) }} />}
      </Drawer>
    </>
  )
}

function PermitDetails({ id }: { id: number }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const permit = useQuery({ queryKey: ['permit', id], queryFn: () => endpoints.permit(id) })
  const [until, setUntil] = useState('')
  const saved = (p: Permit, msg: string) => {
    qc.setQueryData(['permit', id], p)
    qc.invalidateQueries({ queryKey: ['permits'] })
    toast(msg)
  }
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const revoke = useMutation({ mutationFn: () => endpoints.revokePermit(id), onSuccess: (p) => saved(p, 'أُلغي التصريح وأُبلغ العميل'), onError })
  const extend = useMutation({
    mutationFn: () => endpoints.extendPermit(id, fromRiyadhInput(until)),
    onSuccess: (p) => { saved(p, 'مُدّد التصريح'); setUntil('') },
    onError,
  })

  if (permit.isLoading) return <Loading />
  if (permit.error) return <ErrorBox error={permit.error} />
  const p = permit.data!
  const hours = p.allowedFrom ? `${hhmm(p.allowedFrom)} – ${hhmm(p.allowedTo)}` : 'طوال اليوم'

  return (
    <>
      <div className="li"><b>{p.holderName}</b><Tag label={PERMIT_STATUS[p.status]} /></div>
      <Kv k="رقم VORNX"><Num>{p.holderVornxId}</Num></Kv>
      <Kv k="رقم التصريح"><Num>{p.permitNumber}</Num></Kv>
      <Kv k="رمز التحقق"><Num>{p.verificationCode}</Num></Kv>
      <Kv k="النوع">{PERMIT_TYPE[p.type]} · مستوى {LEVEL[p.level]}</Kv>
      <Kv k="الموقع">{p.siteName}</Kv>
      <Kv k="نقاط الدخول">{p.wholeSite ? 'كل الموقع' : p.accessPoints.map((a) => a.name).join('، ')}</Kv>
      <Kv k="من">{dateTime(p.validFrom)}</Kv>
      <Kv k="حتى">{dateTime(p.validUntil)}</Kv>
      <Kv k="الأيام">{p.allowedDays.length ? p.allowedDays.map((d) => DAY[d]).join('، ') : 'كل الأيام'}</Kv>
      <Kv k="الأوقات"><Num>{hours}</Num></Kv>
      {p.conditions && <Kv k="شروط">{p.conditions}</Kv>}

      {can('operate') && p.status !== 'REVOKED' && (
        <>
          <h2 className="section">تمديد</h2>
          <form className="form" onSubmit={(e) => { e.preventDefault(); extend.mutate() }}>
            <label className="field"><span>صالح حتى (بتوقيت الرياض)</span>
              <input className="input" type="datetime-local" dir="ltr" required min={toRiyadhInput(new Date().toISOString())}
                     value={until || toRiyadhInput(p.validUntil)} onChange={(e) => setUntil(e.target.value)} />
            </label>
            <div className="form-actions">
              <button className="btn" disabled={!until || extend.isPending}>حفظ التاريخ الجديد</button>
              <ConfirmButton onConfirm={() => revoke.mutate()} disabled={revoke.isPending}>إلغاء التصريح</ConfirmButton>
            </div>
          </form>
        </>
      )}
    </>
  )
}

function IssueForm({ onDone }: { onDone: (id: number) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [vornxId, setVornxId] = useState('')
  const [type, setType] = useState<PermitType>('HOTEL_KEY')
  const [level, setLevel] = useState<AccessLevel>('STANDARD')
  const [siteId, setSiteId] = useState<number | null>(null)
  const [aps, setAps] = useState<number[]>([])
  const [from, setFrom] = useState(riyadhInputIn(0))
  const [until, setUntil] = useState(riyadhInputIn(24))
  const [days, setDays] = useState<DayOfWeek[]>([])
  const [hoursFrom, setHoursFrom] = useState('')
  const [hoursTo, setHoursTo] = useState('')
  const [conditions, setConditions] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const p = await endpoints.issuePermit({
        customerVornxId: vornxId.trim().toUpperCase(),
        type, level, siteId: siteId!, accessPointIds: aps,
        validFrom: fromRiyadhInput(from), validUntil: fromRiyadhInput(until),
        allowedDays: days, allowedFrom: hoursFrom || null, allowedTo: hoursTo || null,
        conditions: conditions.trim() || undefined,
      })
      qc.invalidateQueries({ queryKey: ['permits'] })
      toast('صدر التصريح ووصل للعميل في تطبيقه')
      onDone(p.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الإصدار')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>رقم VORNX للعميل (يجده في تطبيقه)</span>
        <input className="input" dir="ltr" required placeholder="VX0000000000" value={vornxId} onChange={(e) => setVornxId(e.target.value)} />
      </label>
      <div className="row2">
        <label className="field"><span>نوع التصريح</span>
          <select className="input" value={type} onChange={(e) => setType(e.target.value as PermitType)}>
            {keys(PERMIT_TYPE).map((t) => <option key={t} value={t}>{PERMIT_TYPE[t]}</option>)}
          </select>
        </label>
        <label className="field"><span>المستوى</span>
          <select className="input" value={level} onChange={(e) => setLevel(e.target.value as AccessLevel)}>
            {keys(LEVEL).map((l) => <option key={l} value={l}>{LEVEL[l]}</option>)}
          </select>
        </label>
      </div>
      <label className="field"><span>الموقع</span>
        <SiteSelect value={siteId} onChange={(v) => { setSiteId(v); setAps([]) }} />
      </label>
      <div className="field"><span>نقاط الدخول (بدون اختيار = كل الموقع)</span>
        <AccessPointChecks siteId={siteId} value={aps} onChange={setAps} />
      </div>
      <div className="row2">
        <label className="field"><span>من (بتوقيت الرياض)</span>
          <input className="input" type="datetime-local" dir="ltr" required value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="field"><span>حتى</span>
          <input className="input" type="datetime-local" dir="ltr" required min={from} value={until} onChange={(e) => setUntil(e.target.value)} />
        </label>
      </div>
      <div className="field"><span>الأيام (بدون اختيار = كل الأيام)</span><DayChecks value={days} onChange={setDays} /></div>
      <div className="row2">
        <label className="field"><span>من الساعة (اختياري)</span>
          <input className="input" type="time" dir="ltr" value={hoursFrom} onChange={(e) => setHoursFrom(e.target.value)} />
        </label>
        <label className="field"><span>إلى الساعة</span>
          <input className="input" type="time" dir="ltr" value={hoursTo} onChange={(e) => setHoursTo(e.target.value)} />
        </label>
      </div>
      <label className="field"><span>شروط أو ملاحظات للعميل (اختياري)</span>
        <textarea className="input" maxLength={500} value={conditions} onChange={(e) => setConditions(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn" disabled={busy || siteId === null}>{busy ? 'جارٍ الإصدار…' : 'إصدار'}</button></div>
    </form>
  )
}
