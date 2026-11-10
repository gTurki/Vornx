import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useCan } from '../auth/AuthContext'
import { count, date, money } from '../lib/format'
import { CONTRACT_STATUS, INVOICE_STATUS, TIER } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

/** "العقد والفواتير": the partner's contract with VORNX, its monthly invoices, and asking to renew. */
export function BillingPage() {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const contract = useQuery({ queryKey: ['contract'], queryFn: endpoints.contract, retry: false })
  const invoices = useQuery({ queryKey: ['invoices'], queryFn: endpoints.invoices })
  const renew = useMutation({
    mutationFn: endpoints.requestRenewal,
    onSuccess: (t) => { qc.invalidateQueries({ queryKey: ['tickets'] }); toast(`أُرسل طلب التجديد لمدير حسابك (طلب ${t.number})`) },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الإرسال', true),
  })
  const c = contract.data
  const unpaid = invoices.data?.filter((i) => i.status !== 'PAID').reduce((s, i) => s + i.amount, 0) ?? 0

  return (
    <div className="cols">
      <section>
        <h2>الفواتير</h2>
        {invoices.error && <ErrorBox error={invoices.error} />}
        {invoices.isLoading ? <Loading /> : (
          <div className="tw">
            <table>
              <thead><tr><th>الفاتورة</th><th>الفترة</th><th>المبلغ</th><th>الاستحقاق</th><th>الحالة</th></tr></thead>
              <tbody>
                {invoices.data?.map((i) => (
                  <tr key={i.id}>
                    <td><Num>{i.number}</Num></td>
                    <td><Num>{i.period}</Num></td>
                    <td><Num>{money(i.amount)}</Num> {i.currency === 'SAR' ? 'ر.س' : i.currency}</td>
                    <td>{i.paidAt ? <>دُفعت {date(i.paidAt)}</> : date(i.dueDate)}</td>
                    <td><Tag label={INVOICE_STATUS[i.status]} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {invoices.data?.length === 0 && <Empty>لا توجد فواتير بعد.</Empty>}
          </div>
        )}
        {unpaid > 0 && <p className="mu">غير المدفوع: <Num>{money(unpaid)}</Num> ر.س. للسداد تواصل مع مدير حسابك.</p>}
      </section>

      <section>
        <h2>العقد</h2>
        <div className="panel">
          {contract.isLoading && <Loading />}
          {contract.error && <Empty>لا يوجد عقد ساري بعد.</Empty>}
          {c && (
            <>
              <div className="li"><b>{c.planName}</b><Tag label={CONTRACT_STATUS[c.status]} /></div>
              <Kv k="رقم العقد"><Num>{c.number}</Num></Kv>
              <Kv k="الفئة">{TIER[c.tier]}</Kv>
              <Kv k="الرسوم الشهرية"><Num>{money(c.monthlyFee)}</Num> ر.س</Kv>
              <Kv k="الحد الأقصى للمستفيدين">{c.maxBeneficiaries ? <Num>{count(c.maxBeneficiaries)}</Num> : 'غير محدود'}</Kv>
              <Kv k="من">{date(c.startDate)}</Kv>
              <Kv k="إلى">{date(c.endDate)}</Kv>
              <Kv k="المتبقي"><Num>{count(Math.max(c.daysLeft, 0))}</Num> يوم</Kv>
              <div className="bar"><i style={{ width: `${c.progressPercent}%` }} /></div>
              {can('admin') && c.status !== 'TERMINATED' && (
                <div className="form-actions" style={{ marginTop: 14 }}>
                  <ConfirmButton className="btn" onConfirm={() => renew.mutate()} disabled={renew.isPending}>طلب تجديد العقد</ConfirmButton>
                </div>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  )
}
