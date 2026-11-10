import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { Plan } from '../api/types'
import { date, hhmm, money } from '../lib/format'
import { DAY, PERIOD, PERMIT_ICON, REQUEST_STATUS, REQUEST_TYPE, SUB_STATUS } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { Icon } from '../ui/Icon'
import { useToast } from '../ui/Toast'

/** "اشتراكاتي": my subscriptions and requests, and the plans I can subscribe to. */
export function SubscriptionsPage() {
  const [tab, setTab] = useState<'mine' | 'plans'>('mine')
  return (
    <>
      <div className="tabs">
        <button className={tab === 'mine' ? 'on' : ''} onClick={() => setTab('mine')}>اشتراكاتي</button>
        <button className={tab === 'plans' ? 'on' : ''} onClick={() => setTab('plans')}>الباقات المتاحة</button>
      </div>
      {tab === 'mine' ? <Mine onBrowse={() => setTab('plans')} /> : <Plans />}
    </>
  )
}

function useRefreshAll() {
  const qc = useQueryClient()
  return () => ['subscriptions', 'requests', 'wallet', 'transactions', 'permits'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }))
}

function Mine({ onBrowse }: { onBrowse: () => void }) {
  const toast = useToast()
  const refresh = useRefreshAll()
  const subs = useQuery({ queryKey: ['subscriptions'], queryFn: endpoints.subscriptions })
  const requests = useQuery({ queryKey: ['requests'], queryFn: endpoints.requests })
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الطلب', true)
  const renew = useMutation({
    mutationFn: endpoints.renew,
    onSuccess: (r) => { refresh(); toast(r.status === 'APPROVED' ? 'جُدّد اشتراكك' : 'أُرسل طلب التجديد وحُجز المبلغ من محفظتك') },
    onError,
  })
  const cancel = useMutation({
    mutationFn: endpoints.cancelRequest,
    onSuccess: () => { refresh(); toast('أُلغي الطلب وأُعيد المبلغ لمحفظتك') },
    onError,
  })
  const pending = (requests.data ?? []).filter((r) => r.status === 'PENDING')
  const history = (requests.data ?? []).filter((r) => r.status !== 'PENDING').slice(0, 10)

  return (
    <>
      {subs.error && <ErrorBox error={subs.error} />}
      {(subs.isLoading || requests.isLoading) && <Loading />}

      {pending.length > 0 && (
        <>
          <h2>قيد المراجعة</h2>
          <div className="panel" style={{ marginBottom: 20 }}>
            {pending.map((r) => (
              <div key={r.id} className="li">
                <span className="ent"><b>{r.planName}</b><small>{REQUEST_TYPE[r.type]} · <Num>{money(r.amount)}</Num> ر.س محجوزة حتى يقرر مقدم الخدمة</small></span>
                <ConfirmButton className="btn gh sm" onConfirm={() => cancel.mutate(r.id)}>إلغاء الطلب</ConfirmButton>
              </div>
            ))}
          </div>
        </>
      )}

      {subs.data?.length === 0 && pending.length === 0 && (
        <div className="notice">
          <b>لا توجد اشتراكات بعد</b>
          <p className="mu" style={{ margin: '4px 0 10px' }}>اشترك في نادٍ أو موقف شهري، ويُدفع من محفظتك.</p>
          <button className="btn sm" onClick={onBrowse}>تصفّح الباقات</button>
        </div>
      )}
      <div className="cols" style={{ gap: 16 }}>
        {subs.data?.map((s) => (
          <div key={s.id} className="panel">
            <div className="li" style={{ paddingTop: 0 }}>
              <span className="ent"><b>{s.planName}</b><small>{s.partnerName} · {s.siteName}</small></span>
              <Tag label={SUB_STATUS[s.status]} />
            </div>
            {s.startAt && <div className="kv"><span>الفترة</span><b>{date(s.startAt)} – {date(s.endAt)}</b></div>}
            <div className="kv"><span>سعر التجديد</span><b><Num>{money(s.renewalPrice)}</Num> ر.س / {PERIOD[s.billingPeriod]}</b></div>
            {s.permitNumber && <div className="kv"><span>التصريح</span><b><Num>{s.permitNumber}</Num></b></div>}
            {(s.status === 'ACTIVE' || s.status === 'EXPIRED') && (
              <div className="form-actions" style={{ marginTop: 10 }}>
                <ConfirmButton className="btn soft sm" onConfirm={() => renew.mutate(s.id)} disabled={renew.isPending}>
                  تجديد بـ {money(s.renewalPrice)} ر.س
                </ConfirmButton>
              </div>
            )}
          </div>
        ))}
      </div>

      {history.length > 0 && (
        <>
          <h2 className="section">طلبات سابقة</h2>
          <div className="panel">
            {history.map((r) => (
              <div key={r.id} className="li">
                <span className="ent">
                  <b>{r.planName}</b>
                  <small>{REQUEST_TYPE[r.type]} · {date(r.createdAt)}{r.rejectionReason && <> · السبب: {r.rejectionReason}</>}</small>
                </span>
                <Tag label={REQUEST_STATUS[r.status]} />
              </div>
            ))}
          </div>
        </>
      )}
    </>
  )
}

function Plans() {
  const [page, setPage] = useState(0)
  const plans = useQuery({ queryKey: ['plans', page], queryFn: () => endpoints.plans(page) })
  const wallet = useQuery({ queryKey: ['wallet'], queryFn: endpoints.wallet })
  return (
    <>
      <p className="lead">رصيدك: <Num>{money(wallet.data?.balance)}</Num> ر.س. يُخصم السعر عند الاشتراك، ويُعاد كاملًا إن رُفض الطلب.</p>
      {plans.error && <ErrorBox error={plans.error} />}
      {plans.isLoading && <Loading />}
      {plans.data?.content.length === 0 && <Empty>لا توجد باقات متاحة حاليًا.</Empty>}
      <div className="cols" style={{ gap: 16 }}>
        {plans.data?.content.map((p) => <PlanCard key={p.id} p={p} />)}
      </div>
      {plans.data && <Pager page={plans.data.page} totalPages={plans.data.totalPages} onChange={setPage} />}
    </>
  )
}

function PlanCard({ p }: { p: Plan }) {
  const toast = useToast()
  const refresh = useRefreshAll()
  const subscribe = useMutation({
    mutationFn: () => endpoints.subscribe(p.id),
    onSuccess: (r) => { refresh(); toast(r.status === 'APPROVED' ? 'اشتركت! تصريحك جاهز في صفحة التصاريح.' : 'أُرسل طلبك. سنخبرك فور موافقة مقدم الخدمة.') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الاشتراك', true),
  })
  return (
    <div className="panel">
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 6 }}>
        <span className="tile" style={{ padding: 0, border: 0, background: 'none' }}><span className="ic"><Icon name={PERMIT_ICON[p.permitType]} /></span></span>
        <span className="ent"><b>{p.name}</b><small>{p.partnerName} · {p.siteName}</small></span>
      </div>
      {p.description && <p style={{ margin: '0 0 8px' }}>{p.description}</p>}
      <div className="kv"><span>السعر</span><b><Num>{money(p.price)}</Num> ر.س / {PERIOD[p.billingPeriod]}</b></div>
      <div className="kv"><span>يشمل</span><b>{p.wholeSite ? 'كل الموقع' : p.accessPointNames.join('، ')}</b></div>
      {(p.allowedDays.length > 0 || p.allowedFrom) && (
        <div className="kv"><span>الأوقات</span><b>
          {p.allowedDays.length ? p.allowedDays.map((d) => DAY[d]).join('، ') : 'كل الأيام'}
          {p.allowedFrom && <> · <Num>{hhmm(p.allowedFrom)}–{hhmm(p.allowedTo)}</Num></>}
        </b></div>
      )}
      <div className="form-actions" style={{ marginTop: 12 }}>
        <ConfirmButton className="btn wide" onConfirm={() => subscribe.mutate()} disabled={subscribe.isPending}>
          اشترك بـ {money(p.price)} ر.س
        </ConfirmButton>
      </div>
      <p className="mu" style={{ margin: '6px 0 0' }}>{p.requiresApproval ? 'يحتاج موافقة مقدم الخدمة' : 'تفعيل فوري'}</p>
    </div>
  )
}
