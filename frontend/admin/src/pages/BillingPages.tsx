import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { InvoiceStatus } from '../api/types'
import { canManage, useAuth } from '../auth/AuthContext'
import { date, money, thisMonth } from '../lib/format'
import { CONTRACT_STATUS, INVOICE_STATUS, TIER } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

function useInvalidateBilling() {
  const qc = useQueryClient()
  return () => ['contracts', 'invoices', 'summary', 'partner'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }))
}

/** "العقود والتجديدات" - sorted by nearest end date. */
export function ContractsPage() {
  const { me } = useAuth()
  const toast = useToast()
  const refresh = useInvalidateBilling()
  const [attention, setAttention] = useState(false)
  const list = useQuery({ queryKey: ['contracts', attention], queryFn: () => endpoints.contracts(attention) })
  const onError = (e: Error) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)

  const renew = useMutation({
    mutationFn: (id: number) => endpoints.renewContract(id, 12),
    onSuccess: (c) => { refresh(); toast(`جُدّد العقد حتى ${date(c.endDate)}`) },
    onError,
  })
  const terminate = useMutation({
    mutationFn: endpoints.terminateContract,
    onSuccess: () => { refresh(); toast('أُلغي العقد') },
    onError,
  })

  return (
    <>
      <p className="lead">العقود مرتبة حسب أقرب تاريخ انتهاء. جدّد العقد سنة كاملة بضغطة.</p>
      <div className="chips" style={{ marginBottom: 14 }}>
        <button className={`chip${!attention ? ' on' : ''}`} onClick={() => setAttention(false)}>كل العقود السارية</button>
        <button className={`chip${attention ? ' on' : ''}`} onClick={() => setAttention(true)}>تحتاج متابعة</button>
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الجهة</th><th>الباقة</th><th>شهريًا</th><th>النهاية</th><th>المدة</th><th>الحالة</th>{canManage(me) && <th />}</tr></thead>
            <tbody>
              {list.data?.map((c) => (
                <tr key={c.id}>
                  <td className="ent"><b>{c.partnerName}</b><small><Num>{c.number}</Num></small></td>
                  <td>{c.planName}<div className="mu">{TIER[c.tier]} · جُدّد {c.renewalCount} مرة</div></td>
                  <td><Num>{money(c.monthlyFee)}</Num></td>
                  <td>{date(c.endDate)}<div className="mu">{c.daysLeft >= 0 ? `يتبقى ${c.daysLeft} يومًا` : `منذ ${-c.daysLeft} يومًا`}</div></td>
                  <td style={{ minWidth: 110 }}><div className="bar"><i style={{ width: `${c.progressPercent}%` }} /></div><small className="mu">{c.progressPercent}%</small></td>
                  <td><Tag label={CONTRACT_STATUS[c.status]} /></td>
                  {canManage(me) && (
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <button className="btn sm" disabled={renew.isPending} onClick={() => renew.mutate(c.id)}>تجديد سنة</button>{' '}
                      {me?.role === 'SUPER_ADMIN' && <ConfirmButton className="btn gh sm" onConfirm={() => terminate.mutate(c.id)}>إلغاء</ConfirmButton>}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.length === 0 && <Empty>{attention ? 'لا توجد عقود تحتاج متابعة.' : 'لا توجد عقود بعد. أنشئ عقدًا من ملف الجهة.'}</Empty>}
        </div>
      )}
    </>
  )
}

const INVOICE_FILTERS: [InvoiceStatus | '', string][] = [['', 'الكل'], ['DUE', 'مستحقة'], ['OVERDUE', 'متأخرة'], ['PAID', 'مدفوعة']]

/** "الفواتير" */
export function InvoicesPage() {
  const { me } = useAuth()
  const toast = useToast()
  const refresh = useInvalidateBilling()
  const [status, setStatus] = useState<InvoiceStatus | ''>('')
  const [page, setPage] = useState(0)
  const [period, setPeriod] = useState(thisMonth())
  const params = { status: status || undefined, page }
  const list = useQuery({ queryKey: ['invoices', params], queryFn: () => endpoints.invoices(params) })
  const onError = (e: Error) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)

  const generate = useMutation({
    mutationFn: () => endpoints.generateInvoices(period),
    onSuccess: (created) => {
      refresh()
      toast(created.length ? `أُصدرت ${created.length} فاتورة وأُرسلت للجهات بالبريد` : 'كل العقود لديها فاتورة لهذا الشهر')
    },
    onError,
  })
  const pay = useMutation({
    mutationFn: endpoints.markInvoicePaid,
    onSuccess: () => { refresh(); toast('سُجّلت الفاتورة كمدفوعة') },
    onError,
  })

  return (
    <>
      <div className="toolbar">
        <div className="chips grow">
          {INVOICE_FILTERS.map(([v, t]) => (
            <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => { setStatus(v); setPage(0) }}>{t}</button>
          ))}
        </div>
        {canManage(me) && (
          <>
            <input className="input" type="month" style={{ width: 'auto' }} value={period} onChange={(e) => setPeriod(e.target.value)} />
            <button className="btn" disabled={generate.isPending} onClick={() => generate.mutate()}>إصدار فواتير الشهر</button>
          </>
        )}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>رقم الفاتورة</th><th>الجهة</th><th>الفترة</th><th>المبلغ</th><th>الاستحقاق</th><th>الحالة</th>{canManage(me) && <th />}</tr></thead>
            <tbody>
              {list.data?.content.map((i) => (
                <tr key={i.id}>
                  <td><Num>{i.number}</Num></td>
                  <td>{i.partnerName}</td>
                  <td><Num>{i.period}</Num></td>
                  <td><Num>{money(i.amount)}</Num> ر.س</td>
                  <td>{date(i.dueDate)}</td>
                  <td><Tag label={INVOICE_STATUS[i.status]} />{i.paidAt && <div className="mu">{date(i.paidAt)}</div>}</td>
                  {canManage(me) && (
                    <td>{i.status !== 'PAID' && <ConfirmButton className="btn gh sm" onConfirm={() => pay.mutate(i.id)}>تسجيل الدفع</ConfirmButton>}</td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد فواتير.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}
    </>
  )
}
