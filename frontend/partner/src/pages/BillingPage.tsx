import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useCan } from '../auth/AuthContext'
import { count, date, days, money } from '../lib/format'
import { CONTRACT_STATUS, INVOICE_STATUS, TIER } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

/**
 * "العقد والفواتير", laid out like hotel.html: the 3 contract numbers, the plan with how much of the contract has
 * passed (and "اطلب تجديد العقد"), then the monthly invoices.
 */
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
  const monthsLeft = c ? Math.max(0, Math.round(c.daysLeft / 30)) : 0

  return (
    <>
      {contract.isLoading && <Loading />}
      {contract.error && <div className="panel" style={{ marginBottom: 22 }}><Empty>لا يوجد عقد ساري بعد. سيضيفه مدير حسابك.</Empty></div>}
      {c && (
        <>
          <div className="kpis" style={{ marginTop: 0 }}>
            <div className="kp"><span className="num">{count(Math.round(c.monthlyFee))}</span><span>القيمة الشهرية، ر.س</span></div>
            <div className="kp">
              <span className="num">{c.maxBeneficiaries ? count(c.maxBeneficiaries) : '∞'}</span><span>الحد الأقصى للمستفيدين</span>
            </div>
            <div className={`kp${c.status !== 'ACTIVE' ? ' warn' : ''}`}>
              <span className="num">{c.endDate}</span>
              <span>انتهاء العقد · {c.daysLeft < 0 ? `انتهى منذ ${days(-c.daysLeft)}` : monthsLeft >= 1 ? `يتبقى ${monthsLeft} ${monthsLeft === 1 ? 'شهر' : 'أشهر'}` : `يتبقى ${days(c.daysLeft)}`}</span>
            </div>
          </div>

          <div className="panel" style={{ marginBottom: 22 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
              <b>{c.planName} <span className="mu">· {TIER[c.tier]} · <Num>{c.number}</Num></span></b>
              <Tag label={CONTRACT_STATUS[c.status]} />
            </div>
            <div className="bar"><i style={{ width: `${c.progressPercent}%` }} /></div>
            <small className="mu">
              مضى <Num>{c.progressPercent}%</Num> من مدة العقد ({date(c.startDate)} — {date(c.endDate)}).
              {c.status === 'EXPIRING_SOON' && ' نوصي بطلب التجديد الآن.'}
            </small>
            {can('admin') && c.status !== 'TERMINATED' && (
              <div style={{ marginTop: 12 }}>
                <ConfirmButton className="btn" onConfirm={() => renew.mutate()} disabled={renew.isPending}>اطلب تجديد العقد</ConfirmButton>
              </div>
            )}
          </div>
        </>
      )}

      <h2>الفواتير</h2>
      {invoices.error && <ErrorBox error={invoices.error} />}
      {invoices.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الفترة</th><th>الفاتورة</th><th>المبلغ</th><th>الاستحقاق</th><th>الحالة</th></tr></thead>
            <tbody>
              {invoices.data?.map((i) => (
                <tr key={i.id}>
                  <td>{period(i.period)}</td>
                  <td><Num>{i.number}</Num></td>
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
    </>
  )
}

const MONTH = new Intl.DateTimeFormat('ar-SA-u-ca-gregory-nu-latn', { month: 'long', year: 'numeric', timeZone: 'UTC' })
/** "2026-09" -> "سبتمبر 2026" */
const period = (p: string) => MONTH.format(new Date(p + '-15T12:00:00Z'))
