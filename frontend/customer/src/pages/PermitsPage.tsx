import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AccessDecision, Permit } from '../api/types'
import { dateTime, hhmm, time } from '../lib/format'
import { DAY, DENY, PERMIT_ICON, PERMIT_STATUS, PERMIT_TYPE } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { Icon } from '../ui/Icon'

/** "التصاريح": every door the customer may open, and a button to open it from the phone. */
export function PermitsPage() {
  const [tab, setTab] = useState<'now' | 'old'>('now')
  const permits = useQuery({ queryKey: ['permits'], queryFn: endpoints.permits })
  const now = (permits.data ?? []).filter((p) => p.status === 'ACTIVE' || p.status === 'PENDING' || p.status === 'SUSPENDED')
  const old = (permits.data ?? []).filter((p) => p.status === 'EXPIRED' || p.status === 'REVOKED')
  const shown = tab === 'now' ? now : old

  return (
    <>
      <p className="lead">كل تصريح يحدد الأبواب التي تفتحها بطاقتك، ومتى. وإن نسيت بطاقتك، افتح الباب من هنا.</p>
      <div className="tabs">
        <button className={tab === 'now' ? 'on' : ''} onClick={() => setTab('now')}>الحالية ({now.length})</button>
        <button className={tab === 'old' ? 'on' : ''} onClick={() => setTab('old')}>السابقة ({old.length})</button>
      </div>
      {permits.error && <ErrorBox error={permits.error} />}
      {permits.isLoading && <Loading />}
      {permits.data && shown.length === 0 && <Empty>{tab === 'now' ? 'لا توجد تصاريح حالية.' : 'لا توجد تصاريح سابقة.'}</Empty>}
      <div className="cols" style={{ gap: 16 }}>
        {shown.map((p) => <PermitCard key={p.id} p={p} />)}
      </div>
    </>
  )
}

function PermitCard({ p }: { p: Permit }) {
  const usable = p.status === 'ACTIVE'
  const hours = p.allowedFrom ? `${hhmm(p.allowedFrom)} – ${hhmm(p.allowedTo)}` : null
  return (
    <div className="panel">
      <div className="li" style={{ paddingTop: 0 }}>
        <span style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <span className="tile" style={{ padding: 0, border: 0, background: 'none' }}><span className="ic"><Icon name={PERMIT_ICON[p.type]} /></span></span>
          <span className="ent"><b>{p.siteName}</b><small>{PERMIT_TYPE[p.type]} · {p.partnerName}</small></span>
        </span>
        <Tag label={PERMIT_STATUS[p.status]} />
      </div>
      <div className="kv"><span>صالح حتى</span><b>{dateTime(p.validUntil)}</b></div>
      {(p.allowedDays.length > 0 || hours) && (
        <div className="kv"><span>الأوقات</span><b>
          {p.allowedDays.length ? p.allowedDays.map((d) => DAY[d]).join('، ') : 'كل الأيام'}{hours && <> · <Num>{hours}</Num></>}
        </b></div>
      )}
      <div className="kv"><span>رمز التحقق (للاستقبال)</span><b><Num>{p.verificationCode}</Num></b></div>
      {p.conditions && <p className="mu" style={{ margin: '8px 0 0' }}>{p.conditions}</p>}

      {p.wholeSite ? (
        <p className="mu" style={{ marginBottom: 0 }}>بطاقتك تفتح كل أبواب هذا الموقع.</p>
      ) : (
        <div style={{ marginTop: 8 }}>
          {p.accessPoints.map((ap) => <DoorRow key={ap.id} id={ap.id} name={ap.name} enabled={usable} />)}
        </div>
      )}
    </div>
  )
}

/** One door with an "افتح" button. The backend checks the permit again before it unlocks anything. */
function DoorRow({ id, name, enabled }: { id: number; name: string; enabled: boolean }) {
  const qc = useQueryClient()
  const [result, setResult] = useState<AccessDecision | null>(null)
  const [error, setError] = useState<string | null>(null)
  const open = useMutation({
    mutationFn: () => endpoints.openDoor(id),
    onMutate: () => { setResult(null); setError(null) },
    onSuccess: (d) => { setResult(d); qc.invalidateQueries({ queryKey: ['access-log'] }) },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'تعذّر الاتصال'),
  })
  return (
    <div className="li">
      <span className="ent">
        <b>{name}</b>
        {result && (result.decision === 'ALLOW'
          ? <small className="ok">✓ فُتح الباب · {time(result.occurredAt)}</small>
          : <small className="bad">✕ {result.denyReason ? DENY[result.denyReason] : 'مرفوض'}</small>)}
        {error && <small className="bad">{error}</small>}
      </span>
      <button className="btn sm" disabled={!enabled || open.isPending} onClick={() => open.mutate()}>
        <Icon name="door" />{open.isPending ? 'جارٍ الفتح…' : 'افتح'}
      </button>
    </div>
  )
}

/** "سجل الدخول": every time the customer's card or phone was used, and the answer. */
export function AccessLogPage() {
  const [page, setPage] = useState(0)
  const log = useQuery({ queryKey: ['access-log', page], queryFn: () => endpoints.accessLog(page) })
  return (
    <>
      <p className="lead">كل مرة استخدمت فيها بطاقتك أو جوالك. إن رأيت عملية لا تعرفها، أوقف بطاقتك من صفحة بطاقتي.</p>
      {log.error && <ErrorBox error={log.error} />}
      {log.isLoading && <Loading />}
      {log.data?.content.length === 0 && <Empty>لا توجد عمليات بعد.</Empty>}
      <div className="panel" hidden={!log.data?.content.length}>
        {log.data?.content.map((e) => (
          <div key={e.decisionNumber} className="li">
            <span className="ent">
              <b>{e.accessPointName} · {e.siteName}</b>
              <small>{dateTime(e.occurredAt)} · {e.method === 'REMOTE' ? 'من الجوال' : e.method === 'CARD' ? 'بالبطاقة' : e.method}</small>
            </span>
            {e.decision === 'ALLOW'
              ? <Tag label={{ text: 'مسموح', tone: 'ok' }} />
              : <Tag label={{ text: e.denyReason ? DENY[e.denyReason] : 'مرفوض', tone: 'bad' }} />}
          </div>
        ))}
      </div>
      {log.data && <Pager page={log.data.page} totalPages={log.data.totalPages} onChange={setPage} />}
    </>
  )
}
