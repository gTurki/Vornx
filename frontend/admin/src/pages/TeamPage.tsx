import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AdminRole, TeamInviteResponse, TeamMember } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ago, dateTime } from '../lib/format'
import { ACCOUNT_STATUS, ADMIN_ROLE } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../ui/Toast'

/** What each role can do, shown next to the role picker so the choice is clear. */
const ROLE_HINT: Record<AdminRole, string> = {
  SUPER_ADMIN: 'كل شيء، بما فيه إدارة الفريق.',
  ACCOUNT_MANAGER: 'الجهات والعقود والفواتير وطلبات الانضمام.',
  SUPPORT_AGENT: 'طلبات الدعم ورسائل الموقع، ومشاهدة الباقي.',
  AUDITOR: 'مشاهدة فقط، مع سجل النشاط.',
}
const ROLES = Object.keys(ADMIN_ROLE) as AdminRole[]

/**
 * "فريق VORNX": the people who use this dashboard. A SUPER_ADMIN invites by email (no shared passwords),
 * changes roles and suspends. The server refuses changing yourself and removing the last active super admin.
 */
export function TeamPage() {
  const { me } = useAuth()
  const isSuper = me?.role === 'SUPER_ADMIN'
  const [openId, setOpenId] = useState<number | null>(null)
  const [inviting, setInviting] = useState(false)
  const team = useQuery({ queryKey: ['team'], queryFn: endpoints.team })
  const open = team.data?.find((m) => m.id === openId) ?? null

  return (
    <>
      <p className="lead">
        من يستخدم لوحة الإدارة. الدعوة تصل بالبريد ليختار العضو كلمة مروره بنفسه، والرابط صالح 3 أيام ويُستخدم مرة واحدة.
        {!isSuper && ' إدارة الفريق للمدير العام فقط.'}
      </p>
      {isSuper && (
        <div className="form-actions" style={{ marginBottom: 14, justifyContent: 'flex-start' }}>
          <button className="btn" onClick={() => setInviting(true)}>دعوة عضو</button>
        </div>
      )}
      {team.error && <ErrorBox error={team.error} />}
      {team.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>العضو</th><th>الصلاحية</th><th>الحالة</th><th>آخر دخول</th></tr></thead>
            <tbody>
              {team.data?.map((m) => (
                <tr key={m.id} className="click" onClick={() => setOpenId(m.id)}>
                  <td className="ent"><b>{m.fullName}{m.id === me?.id ? ' (أنت)' : ''}</b><small><Num>{m.email}</Num></small></td>
                  <td>{ADMIN_ROLE[m.role]}</td>
                  <td><Tag label={ACCOUNT_STATUS[m.status]} /></td>
                  <td>{m.status === 'INVITED' ? <span className="mu">{inviteLeft(m)}</span> : m.lastLoginAt ? ago(m.lastLoginAt) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {team.data?.length === 0 && <Empty>لا يوجد أعضاء.</Empty>}
        </div>
      )}

      <Drawer open={inviting} onClose={() => setInviting(false)} title="دعوة عضو لفريق VORNX">
        {inviting && <InviteForm onDone={() => setInviting(false)} />}
      </Drawer>
      <Drawer open={open !== null} onClose={() => setOpenId(null)} title="عضو في الفريق">
        {open && <MemberDetails m={open} canEdit={isSuper && open.id !== me?.id} />}
      </Drawer>
    </>
  )
}

function inviteLeft(m: TeamMember) {
  if (!m.inviteExpiresAt) return 'بانتظار التفعيل'
  return new Date(m.inviteExpiresAt).getTime() < Date.now() ? 'انتهت الدعوة' : 'بانتظار التفعيل'
}

const errorText = (e: unknown) => (e instanceof ApiError ? e.message : 'تعذّر الحفظ')

/** In development the API also returns the link (expose-tokens), so you can test without an inbox. */
function DevLink({ r }: { r: TeamInviteResponse | undefined }) {
  if (!r?.inviteToken) return null
  const url = `${window.location.origin}/accept-invite?token=${encodeURIComponent(r.inviteToken)}`
  return <p className="mu" style={{ fontSize: 12, wordBreak: 'break-all' }}>رابط التطوير: <a href={url} dir="ltr">{url}</a></p>
}

function InviteForm({ onDone }: { onDone: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<AdminRole>('SUPPORT_AGENT')
  const invite = useMutation({
    mutationFn: () => endpoints.inviteAdmin({ fullName: fullName.trim(), email: email.trim(), role }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['team'] })
      toast(`أُرسلت الدعوة إلى ${r.member.email}`)
      if (!r.inviteToken) onDone()
    },
  })
  return (
    <form className="form" onSubmit={(e) => { e.preventDefault(); invite.mutate() }}>
      <label className="field"><span>الاسم الكامل</span>
        <input className="input" required maxLength={100} value={fullName} onChange={(e) => setFullName(e.target.value)} /></label>
      <label className="field"><span>البريد الإلكتروني</span>
        <input className="input" type="email" dir="ltr" required maxLength={120} value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <label className="field"><span>الصلاحية</span>
        <select className="input" value={role} onChange={(e) => setRole(e.target.value as AdminRole)}>
          {ROLES.map((r) => <option key={r} value={r}>{ADMIN_ROLE[r]}</option>)}
        </select>
      </label>
      <p className="mu" style={{ margin: 0, fontSize: 13 }}>{ROLE_HINT[role]}</p>
      {invite.error && <ErrorBox error={invite.error} />}
      <DevLink r={invite.data} />
      <div className="form-actions"><button className="btn" disabled={invite.isPending}>{invite.isPending ? 'جارٍ الإرسال…' : 'إرسال الدعوة'}</button></div>
    </form>
  )
}

function MemberDetails({ m, canEdit }: { m: TeamMember; canEdit: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [role, setRole] = useState<AdminRole>(m.role)
  const done = (text: string) => { qc.invalidateQueries({ queryKey: ['team'] }); toast(text) }
  const onError = (e: unknown) => toast(errorText(e), true)
  const update = useMutation({
    mutationFn: (body: Parameters<typeof endpoints.updateAdmin>[1]) => endpoints.updateAdmin(m.id, body),
    onSuccess: (r) => done(r.status === 'SUSPENDED' ? `عُلّق حساب ${r.fullName} وخرج من جميع الأجهزة` : `حُفظت التغييرات على ${r.fullName}`),
    onError,
  })
  const resend = useMutation({
    mutationFn: () => endpoints.resendAdminInvite(m.id),
    onSuccess: () => done(`أُرسلت دعوة جديدة إلى ${m.email}`),
    onError,
  })

  return (
    <>
      <div className="li"><b>{m.fullName}</b><Tag label={ACCOUNT_STATUS[m.status]} /></div>
      <Kv k="البريد"><a href={`mailto:${m.email}`}><Num>{m.email}</Num></a></Kv>
      <Kv k="الصلاحية">{ADMIN_ROLE[m.role]}</Kv>
      <Kv k="أُضيف">{dateTime(m.createdAt)}</Kv>
      {m.status === 'INVITED'
        ? <Kv k="الدعوة">{inviteLeft(m)}{m.inviteExpiresAt ? ` · حتى ${dateTime(m.inviteExpiresAt)}` : ''}</Kv>
        : <Kv k="آخر دخول">{m.lastLoginAt ? dateTime(m.lastLoginAt) : '—'}</Kv>}

      {canEdit && (
        <>
          <h2 className="section">تعديل</h2>
          {m.status !== 'SUSPENDED' && (
            <form className="form" onSubmit={(e) => { e.preventDefault(); update.mutate({ role }) }}>
              <label className="field"><span>الصلاحية</span>
                <select className="input" value={role} onChange={(e) => setRole(e.target.value as AdminRole)}>
                  {ROLES.map((r) => <option key={r} value={r}>{ADMIN_ROLE[r]}</option>)}
                </select>
              </label>
              <p className="mu" style={{ margin: 0, fontSize: 13 }}>{ROLE_HINT[role]} يُسجَّل خروجه ليدخل بالصلاحية الجديدة.</p>
              <div className="form-actions"><button className="btn" disabled={update.isPending || role === m.role}>حفظ الصلاحية</button></div>
            </form>
          )}
          <div className="form-actions" style={{ justifyContent: 'flex-start' }}>
            {m.status === 'INVITED' && (
              <button className="btn gh" disabled={resend.isPending} onClick={() => resend.mutate()}>إعادة إرسال الدعوة</button>
            )}
            {m.status === 'SUSPENDED'
              ? <button className="btn gh" disabled={update.isPending} onClick={() => update.mutate({ status: 'ACTIVE' })}>إعادة التفعيل</button>
              : <ConfirmButton disabled={update.isPending} onConfirm={() => update.mutate({ status: 'SUSPENDED' })}>
                  {m.status === 'INVITED' ? 'إلغاء الدعوة' : 'تعليق الحساب'}
                </ConfirmButton>}
          </div>
          <DevLink r={resend.data} />
        </>
      )}
    </>
  )
}
