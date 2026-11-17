import type { Sector } from '../api/types'

/** The 8 service icons from the design (index 55), one per partner sector. Static markup only. */
const SHAPES: Record<Sector, string> = {
  BANK: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M3 10h18M7 15h4"/>',
  WORKPLACE: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  TRANSPORT: '<rect x="4" y="4" width="16" height="13" rx="2"/><path d="M4 11h16M8 21v-4M16 21v-4"/>',
  AUTOMOTIVE: '<path d="M5 16l1.5-5a2 2 0 012-1.5h7a2 2 0 012 1.5L19 16M4 16h16v3H4zM7 19v1.5M17 19v1.5"/>',
  HOTEL: '<path d="M4 21V8l8-5 8 5v13M9 21v-6h6v6M8 11h2M14 11h2"/>',
  CLUB: '<path d="M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12"/>',
  EVENTS: '<path d="M4 8a2 2 0 012-2h12a2 2 0 012 2v2a2 2 0 000 4v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2a2 2 0 000-4z"/><path d="M14 6v12"/>',
  PARKING: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M10 16V8h3a2.5 2.5 0 010 5h-3"/>',
}

/** The design's order: الدفع، الدوام، النقل، السيارات، الفنادق، الأندية، الفعاليات، المواقف */
export const SECTOR_ORDER: Sector[] = ['BANK', 'WORKPLACE', 'TRANSPORT', 'AUTOMOTIVE', 'HOTEL', 'CLUB', 'EVENTS', 'PARKING']

export function SectorIcon({ sector, className = 'i' }: { sector: Sector; className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" aria-hidden="true" dangerouslySetInnerHTML={{ __html: SHAPES[sector] }} />
}
