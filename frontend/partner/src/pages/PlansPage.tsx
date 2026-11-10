import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { BillingPeriod, DayOfWeek, PermitType, Plan } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { hhmm, money } from '../lib/format'
import { DAY, keys, PERIOD, PERMIT_TYPE } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { AccessPointChecks, DayChecks, SiteSelect } from '../ui/pickers'
import { useToast } from '../ui/Toast'

/** "الباقات": what customers can subscribe to from the app (a gym membership, a monthly parking spot...). */
export function PlansPage() {
  const can = useCan()
  const plans = useQuery({ queryKey: ['plans'], queryFn: endpoints.plans })
  const [editing, setEditing] = useState<Plan | 'new' | null>(null)

  return (
    <>
      <p className="lead">الباقة تظهر للعملاء في تطبيق VORNX. عند الاشتراك يُدفع السعر من المحفظة، ويحصل العميل على تصريح بنفس النقاط والأيام والأوقات.</p>
      <div className="toolbar">
        <span className="grow" />
        {can('edit') && <button className="btn" onClick={() => setEditing('new')}><Icon name="plus" />باقة جديدة</button>}
      </div>
      {plans.error && <ErrorBox error={plans.error} />}
      {plans.isLoading && <Loading />}
      {plans.data?.length === 0 && <Empty>لا توجد باقات بعد.</Empty>}
      <div className="cards">
        {plans.data?.map((p) => (
          <button key={p.id} className="card" onClick={() => can('edit') && setEditing(p)} disabled={!can('edit')} style={{ opacity: 1, cursor: can('edit') ? 'pointer' : 'default' }}>
            <div className="li" style={{ padding: 0, border: 0 }}>
              <h3>{p.name}</h3>
              <Tag label={p.active ? { text: 'متاحة', tone: 'ok' } : { text: 'موقوفة', tone: 'mu' }} />
            </div>
            <span><Num>{money(p.price)}</Num> ر.س / {PERIOD[p.billingPeriod]}</span>
            <span className="mu">{p.siteName} · {p.wholeSite ? 'كل الموقع' : p.accessPointNames.join('، ')}</span>
            <span className="mu">
              {p.allowedDays.length ? p.allowedDays.map((d) => DAY[d]).join('، ') : 'كل الأيام'}
              {p.allowedFrom && <> · <Num>{hhmm(p.allowedFrom)}–{hhmm(p.allowedTo)}</Num></>}
              {p.requiresApproval ? ' · تحتاج موافقة' : ' · تفعيل فوري'}
            </span>
          </button>
        ))}
      </div>

      <Drawer open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'باقة جديدة' : 'تعديل الباقة'}>
        {editing !== null && <PlanForm plan={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Drawer>
    </>
  )
}

function PlanForm({ plan, onDone }: { plan: Plan | null; onDone: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [siteId, setSiteId] = useState<number | null>(plan?.siteId ?? null)
  const [name, setName] = useState(plan?.name ?? '')
  const [description, setDescription] = useState(plan?.description ?? '')
  const [permitType, setPermitType] = useState<PermitType>(plan?.permitType ?? 'CLUB')
  const [period, setPeriod] = useState<BillingPeriod>(plan?.billingPeriod ?? 'MONTHLY')
  const [price, setPrice] = useState(plan ? String(plan.price) : '')
  const [approval, setApproval] = useState(plan?.requiresApproval ?? true)
  const [active, setActive] = useState(plan?.active ?? true)
  const [aps, setAps] = useState<number[]>(plan?.accessPointIds ?? [])
  const [days, setDays] = useState<DayOfWeek[]>(plan?.allowedDays ?? [])
  const [from, setFrom] = useState(hhmm(plan?.allowedFrom))
  const [to, setTo] = useState(hhmm(plan?.allowedTo))
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const body = {
      siteId: siteId!, name: name.trim(), description: description.trim() || undefined, permitType,
      billingPeriod: period, price: Number(price), requiresApproval: approval, accessPointIds: aps,
      allowedDays: days, allowedFrom: from || null, allowedTo: to || null, active,
    }
    try {
      if (plan) await endpoints.updatePlan(plan.id, body)
      else await endpoints.createPlan(body)
      qc.invalidateQueries({ queryKey: ['plans'] })
      toast(plan ? 'حُفظت الباقة' : 'أُضيفت الباقة وأصبحت تظهر للعملاء')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الحفظ')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>اسم الباقة</span><input className="input" required maxLength={150} placeholder="عضوية النادي الشهرية" value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label className="field"><span>الوصف (يظهر للعميل)</span><textarea className="input" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
      <label className="field"><span>الموقع</span><SiteSelect value={siteId} onChange={(v) => { setSiteId(v); setAps([]) }} /></label>
      <div className="field"><span>نقاط الدخول (بدون اختيار = كل الموقع)</span><AccessPointChecks siteId={siteId} value={aps} onChange={setAps} /></div>
      <div className="row2">
        <label className="field"><span>نوع التصريح</span>
          <select className="input" value={permitType} onChange={(e) => setPermitType(e.target.value as PermitType)}>
            {keys(PERMIT_TYPE).map((t) => <option key={t} value={t}>{PERMIT_TYPE[t]}</option>)}
          </select>
        </label>
        <label className="field"><span>مدة الاشتراك</span>
          <select className="input" value={period} onChange={(e) => setPeriod(e.target.value as BillingPeriod)}>
            {keys(PERIOD).map((p) => <option key={p} value={p}>{PERIOD[p]}</option>)}
          </select>
        </label>
      </div>
      <label className="field"><span>السعر (ر.س)</span>
        <input className="input" type="number" dir="ltr" required min={0} max={100000} step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
      </label>
      <div className="field"><span>الأيام (بدون اختيار = كل الأيام)</span><DayChecks value={days} onChange={setDays} /></div>
      <div className="row2">
        <label className="field"><span>من الساعة (اختياري)</span><input className="input" type="time" dir="ltr" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="field"><span>إلى الساعة</span><input className="input" type="time" dir="ltr" value={to} onChange={(e) => setTo(e.target.value)} /></label>
      </div>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input type="checkbox" checked={approval} onChange={(e) => setApproval(e.target.checked)} /> كل اشتراك يحتاج موافقة فريقك
      </label>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> متاحة للاشتراك الآن
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn" disabled={siteId === null}>حفظ</button></div>
    </form>
  )
}
