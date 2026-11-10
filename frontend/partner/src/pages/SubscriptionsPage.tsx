import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { RequestStatus, ServiceRequest } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { ago, date, dateTime, money } from '../lib/format'
import { PERIOD, REQUEST_STATUS, REQUEST_TYPE, SUB_STATUS } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

const FILTERS: [RequestStatus | '', string][] = [['PENDING', 'بانتظار القرار'], ['APPROVED', 'المقبولة'], ['REJECTED', 'المرفوضة'], ['', 'الكل']]

/** "طلبات الاشتراك": customers asking to subscribe to a plan (or renew), and the running subscriptions. */
export function SubscriptionsPage() {
  const [tab, setTab] = useState<'requests' | 'subs'>('requests')
  return (
    <>
      <p className="lead">
        يطلب العميل الاشتراك في إحدى باقاتك من تطبيقه، ويُحجز المبلغ من محفظته. عند القبول يصدر له التصريح تلقائيًا،
        وعند الرفض يُعاد المبلغ لمحفظته.
      </p>
      <div className="tabs">
        <button className={tab === 'requests' ? 'on' : ''} onClick={() => setTab('requests')}>الطلبات</button>
        <button className={tab === 'subs' ? 'on' : ''} onClick={() => setTab('subs')}>الاشتراكات</button>
      </div>
      {tab === 'requests' ? <Requests /> : <Subscriptions />}
    </>
  )
}

function Requests() {
  const [status, setStatus] = useState<RequestStatus | ''>('PENDING')
  const [page, setPage] = useState(0)
  const params = { status: status || undefined, page }
  const list = useQuery({ queryKey: ['requests', params], queryFn: () => endpoints.requests(params), refetchInterval: 30_000 })

  return (
    <>
      <div className="chips" style={{ marginBottom: 14 }}>
        {FILTERS.map(([v, t]) => (
          <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => { setStatus(v); setPage(0) }}>{t}</button>
        ))}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading && <Loading />}
      {list.data?.content.length === 0 && <Empty>لا توجد طلبات هنا.</Empty>}
      <div className="panel" hidden={!list.data?.content.length}>
        {list.data?.content.map((r) => <RequestRow key={r.id} r={r} />)}
      </div>
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}
    </>
  )
}

function RequestRow({ r }: { r: ServiceRequest }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const done = (msg: string) => {
    qc.invalidateQueries({ queryKey: ['requests'] })
    qc.invalidateQueries({ queryKey: ['subscriptions'] })
    qc.invalidateQueries({ queryKey: ['permits'] })
    toast(msg)
  }
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const approve = useMutation({ mutationFn: () => endpoints.approveRequest(r.id), onSuccess: () => done('قُبل الطلب وصدر التصريح للعميل'), onError })
  const reject = useMutation({ mutationFn: () => endpoints.rejectRequest(r.id, reason.trim()), onSuccess: () => done('رُفض الطلب وأُعيد المبلغ للعميل'), onError })

  return (
    <div className="li" style={{ flexWrap: 'wrap' }}>
      <span className="ent">
        <b>{r.customerName} <span className="mu num">{r.customerVornxId}</span></b>
        <small>
          {r.planName} · {REQUEST_TYPE[r.type]} · <Num>{money(r.amount)}</Num> ر.س · {ago(r.createdAt)}
          {r.rejectionReason && <> · السبب: {r.rejectionReason}</>}
        </small>
      </span>
      {r.status === 'PENDING' && can('operate') ? (
        <span style={{ display: 'flex', gap: 6 }}>
          <button className="btn sm" disabled={approve.isPending} onClick={() => approve.mutate()}>قبول</button>
          <button className="btn gh sm" onClick={() => setRejecting(!rejecting)}>رفض</button>
        </span>
      ) : <Tag label={REQUEST_STATUS[r.status]} />}
      {rejecting && (
        <form className="form" style={{ width: '100%' }} onSubmit={(e) => { e.preventDefault(); reject.mutate() }}>
          <input className="input" required maxLength={500} placeholder="سبب الرفض (يظهر للعميل)" value={reason} onChange={(e) => setReason(e.target.value)} />
          <div className="form-actions"><button className="btn danger sm" disabled={!reason.trim() || reject.isPending}>تأكيد الرفض</button></div>
        </form>
      )}
    </div>
  )
}

function Subscriptions() {
  const [page, setPage] = useState(0)
  const list = useQuery({ queryKey: ['subscriptions', page], queryFn: () => endpoints.subscriptions({ page }) })
  return (
    <>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>العميل</th><th>الباقة</th><th>الفترة</th><th>من</th><th>إلى</th><th>الحالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((s) => (
                <tr key={s.id}>
                  <td className="ent"><b>{s.customerName}</b><small><Num>{s.number}</Num></small></td>
                  <td className="ent"><b>{s.planName}</b><small>{s.siteName}</small></td>
                  <td>{PERIOD[s.billingPeriod]} · <Num>{money(s.renewalPrice)}</Num></td>
                  <td>{date(s.startAt)}</td>
                  <td title={dateTime(s.endAt)}>{date(s.endAt)}</td>
                  <td><Tag label={SUB_STATUS[s.status]} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد اشتراكات بعد.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}
    </>
  )
}
