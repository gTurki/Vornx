import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AccessMethod, AccessPoint, AccessPointStatus, AccessPointType, Site, SiteType } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { hhmm } from '../lib/format'
import { AP_STATUS, AP_TYPE, keys, METHOD, SITE_TYPE } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { useAccessPoints, useSites } from '../ui/pickers'
import { useToast } from '../ui/Toast'

/** "المواقع ونقاط الدخول": the partner's places (a hotel, an office...) and their doors, gates and rooms. */
export function SitesPage() {
  const can = useCan()
  const sites = useSites()
  const [openId, setOpenId] = useState<number | null>(null)
  const [editing, setEditing] = useState<Site | 'new' | null>(null)
  const open = sites.data?.find((s) => s.id === openId) ?? null

  return (
    <>
      <p className="lead">كل موقع له نقاط دخول: غرفة، باب، بوابة، حاجز مواقف. كل جهاز قارئ يرتبط بنقطة، وكل تصريح يحدد النقاط التي يفتحها.</p>
      <div className="toolbar">
        <span className="grow" />
        {can('edit') && <button className="btn" onClick={() => setEditing('new')}><Icon name="plus" />موقع جديد</button>}
      </div>
      {sites.error && <ErrorBox error={sites.error} />}
      {sites.isLoading && <Loading />}
      {sites.data?.length === 0 && <Empty>لا توجد مواقع بعد. أضف أول موقع لتبدأ بإضافة نقاط الدخول.</Empty>}
      <div className="cards">
        {sites.data?.map((s) => (
          <button key={s.id} className="card" onClick={() => setOpenId(s.id)}>
            <h3>{s.name}</h3>
            <span className="mu">{SITE_TYPE[s.type]}{s.city ? ` · ${s.city}` : ''}</span>
            <span className="mu">{s.open24Hours ? 'مفتوح 24 ساعة' : <>يفتح <Num>{hhmm(s.opensAt)}</Num> – <Num>{hhmm(s.closesAt)}</Num></>}</span>
          </button>
        ))}
      </div>

      <Drawer open={open !== null} onClose={() => setOpenId(null)} title={open?.name ?? ''}>
        {open && <SiteDetails site={open} onEdit={() => setEditing(open)} />}
      </Drawer>
      <Drawer open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'موقع جديد' : 'تعديل الموقع'}>
        {editing !== null && <SiteForm site={editing === 'new' ? null : editing} onDone={(s) => { setEditing(null); setOpenId(s.id) }} />}
      </Drawer>
    </>
  )
}

function SiteDetails({ site, onEdit }: { site: Site; onEdit: () => void }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const aps = useAccessPoints(site.id)
  const [apForm, setApForm] = useState<AccessPoint | 'new' | null>(null)
  const status = useMutation({
    mutationFn: ({ id, s }: { id: number; s: AccessPointStatus }) => endpoints.setAccessPointStatus(id, s),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['access-points', site.id] }); toast('حُدّثت الحالة') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true),
  })

  if (apForm !== null) {
    return <AccessPointForm siteId={site.id} ap={apForm === 'new' ? null : apForm} onDone={() => setApForm(null)} />
  }

  return (
    <>
      <p className="mu" style={{ marginTop: 0 }}>
        {SITE_TYPE[site.type]} · {[site.address, site.city, site.country].filter(Boolean).join('، ') || 'بدون عنوان'}
      </p>
      {can('edit') && <div className="form-actions"><button className="btn gh sm" onClick={onEdit}>تعديل الموقع</button></div>}

      <div className="li" style={{ marginTop: 14 }}>
        <h2 style={{ margin: 0 }}>نقاط الدخول</h2>
        {can('edit') && <button className="btn sm" onClick={() => setApForm('new')}><Icon name="plus" />نقطة جديدة</button>}
      </div>
      {aps.isLoading && <Loading />}
      {aps.data?.length === 0 && <Empty>لا توجد نقاط دخول بعد.</Empty>}
      {aps.data?.map((ap) => (
        <div key={ap.id} className="li">
          <span className="ent">
            <b>{ap.name} <span className="num mu">{ap.code}</span></b>
            <small>
              <span className={ap.online ? 'ok' : 'mu'}>{ap.online ? '● متصل' : '○ غير متصل'}</span>
              {' · '}{AP_TYPE[ap.type]}{ap.floor ? ` · الطابق ${ap.floor}` : ''}
              {' · '}{ap.supportedMethods.map((m) => METHOD[m]).join('، ')}
            </small>
          </span>
          <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            {can('operate') ? (
              <select className="input" style={{ width: 'auto', padding: '4px 8px' }} value={ap.status}
                      onChange={(e) => status.mutate({ id: ap.id, s: e.target.value as AccessPointStatus })}>
                {keys(AP_STATUS).map((s) => <option key={s} value={s}>{AP_STATUS[s].text}</option>)}
              </select>
            ) : <Tag label={AP_STATUS[ap.status]} />}
            {can('edit') && <button className="btn gh sm" onClick={() => setApForm(ap)}>تعديل</button>}
          </span>
        </div>
      ))}
    </>
  )
}

function SiteForm({ site, onDone }: { site: Site | null; onDone: (s: Site) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState({
    name: site?.name ?? '', type: site?.type ?? ('HOTEL' as SiteType), address: site?.address ?? '',
    city: site?.city ?? '', country: site?.country ?? 'السعودية',
    opensAt: hhmm(site?.opensAt), closesAt: hhmm(site?.closesAt),
  })
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const body = {
      ...f, name: f.name.trim(), address: f.address.trim() || undefined, city: f.city.trim() || undefined,
      country: f.country.trim() || undefined, opensAt: f.opensAt || null, closesAt: f.closesAt || null,
      latitude: site?.latitude ?? null, longitude: site?.longitude ?? null,
    }
    try {
      const saved = site ? await endpoints.updateSite(site.id, body) : await endpoints.createSite(body)
      qc.invalidateQueries({ queryKey: ['sites'] })
      toast(site ? 'حُفظ الموقع' : 'أُضيف الموقع')
      onDone(saved)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الحفظ')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>اسم الموقع</span><input className="input" required maxLength={150} value={f.name} onChange={set('name')} /></label>
      <label className="field"><span>النوع</span>
        <select className="input" value={f.type} onChange={set('type')}>
          {keys(SITE_TYPE).map((t) => <option key={t} value={t}>{SITE_TYPE[t]}</option>)}
        </select>
      </label>
      <label className="field"><span>العنوان</span><input className="input" maxLength={255} value={f.address} onChange={set('address')} /></label>
      <div className="row2">
        <label className="field"><span>المدينة</span><input className="input" maxLength={80} value={f.city} onChange={set('city')} /></label>
        <label className="field"><span>الدولة</span><input className="input" maxLength={80} value={f.country} onChange={set('country')} /></label>
      </div>
      <div className="row2">
        <label className="field"><span>يفتح (فارغ = 24 ساعة)</span><input className="input" type="time" dir="ltr" value={f.opensAt} onChange={set('opensAt')} /></label>
        <label className="field"><span>يغلق</span><input className="input" type="time" dir="ltr" value={f.closesAt} onChange={set('closesAt')} /></label>
      </div>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn">حفظ</button></div>
    </form>
  )
}

function AccessPointForm({ siteId, ap, onDone }: { siteId: number; ap: AccessPoint | null; onDone: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [code, setCode] = useState(ap?.code ?? '')
  const [name, setName] = useState(ap?.name ?? '')
  const [type, setType] = useState<AccessPointType>(ap?.type ?? 'ROOM')
  const [floor, setFloor] = useState(ap?.floor ?? '')
  const [methods, setMethods] = useState<AccessMethod[]>(ap?.supportedMethods ?? ['CARD', 'MOBILE'])
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const body = { code: code.trim(), name: name.trim(), type, floor: floor.trim() || undefined, supportedMethods: methods }
    try {
      if (ap) await endpoints.updateAccessPoint(ap.id, body)
      else await endpoints.createAccessPoint(siteId, body)
      qc.invalidateQueries({ queryKey: ['access-points', siteId] })
      toast(ap ? 'حُفظت النقطة' : 'أُضيفت النقطة')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الحفظ')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <h2 style={{ margin: 0 }}>{ap ? 'تعديل نقطة الدخول' : 'نقطة دخول جديدة'}</h2>
      <div className="row2">
        <label className="field"><span>الرمز (حروف إنجليزية وأرقام)</span>
          <input className="input" dir="ltr" required maxLength={30} pattern="[A-Za-z0-9_\-]+" placeholder="R-1204" value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        <label className="field"><span>الطابق</span><input className="input" maxLength={20} value={floor} onChange={(e) => setFloor(e.target.value)} /></label>
      </div>
      <label className="field"><span>الاسم</span><input className="input" required maxLength={100} placeholder="الغرفة 1204" value={name} onChange={(e) => setName(e.target.value)} /></label>
      <label className="field"><span>النوع</span>
        <select className="input" value={type} onChange={(e) => setType(e.target.value as AccessPointType)}>
          {keys(AP_TYPE).map((t) => <option key={t} value={t}>{AP_TYPE[t]}</option>)}
        </select>
      </label>
      <div className="field"><span>طرق الفتح المدعومة</span>
        <div className="checks">
          {keys(METHOD).map((m) => (
            <label key={m}>
              <input type="checkbox" checked={methods.includes(m)}
                     onChange={(e) => setMethods(e.target.checked ? [...methods, m] : methods.filter((x) => x !== m))} />
              {METHOD[m]}
            </label>
          ))}
        </div>
      </div>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn">حفظ</button>
        <button type="button" className="btn gh" onClick={onDone}>رجوع</button>
      </div>
    </form>
  )
}
