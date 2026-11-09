import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { ContractRequest, PartnerRequest, PartnerRole, PartnerStatus, PlanTier, Sector } from '../api/types'
import { canManage, useAuth } from '../auth/AuthContext'
import { date, dateTime, money } from '../lib/format'
import { ACCOUNT_STATUS, CONTRACT_STATUS, PARTNER_ROLE, PARTNER_STATUS, SECTORS, TIER } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Pager, Tag, useDebounced } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { useToast } from '../ui/Toast'

const STATUS_FILTERS: [PartnerStatus | '', string][] = [['', 'كل الحالات'], ['ACTIVE', 'نشطة'], ['ONBOARDING', 'قيد التهيئة'], ['SUSPENDED', 'معلّقة']]

export function PartnersPage() {
  const { me } = useAuth()
  const [sector, setSector] = useState<Sector | ''>('')
  const [status, setStatus] = useState<PartnerStatus | ''>('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const query = useDebounced(q)

  const params = { sector: sector || undefined, status: status || undefined, q: query || undefined, page }
  const list = useQuery({ queryKey: ['partners', params], queryFn: () => endpoints.partners(params) })

  const reset = (fn: () => void) => { fn(); setPage(0) }

  return (
    <>
      <p className="lead">الجهات التي تقدّم خدماتها للعميل النهائي عبر VORNX: من الدفع والدوام إلى الفنادق والمواقف.</p>

      <div className="toolbar">
        <input className="input grow" placeholder="ابحث باسم الجهة…" value={q} onChange={(e) => reset(() => setQ(e.target.value))} />
        <select className="input" style={{ width: 'auto' }} value={status} onChange={(e) => reset(() => setStatus(e.target.value as PartnerStatus | ''))}>
          {STATUS_FILTERS.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
        </select>
        {canManage(me) && <button className="btn" onClick={() => setCreating(true)}><Icon name="plus" />جهة جديدة</button>}
      </div>
      <div className="chips" style={{ marginBottom: 14 }}>
        <button className={`chip${sector === '' ? ' on' : ''}`} onClick={() => reset(() => setSector(''))}>كل الخدمات</button>
        {(Object.keys(SECTORS) as Sector[]).map((s) => (
          <button key={s} className={`chip${sector === s ? ' on' : ''}`} onClick={() => reset(() => setSector(s))}>{SECTORS[s]}</button>
        ))}
      </div>

      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الجهة</th><th>المدينة</th><th>الحالة</th><th>مدير الحساب</th><th>تاريخ الإضافة</th></tr></thead>
            <tbody>
              {list.data?.content.map((p) => (
                <tr key={p.id} className="click" onClick={() => setOpenId(p.id)}>
                  <td className="ent"><b>{p.name}</b><small>{SECTORS[p.sector]}</small></td>
                  <td>{p.city ?? '—'}</td>
                  <td><Tag label={PARTNER_STATUS[p.status]} /></td>
                  <td>{p.accountManagerName ?? '—'}</td>
                  <td>{date(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد جهات مطابقة. غيّر الفلتر أو كلمة البحث.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={creating} onClose={() => setCreating(false)} title="جهة جديدة">
        <NewPartnerForm onDone={(id) => { setCreating(false); setOpenId(id) }} />
      </Drawer>
      <Drawer open={openId !== null} onClose={() => setOpenId(null)} title="ملف الجهة">
        {openId !== null && <PartnerDetails id={openId} />}
      </Drawer>
    </>
  )
}

function NewPartnerForm({ onDone }: { onDone: (id: number) => void }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState<PartnerRequest>({ name: '', sector: 'HOTEL', city: 'الرياض', country: 'SA' })
  const [iManage, setIManage] = useState(true)
  const create = useMutation({
    mutationFn: () => endpoints.createPartner({ ...f, accountManagerId: iManage ? me?.id : null }),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['partners'] })
      toast('أُضيفت الجهة. فعّلها وادعُ أول موظف من ملفها.')
      onDone(p.id)
    },
  })
  const set = (k: keyof PartnerRequest) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  return (
    <form className="form" onSubmit={(e: FormEvent) => { e.preventDefault(); create.mutate() }}>
      <label className="field"><span>اسم الجهة</span><input className="input" required maxLength={150} value={f.name} onChange={set('name')} /></label>
      <label className="field"><span>القطاع</span>
        <select className="input" value={f.sector} onChange={set('sector')}>
          {(Object.keys(SECTORS) as Sector[]).map((s) => <option key={s} value={s}>{SECTORS[s]}</option>)}
        </select>
      </label>
      <div className="row2">
        <label className="field"><span>السجل التجاري (10 أرقام)</span><input className="input" dir="ltr" pattern="\d{10}" value={f.crNumber ?? ''} onChange={set('crNumber')} /></label>
        <label className="field"><span>الهاتف</span><input className="input" dir="ltr" value={f.phone ?? ''} onChange={set('phone')} /></label>
      </div>
      <div className="row2">
        <label className="field"><span>المدينة</span><input className="input" value={f.city ?? ''} onChange={set('city')} /></label>
        <label className="field"><span>الدولة</span><input className="input" value={f.country ?? ''} onChange={set('country')} /></label>
      </div>
      <label className="field"><span>ملاحظات</span><textarea className="input" maxLength={2000} value={f.notes ?? ''} onChange={set('notes')} /></label>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <input type="checkbox" checked={iManage} onChange={(e) => setIManage(e.target.checked)} /> أنا مدير الحساب لهذه الجهة
      </label>
      {create.error && <ErrorBox error={create.error} />}
      <div className="form-actions"><button className="btn" disabled={create.isPending}>حفظ</button></div>
    </form>
  )
}

function PartnerDetails({ id }: { id: number }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const manage = canManage(me)
  const partner = useQuery({ queryKey: ['partner', id], queryFn: () => endpoints.partner(id) })
  const users = useQuery({ queryKey: ['partner', id, 'users'], queryFn: () => endpoints.partnerUsers(id) })
  const sites = useQuery({ queryKey: ['partner', id, 'sites'], queryFn: () => endpoints.partnerSites(id) })
  const contracts = useQuery({ queryKey: ['partner', id, 'contracts'], queryFn: () => endpoints.partnerContracts(id) })

  const setStatus = useMutation({
    mutationFn: (s: PartnerStatus) => endpoints.setPartnerStatus(id, s),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['partners'] })
      qc.setQueryData(['partner', id], p)
      toast(p.status === 'ACTIVE' ? 'تم تفعيل الجهة' : 'تم تعليق الجهة: لن يتمكن موظفوها من الدخول')
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true),
  })

  if (partner.isLoading) return <Loading />
  if (partner.error) return <ErrorBox error={partner.error} />
  const p = partner.data!

  return (
    <>
      <h3 style={{ margin: '0 0 4px' }}>{p.name}</h3>
      <p className="mu" style={{ margin: '0 0 12px' }}>{SECTORS[p.sector]} · <Tag label={PARTNER_STATUS[p.status]} /></p>
      <Kv k="السجل التجاري"><Num>{p.crNumber ?? '—'}</Num></Kv>
      <Kv k="المدينة">{[p.city, p.country].filter(Boolean).join('، ') || '—'}</Kv>
      <Kv k="الهاتف"><Num>{p.phone ?? '—'}</Num></Kv>
      <Kv k="مدير الحساب">{p.accountManagerName ?? '—'}</Kv>
      <Kv k="تاريخ الإضافة">{date(p.createdAt)}</Kv>
      {p.notes && <Kv k="ملاحظات">{p.notes}</Kv>}

      {manage && (
        <div className="form-actions" style={{ margin: '14px 0' }}>
          {p.status !== 'ACTIVE' && <button className="btn" onClick={() => setStatus.mutate('ACTIVE')}>تفعيل الجهة</button>}
          {p.status !== 'SUSPENDED' && (
            <ConfirmButton onConfirm={() => setStatus.mutate('SUSPENDED')}>تعليق الجهة</ConfirmButton>
          )}
        </div>
      )}

      <h2 className="section">الفريق</h2>
      {users.data?.length === 0 && <Empty>لا يوجد موظفون بعد. ادعُ أول مدير للجهة.</Empty>}
      {users.data?.map((u) => (
        <div className="li" key={u.id}>
          <span><b>{u.fullName}</b><div className="mu" dir="ltr" style={{ textAlign: 'end' }}>{u.email}</div></span>
          <span style={{ textAlign: 'end' }}>
            <Tag label={ACCOUNT_STATUS[u.status]} />
            <div className="mu">{PARTNER_ROLE[u.role]}{u.lastLoginAt ? ` · آخر دخول ${dateTime(u.lastLoginAt)}` : ''}</div>
          </span>
        </div>
      ))}
      {manage && p.status !== 'SUSPENDED' && <InviteForm partnerId={id} />}

      <h2 className="section">المواقع</h2>
      {sites.data?.length === 0 && <Empty>لم تُضف الجهة مواقع بعد (تضيفها من بوابة الشريك).</Empty>}
      {sites.data?.map((s) => (
        <div className="li" key={s.id}><b>{s.name}</b><span className="mu">{s.city ?? ''} · {s.open24Hours ? '24 ساعة' : `${s.opensAt} – ${s.closesAt}`}</span></div>
      ))}

      <h2 className="section">العقود</h2>
      {contracts.data?.length === 0 && <Empty>لا يوجد عقد بعد.</Empty>}
      {contracts.data?.map((c) => (
        <div className="li" key={c.id}>
          <span><b>{c.planName}</b> <span className="mu">({TIER[c.tier]})</span>
            <div className="mu"><Num>{money(c.monthlyFee)}</Num> ر.س شهريًا · {date(c.startDate)} → {date(c.endDate)}</div></span>
          <Tag label={CONTRACT_STATUS[c.status]} />
        </div>
      ))}
      {manage && !contracts.data?.some((c) => c.status === 'ACTIVE' || c.status === 'EXPIRING_SOON') && <ContractForm partnerId={id} />}
    </>
  )
}

function InviteForm({ partnerId }: { partnerId: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<PartnerRole>('PARTNER_ADMIN')
  const invite = useMutation({
    mutationFn: () => endpoints.invitePartnerUser(partnerId, { fullName, email, role }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['partner', partnerId, 'users'] })
      toast(`أُرسلت الدعوة إلى ${r.user.email}`)
      setFullName('')
      setEmail('')
    },
  })
  return (
    <form className="form panel" style={{ marginTop: 12 }} onSubmit={(e) => { e.preventDefault(); invite.mutate() }}>
      <b>دعوة موظف (تصله رسالة بريد لتفعيل حسابه)</b>
      <div className="row2">
        <label className="field"><span>الاسم الكامل</span><input className="input" required value={fullName} onChange={(e) => setFullName(e.target.value)} /></label>
        <label className="field"><span>البريد</span><input className="input" type="email" dir="ltr" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      </div>
      <label className="field"><span>الصلاحية</span>
        <select className="input" value={role} onChange={(e) => setRole(e.target.value as PartnerRole)}>
          {(Object.keys(PARTNER_ROLE) as PartnerRole[]).map((r) => <option key={r} value={r}>{PARTNER_ROLE[r]}</option>)}
        </select>
      </label>
      {invite.error && <ErrorBox error={invite.error} />}
      <div className="form-actions"><button className="btn" disabled={invite.isPending}>إرسال الدعوة</button></div>
    </form>
  )
}

function ContractForm({ partnerId }: { partnerId: number }) {
  const qc = useQueryClient()
  const toast = useToast()
  const today = new Date().toISOString().slice(0, 10)
  const nextYear = new Date(Date.now() + 364 * 86400_000).toISOString().slice(0, 10)
  const [f, setF] = useState<ContractRequest>({ planName: '', tier: 'PROFESSIONAL', monthlyFee: 0, startDate: today, endDate: nextYear })
  const create = useMutation({
    mutationFn: () => endpoints.createContract(partnerId, f),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['partner', partnerId, 'contracts'] })
      qc.invalidateQueries({ queryKey: ['contracts'] })
      qc.invalidateQueries({ queryKey: ['summary'] })
      toast('أُنشئ العقد')
    },
  })
  return (
    <form className="form panel" style={{ marginTop: 12 }} onSubmit={(e) => { e.preventDefault(); create.mutate() }}>
      <b>عقد جديد</b>
      <div className="row2">
        <label className="field"><span>اسم الباقة</span><input className="input" required placeholder="باقة الضيافة" value={f.planName} onChange={(e) => setF({ ...f, planName: e.target.value })} /></label>
        <label className="field"><span>الفئة</span>
          <select className="input" value={f.tier} onChange={(e) => setF({ ...f, tier: e.target.value as PlanTier })}>
            {(Object.keys(TIER) as PlanTier[]).map((t) => <option key={t} value={t}>{TIER[t]}</option>)}
          </select>
        </label>
      </div>
      <div className="row2">
        <label className="field"><span>الرسوم الشهرية (ر.س)</span><input className="input" type="number" min={0} step="0.01" dir="ltr" required value={f.monthlyFee} onChange={(e) => setF({ ...f, monthlyFee: Number(e.target.value) })} /></label>
        <label className="field"><span>الحد الأقصى للمستفيدين</span><input className="input" type="number" min={1} dir="ltr" value={f.maxBeneficiaries ?? ''} onChange={(e) => setF({ ...f, maxBeneficiaries: e.target.value ? Number(e.target.value) : null })} /></label>
      </div>
      <div className="row2">
        <label className="field"><span>البداية</span><input className="input" type="date" required value={f.startDate} onChange={(e) => setF({ ...f, startDate: e.target.value })} /></label>
        <label className="field"><span>النهاية</span><input className="input" type="date" required value={f.endDate} onChange={(e) => setF({ ...f, endDate: e.target.value })} /></label>
      </div>
      {create.error && <ErrorBox error={create.error} />}
      <div className="form-actions"><button className="btn" disabled={create.isPending}>إنشاء العقد</button></div>
    </form>
  )
}
