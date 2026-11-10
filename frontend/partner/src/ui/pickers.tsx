import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { DayOfWeek } from '../api/types'
import { DAYS } from '../lib/labels'

/** The partner's sites. Many pages need them (filters, forms), and the cache shares one request. */
export function useSites() {
  return useQuery({ queryKey: ['sites'], queryFn: endpoints.sites, staleTime: 60_000 })
}

export function useAccessPoints(siteId: number | null) {
  return useQuery({
    queryKey: ['access-points', siteId],
    queryFn: () => endpoints.accessPoints(siteId!),
    enabled: siteId !== null,
  })
}

export function SiteSelect({ value, onChange, all }: {
  value: number | null
  onChange: (id: number | null) => void
  /** Show an "all sites" option (for filters). */
  all?: boolean
}) {
  const sites = useSites()
  return (
    <select className="input" style={all ? { width: 'auto' } : undefined} value={value ?? ''} required={!all}
            onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
      <option value="">{all ? 'كل المواقع' : 'اختر الموقع…'}</option>
      {sites.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
    </select>
  )
}

/** Pick some of the site's access points. None picked = the whole site. */
export function AccessPointChecks({ siteId, value, onChange }: {
  siteId: number | null
  value: number[]
  onChange: (ids: number[]) => void
}) {
  const aps = useAccessPoints(siteId)
  if (siteId === null) return <p className="mu" style={{ margin: 0 }}>اختر الموقع أولًا.</p>
  if (aps.data?.length === 0) return <p className="mu" style={{ margin: 0 }}>لا توجد نقاط دخول في هذا الموقع بعد.</p>
  return (
    <div className="checks">
      {aps.data?.map((ap) => (
        <label key={ap.id}>
          <input type="checkbox" checked={value.includes(ap.id)}
                 onChange={(e) => onChange(e.target.checked ? [...value, ap.id] : value.filter((x) => x !== ap.id))} />
          {ap.name} <span className="num mu">{ap.code}</span>
        </label>
      ))}
    </div>
  )
}

/** Days of the week. None picked = every day. */
export function DayChecks({ value, onChange }: { value: DayOfWeek[]; onChange: (d: DayOfWeek[]) => void }) {
  return (
    <div className="checks">
      {DAYS.map(([d, t]) => (
        <label key={d}>
          <input type="checkbox" checked={value.includes(d)}
                 onChange={(e) => onChange(e.target.checked ? [...value, d] : value.filter((x) => x !== d))} />
          {t}
        </label>
      ))}
    </div>
  )
}
