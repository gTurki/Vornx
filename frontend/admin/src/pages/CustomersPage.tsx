import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AccountStatus, CustomerDetails } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ago, date, dateTime, money } from '../lib/format'
import { ACCOUNT_STATUS, type Label } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Pager, Tag, useDebounced } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../ui/Toast'

const FILTERS: [AccountStatus | '', string][] = [['', 'الكل'], ['ACTIVE', 'نشط'], ['SUSPENDED', 'معلّق']]
const TIER: Record<string, string> = { BRONZE: 'برونزي', SILVER: 'فضي', GOLD: 'ذهبي', PLATINUM: 'بلاتيني' }
const PERMIT_TYPE: Record<string, string> = {
  HOTEL_KEY: 'مفتاح فندق', WORKPLACE: 'جهة عمل', CLUB: 'نادٍ', PARKING: 'موقف', TRANSIT: 'نقل', EVENT_PASS: 'تصريح فعالية', HOME: 'سكن',
}
const STATUS: Record<string, Label> = {
  ACTIVE: { text: 'نشط', tone: 'ok' }, PENDING: { text: 'بانتظار', tone: 'soon' }, UNDER_REVIEW: { text: 'قيد المراجعة', tone: 'soon' },
  CONFIRMED: { text: 'مؤكد', tone: 'ok' }, CHECKED_IN: { text: 'حضر', tone: 'ok' }, APPROVED: { text: 'مقبول', tone: 'ok' },
  EXPIRED: { text: 'منتهي', tone: 'mu' }, CANCELLED: { text: 'ملغى', tone: 'mu' }, REJECTED: { text: 'مرفوض', tone: 'bad' },
  SUSPENDED: { text: 'معلّق', tone: 'bad' }, REVOKED: { text: 'ملغى', tone: 'bad' }, BLOCKED: { text: 'محظورة', tone: 'bad' },
  LOST: { text: 'مفقودة', tone: 'bad' },
}
const st = (s: string): Label => STATUS[s] ?? { text: s, tone: 'mu' }
const DENY: Record<string, string> = {
  CARD_INVALID: 'بطاقة غير صالحة', ACCOUNT_INACTIVE: 'الحساب معلّق', NO_PERMIT: 'لا يوجد تصريح', PERMIT_INACTIVE: 'التصريح موقوف',
  PERMIT_NOT_YET_VALID: 'التصريح لم يبدأ', PERMIT_EXPIRED: 'التصريح منتهٍ', DAY_NOT_ALLOWED: 'يوم غير مسموح', OUTSIDE_HOURS: 'خارج الأوقات',
  METHOD_NOT_SUPPORTED: 'طريقة غير مدعومة', ACCESS_POINT_UNAVAILABLE: 'الباب خارج الخدمة',
}

/** "العملاء": everyone with a customer account. Search, then open one to see their card, wallet, keys and activity. */
export function CustomersPage() {
  const [url] = useSearchParams()
  const [q, setQ] = useState(url.get('q') ?? '')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<string | null>(null)
  const query = useDebounced(q)
  useEffect(() => setPage(0), [query, status])

  const params = { q: query || undefined, status: status || undefined, page }
  const list = useQuery({ queryKey: ['customers', params], queryFn: () => endpoints.customers(params) })

  return (
    <>
      <p className="lead">كل من سجّل في تطبيق العملاء. ابحث بالاسم أو البريد أو الجوال أو معرّف VORNX، وافتح العميل لترى بطاقته ومحفظته وتصاريحه ونشاطه.</p>
      <div className="toolbar" style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14, alignItems: 'center' }}>
        <input className="input" style={{ flex: '1 1 260px', maxWidth: 380 }} placeholder="بحث: الاسم، البريد، 05XXXXXXXX، VX…" value={q}
               onChange={(e) => setQ(e.target.value)} />
        <div className="chips">
          {FILTERS.map(([v, t]) => (
            <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => setStatus(v)}>{t}</button>
          ))}
        </div>
        {list.data && <span className="mu" style={{ fontSize: 13 }}><Num>{list.data.totalElements}</Num> عميل</span>}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>العميل</th><th>معرّف VORNX</th><th>الجوال</th><th>التحقق</th><th>المستوى</th><th>سجّل</th><th>الحالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((c) => (
                <tr key={c.id} className="click" onClick={() => setOpen(c.vornxId)}>
                  <td className="ent"><b>{c.firstName} {c.lastName}</b><small><Num>{c.email}</Num></small></td>
                  <td><Num>{c.vornxId}</Num></td>
                  <td><Num>{c.phone}</Num></td>
                  <td>
                    <span className={c.emailVerified ? 'ok' : 'mu'} title="البريد">✉ {c.emailVerified ? '✓' : '—'}</span>{'  '}
                    <span className={c.phoneVerified ? 'ok' : 'mu'} title="الجوال">☎ {c.phoneVerified ? '✓' : '—'}</span>
                  </td>
                  <td>{TIER[c.tier] ?? c.tier} <span className="mu">· <Num>{c.points}</Num></span></td>
                  <td>{ago(c.createdAt)}</td>
                  <td><Tag label={ACCOUNT_STATUS[c.status]} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>{query ? 'لا يوجد عميل بهذا البحث.' : 'لا يوجد عملاء بعد.'}</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={open !== null} onClose={() => setOpen(null)} title="العميل">
        {open && <CustomerDrawer vornxId={open} />}
      </Drawer>
    </>
  )
}

function CustomerDrawer({ vornxId }: { vornxId: string }) {
  const q = useQuery({ queryKey: ['customer', vornxId], queryFn: () => endpoints.customer(vornxId) })
  if (q.isLoading) return <Loading />
  if (q.error || !q.data) return <ErrorBox error={q.error} />
  return <Details d={q.data} />
}

function Details({ d }: { d: CustomerDetails }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const c = d.customer
  const canAct = me?.role === 'SUPER_ADMIN' || me?.role === 'SUPPORT_AGENT'
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['customer', c.vornxId] })
    qc.invalidateQueries({ queryKey: ['customers'] })
  }
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const setStatus = useMutation({
    mutationFn: (s: AccountStatus) => endpoints.setCustomerStatus(c.vornxId, s),
    onSuccess: (r) => { refresh(); toast(r.status === 'SUSPENDED' ? 'عُلّق الحساب وخرج العميل من جميع الأجهزة' : 'أُعيد تفعيل الحساب') },
    onError,
  })
  const issue = useMutation({
    mutationFn: () => endpoints.issueCard(c.vornxId),
    onSuccess: () => { refresh(); toast('أُصدرت البطاقة') },
    onError,
  })
  const activeCard = d.cards.find((x) => x.status === 'ACTIVE')

  return (
    <>
      <div className="li"><span><b>{c.firstName} {c.lastName}</b><div className="mu"><Num>{c.vornxId}</Num></div></span><Tag label={ACCOUNT_STATUS[c.status]} /></div>
      <Kv k="البريد"><a href={`mailto:${c.email}`}><Num>{c.email}</Num></a> {c.emailVerified ? <span className="ok">✓</span> : <span className="mu">(غير مؤكد)</span>}</Kv>
      <Kv k="الجوال"><a href={`tel:${c.phone}`}><Num>{c.phone}</Num></a> {c.phoneVerified ? <span className="ok">✓</span> : <span className="mu">(غير مؤكد)</span>}</Kv>
      <Kv k="المستوى">{TIER[c.tier] ?? c.tier} · <Num>{c.points}</Num> نقطة</Kv>
      <Kv k="الأمان">{[d.totpEnabled && 'تطبيق المصادقة', d.passkeys > 0 && `Face ID / البصمة (${d.passkeys})`].filter(Boolean).join(' · ') || 'رمز البريد'}</Kv>
      <Kv k="سجّل">{dateTime(c.createdAt)}{d.termsAcceptedAt ? ' · وافق على الشروط' : ''}</Kv>

      <h2 className="section">البطاقة والمحفظة</h2>
      {activeCard
        ? <Kv k="البطاقة"><Num>•••• {activeCard.last4}</Num> · حتى {date(activeCard.expiresAt)} <Tag label={st(activeCard.status)} /></Kv>
        : <div className="li"><span className="mu">لا توجد بطاقة نشطة.</span>
            {canAct && c.status === 'ACTIVE' && <button className="btn sm" disabled={issue.isPending} onClick={() => issue.mutate()}>إصدار بطاقة</button>}</div>}
      <Kv k="الرصيد"><b><Num>{money(d.wallet.balance)}</Num></b> ر.س</Kv>
      {d.recentTransactions.slice(0, 5).map((t) => (
        <div className="li" key={t.reference}>
          <span><b>{t.description ?? t.partnerName ?? t.counterpartyName ?? t.type}</b><small className="mu">{dateTime(t.createdAt)}</small></span>
          <Num><span className={t.direction === 'IN' ? 'ok' : ''}>{t.direction === 'IN' ? '+' : '−'}{money(t.amount)}</span></Num>
        </div>
      ))}

      <h2 className="section">التصاريح والمفاتيح ({d.permits.length})</h2>
      {d.permits.length === 0 && <Empty>لا توجد تصاريح.</Empty>}
      {d.permits.map((p) => (
        <div className="li" key={p.id}>
          <span><b>{PERMIT_TYPE[p.type] ?? p.type} · {p.siteName}</b>
            <small className="mu">{p.partnerName} · {date(p.validFrom)} → {date(p.validUntil)}{p.wholeSite ? ' · كل الموقع' : p.accessPoints.length ? ` · ${p.accessPoints.map((a) => a.name).join('، ')}` : ''}</small></span>
          <Tag label={st(p.status)} />
        </div>
      ))}

      <h2 className="section">الاشتراكات ({d.subscriptions.length})</h2>
      {d.subscriptions.length === 0 && <Empty>لا توجد اشتراكات.</Empty>}
      {d.subscriptions.map((s) => (
        <div className="li" key={s.id}>
          <span><b>{s.planName}</b><small className="mu">{s.partnerName} · <Num>{money(s.renewalPrice)}</Num> ر.س{s.endAt ? ` · حتى ${date(s.endAt)}` : ''}</small></span>
          <Tag label={st(s.status)} />
        </div>
      ))}

      <h2 className="section">تذاكر الفعاليات ({d.tickets.length})</h2>
      {d.tickets.length === 0 && <Empty>لا توجد تذاكر.</Empty>}
      {d.tickets.map((t) => (
        <div className="li" key={t.id}>
          <span><b>{t.eventTitle}</b><small className="mu">{t.venue} · {dateTime(t.startsAt)}</small></span>
          <Tag label={st(t.status)} />
        </div>
      ))}

      <h2 className="section">آخر الدخول</h2>
      {d.recentAccess.length === 0 && <Empty>لا يوجد نشاط.</Empty>}
      {d.recentAccess.map((a) => (
        <div className="li" key={a.decisionNumber}>
          <span><b>{a.accessPointName}</b><small className="mu">{a.siteName} · {dateTime(a.occurredAt)}</small></span>
          {a.decision === 'ALLOW' ? <span className="tag ok">سُمح</span> : <span className="tag bad" title={a.denyReason ?? ''}>رُفض{a.denyReason ? ` · ${DENY[a.denyReason] ?? a.denyReason}` : ''}</span>}
        </div>
      ))}

      {canAct && (
        <>
          <h2 className="section">الحساب</h2>
          <p className="mu" style={{ fontSize: 13 }}>
            التعليق يمنع الدخول للتطبيق ويخرج العميل من جميع الأجهزة، وترفض الأبواب بطاقته. يبقى رصيد المحفظة كما هو.
          </p>
          <div className="form-actions" style={{ justifyContent: 'flex-start' }}>
            {c.status === 'SUSPENDED'
              ? <button className="btn" disabled={setStatus.isPending} onClick={() => setStatus.mutate('ACTIVE')}>إعادة التفعيل</button>
              : <ConfirmButton disabled={setStatus.isPending} onConfirm={() => setStatus.mutate('SUSPENDED')}>تعليق الحساب</ConfirmButton>}
          </div>
        </>
      )}
    </>
  )
}
