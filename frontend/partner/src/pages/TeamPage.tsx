import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AccountStatus, ApiKeyCreated, PartnerRole } from '../api/types'
import { useAuth, useCan } from '../auth/AuthContext'
import { ago, date } from '../lib/format'
import { ACCOUNT_STATUS, keys, PARTNER_ROLE, ROLE_HINT } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { useToast } from '../ui/Toast'

/** "الفريق والأجهزة": staff accounts and roles, and the API keys the door readers use. */
export function TeamPage() {
  const can = useCan()
  return (
    <>
      <Team />
      {can('admin') && <DeviceKeys />}
    </>
  )
}

function Team() {
  const { me } = useAuth()
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const team = useQuery({ queryKey: ['team'], queryFn: endpoints.team })
  const [inviting, setInviting] = useState(false)
  const update = useMutation({
    mutationFn: ({ id, ...body }: { id: number; role?: PartnerRole; status?: AccountStatus }) => endpoints.updateMember(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['team'] }); toast('حُفظ') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true),
  })

  return (
    <>
      <div className="toolbar">
        <h2 className="grow" style={{ margin: 0 }}>الفريق</h2>
        {can('admin') && <button className="btn" onClick={() => setInviting(true)}><Icon name="plus" />دعوة موظف</button>}
      </div>
      {team.error && <ErrorBox error={team.error} />}
      {team.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الموظف</th><th>الدور</th><th>الحالة</th><th>آخر دخول</th></tr></thead>
            <tbody>
              {team.data?.map((u) => {
                const editable = can('admin') && u.id !== me?.id && u.status !== 'INVITED'
                return (
                  <tr key={u.id}>
                    <td className="ent"><b>{u.fullName}{u.id === me?.id && <span className="mu"> (أنت)</span>}</b><small><Num>{u.email}</Num></small></td>
                    <td>
                      {editable ? (
                        <select className="input" style={{ width: 'auto', padding: '4px 8px' }} value={u.role}
                                onChange={(e) => update.mutate({ id: u.id, role: e.target.value as PartnerRole })}>
                          {keys(PARTNER_ROLE).map((r) => <option key={r} value={r}>{PARTNER_ROLE[r]}</option>)}
                        </select>
                      ) : PARTNER_ROLE[u.role]}
                    </td>
                    <td>
                      <Tag label={ACCOUNT_STATUS[u.status]} />{' '}
                      {editable && (
                        <ConfirmButton className="btn gh sm" onConfirm={() => update.mutate({ id: u.id, status: u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' })}>
                          {u.status === 'ACTIVE' ? 'تعليق' : 'تفعيل'}
                        </ConfirmButton>
                      )}
                    </td>
                    <td>{u.lastLoginAt ? ago(u.lastLoginAt) : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      <div className="panel" style={{ marginTop: 14 }}>
        {keys(ROLE_HINT).map((r) => <div key={r} className="kv"><span>{PARTNER_ROLE[r]}</span><b style={{ fontWeight: 400 }}>{ROLE_HINT[r]}</b></div>)}
      </div>

      <Drawer open={inviting} onClose={() => setInviting(false)} title="دعوة موظف">
        {inviting && <InviteForm onDone={() => setInviting(false)} />}
      </Drawer>
    </>
  )
}

function InviteForm({ onDone }: { onDone: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<PartnerRole>('FRONT_DESK')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await endpoints.invite({ fullName: fullName.trim(), email: email.trim(), role })
      qc.invalidateQueries({ queryKey: ['team'] })
      toast('أُرسلت الدعوة إلى بريده. الرابط صالح لمدة 7 أيام.')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الإرسال')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>الاسم الكامل</span><input className="input" required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} /></label>
      <label className="field"><span>البريد الإلكتروني</span><input className="input" type="email" dir="ltr" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
      <label className="field"><span>الدور</span>
        <select className="input" value={role} onChange={(e) => setRole(e.target.value as PartnerRole)}>
          {keys(PARTNER_ROLE).map((r) => <option key={r} value={r}>{PARTNER_ROLE[r]}</option>)}
        </select>
      </label>
      <p className="mu" style={{ margin: 0 }}>{ROLE_HINT[role]}</p>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn">إرسال الدعوة</button></div>
    </form>
  )
}

/**
 * Device keys: each door reader / turnstile sends "X-API-Key: vx_..." with every card scan.
 * The full key is shown ONCE, right after creating it. The server only keeps a hash, so it can't show it again.
 */
function DeviceKeys() {
  const qc = useQueryClient()
  const toast = useToast()
  const list = useQuery({ queryKey: ['api-keys'], queryFn: endpoints.apiKeys })
  const [name, setName] = useState('')
  const [created, setCreated] = useState<ApiKeyCreated | null>(null)
  const create = useMutation({
    mutationFn: () => endpoints.createApiKey(name.trim()),
    onSuccess: (k) => { setCreated(k); setName(''); qc.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الإنشاء', true),
  })
  const revoke = useMutation({
    mutationFn: endpoints.revokeApiKey,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['api-keys'] }); toast('أُلغي المفتاح. أي جهاز يستخدمه سيتوقف فورًا.') },
  })
  const copy = async () => {
    try { await navigator.clipboard.writeText(created!.apiKey); toast('نُسخ المفتاح') } catch { toast('انسخ المفتاح يدويًا', true) }
  }

  return (
    <>
      <h2 className="section">مفاتيح الأجهزة</h2>
      <p className="mu" style={{ marginTop: 0 }}>أجهزة القراءة على الأبواب والبوابات تتصل بـ VORNX بمفتاح خاص. أنشئ مفتاحًا لكل جهاز أو مجموعة أجهزة، وألغِه فورًا إذا فُقد الجهاز.</p>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); create.mutate() }}>
        <input className="input grow" required maxLength={100} placeholder="اسم المفتاح، مثلًا: قارئات الطابق 12" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn" disabled={create.isPending || !name.trim()}><Icon name="plus" />إنشاء مفتاح</button>
      </form>
      {created && (
        <div className="secret">
          <b>انسخ المفتاح الآن: لن يظهر مرة أخرى.</b>
          <div style={{ margin: '8px 0' }}><code>{created.apiKey}</code></div>
          <div className="form-actions">
            <button className="btn sm" onClick={copy}>نسخ</button>
            <button className="btn gh sm" onClick={() => setCreated(null)}>نسخته، إخفاء</button>
          </div>
        </div>
      )}
      <div className="panel">
        {list.isLoading && <Loading />}
        {list.data?.length === 0 && <Empty>لا توجد مفاتيح بعد.</Empty>}
        {list.data?.map((k) => (
          <div key={k.id} className="li">
            <span className="ent">
              <b>{k.name} <code>{k.keyPrefix}…</code></b>
              <small>أُنشئ {date(k.createdAt)} · {k.lastUsedAt ? `آخر استخدام ${ago(k.lastUsedAt)}` : 'لم يُستخدم بعد'}</small>
            </span>
            {k.active
              ? <ConfirmButton className="btn gh sm" onConfirm={() => revoke.mutate(k.id)}>إلغاء</ConfirmButton>
              : <Tag label={{ text: 'ملغى', tone: 'mu' }} />}
          </div>
        ))}
      </div>
    </>
  )
}
