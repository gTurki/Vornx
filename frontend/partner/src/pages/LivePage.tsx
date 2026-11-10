import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { AccessEvent } from '../api/types'
import { count, time } from '../lib/format'
import { DENY, METHOD } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { SiteSelect } from '../ui/pickers'

/**
 * "التشغيل المباشر": every door decision, newest first. Each new decision arrives over the WebSocket
 * (useRealtime reloads 'access-events'); a slow poll stays as a safety net.
 */
export function LivePage() {
  const [siteId, setSiteId] = useState<number | null>(null)
  const [onlyDenied, setOnlyDenied] = useState(false)
  const [paused, setPaused] = useState(false)
  const [page, setPage] = useState(0)
  const params = { siteId: siteId ?? undefined, page }
  const live = useQuery({
    queryKey: ['access-events', params],
    queryFn: () => endpoints.accessEvents(params),
    // Only the first page is "live"; older pages don't change.
    refetchInterval: paused || page > 0 ? false : 30_000, // a safety net: new scans arrive instantly over the WebSocket
  })

  // Remember which rows we've already shown, so only NEW ones flash.
  const seen = useRef<Set<string> | null>(null)
  const rows = live.data?.content ?? []
  const fresh = new Set(seen.current ? rows.filter((r) => !seen.current!.has(r.decisionNumber)).map((r) => r.decisionNumber) : [])
  useEffect(() => {
    if (live.data) seen.current = new Set([...(seen.current ?? []), ...live.data.content.map((r) => r.decisionNumber)])
  }, [live.data])

  const shown = onlyDenied ? rows.filter((r) => r.decision === 'DENY') : rows
  const denied = rows.filter((r) => r.decision === 'DENY').length

  return (
    <>
      <p className="lead">كل محاولة دخول في مواقعك: من، وأين، وبأي طريقة، وهل سُمح له. القائمة تتحدث لحظيًا مع كل عملية.</p>
      <div className="toolbar">
        <SiteSelect all value={siteId} onChange={(v) => { setSiteId(v); setPage(0) }} />
        <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input type="checkbox" checked={onlyDenied} onChange={(e) => setOnlyDenied(e.target.checked)} /> المرفوضة فقط
        </label>
        <span className="grow" />
        <span className="mu">{paused || page > 0 ? 'متوقف' : <><span className="pulse" />مباشر</>}</span>
        <button className="btn gh sm" onClick={() => setPaused(!paused)}>{paused ? 'استئناف' : 'إيقاف مؤقت'}</button>
      </div>

      {live.data && (
        <div className="kpis">
          <div className="kp"><span className="num">{count(live.data.totalElements)}</span><span>عملية مسجّلة</span></div>
          <div className="kp"><span className="num">{count(rows.length - denied)}</span><span>مسموح في هذه الصفحة</span></div>
          <div className={`kp${denied ? ' bad' : ''}`}><span className="num">{count(denied)}</span><span>مرفوض في هذه الصفحة</span></div>
        </div>
      )}

      {live.error && <ErrorBox error={live.error} />}
      <div className="panel">
        {live.isLoading && <Loading />}
        {live.data && shown.length === 0 && <Empty>لا توجد عمليات هنا.</Empty>}
        <div className="feed">
          {shown.map((e) => <AccessRow key={e.decisionNumber} e={e} isNew={fresh.has(e.decisionNumber)} />)}
        </div>
      </div>
      {live.data && <Pager page={live.data.page} totalPages={live.data.totalPages} onChange={setPage} />}
    </>
  )
}

/** One door decision. Also used on the overview page. */
export function AccessRow({ e, isNew }: { e: AccessEvent; isNew?: boolean }) {
  const allowed = e.decision === 'ALLOW'
  return (
    <div className={`feed-row${isNew ? ' new' : ''}`}>
      <Num>{time(e.occurredAt)}</Num>
      <div>
        <b>{e.holderName ?? 'بطاقة غير معروفة'}</b>
        <span className="mu"> · {e.accessPointName} · {e.siteName}</span>
        <div className="mu">
          {METHOD[e.method]} · {e.direction === 'ENTRY' ? 'دخول' : 'خروج'}
          {e.permitNumber && <> · <Num>{e.permitNumber}</Num></>}
          {e.enforcementResult === 'FAILED' && <span className="bad"> · سمح النظام لكن الباب لم يُفتح</span>}
        </div>
      </div>
      <Tag label={allowed ? { text: 'مسموح', tone: 'ok' } : { text: e.denyReason ? DENY[e.denyReason] : 'مرفوض', tone: 'bad' }} />
    </div>
  )
}
