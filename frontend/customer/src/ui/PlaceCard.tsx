import type { Permit, PermitType } from '../api/types'
import { PERMIT_TYPE } from '../lib/labels'
import { tr } from '../i18n'
import { useTilt } from './VCard'

/** The 3D card picture for each kind of place (from the VORNX design). HOME and CAR have none yet: the main card is used. */
const ART: Partial<Record<PermitType, string>> = {
  HOTEL_KEY: 'hotel', CLUB: 'gym', PARKING: 'park', TRANSIT: 'bus', WORKPLACE: 'work', EVENT_PASS: 'event',
}
/** Where each picture says "Valid Until" (percent from the right / from the top): the date goes just under it. */
const EXPIRY_AT: Record<string, [number, number]> = {
  gym: [6.0, 92.7], hotel: [5.0, 94.2], park: [5.3, 93.6], bus: [6.3, 92.8], work: [5.5, 92.8], event: [5.2, 93.0], pay: [6.6, 93.4],
}

export function hasPlaceArt(type: PermitType) {
  return ART[type] !== undefined
}

/** "2026-11-21T09:52:00Z" -> "21/11/2026" (as printed on a card). */
function cardDate(iso: string) {
  const d = new Date(iso)
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`
}

/**
 * A key / membership / pass drawn as its own VORNX card (hotel, club, parking...), with the same 3D tilt and light
 * as the main card. The expiry is printed where the card says "Valid Until"; a small badge names the place.
 */
export function PlaceCard({ permit, art }: { permit?: Permit; art?: 'pay' }) {
  const tilt = useTilt<HTMLDivElement>()
  const key = art ?? (permit ? ART[permit.type] : undefined)
  if (!key) return null
  const [right, top] = EXPIRY_AT[key]
  const live = permit?.status === 'ACTIVE' && new Date(permit.validFrom).getTime() <= Date.now()
  const label = permit ? `${PERMIT_TYPE[permit.type]} · ${permit.siteName}` : tr('الدفع')

  return (
    <div className="vwrap pcard-wrap">
      <div ref={tilt} className={`vcard pcard${permit && permit.status !== 'ACTIVE' && permit.status !== 'PENDING' ? ' off' : ''}`}
           role="img" aria-label={permit ? tr('{0}، صالحة حتى {1}', { 0: label, 1: cardDate(permit.validUntil) }) : label}>
        <img src={`/cards/${key}.webp`} alt="" draggable={false} loading="lazy" />
        {permit && <span className="pc-exp" style={{ right: `${right}%`, top: `${top}%` }}>{cardDate(permit.validUntil)}</span>}
        {permit && <span className={`pc-badge${live ? '' : ' later'}`} dir="auto">{permit.siteName}</span>}
        <span className="vc-shine" aria-hidden="true" />
      </div>
    </div>
  )
}
