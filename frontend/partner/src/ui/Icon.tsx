// Simple line icons (same style as the design: 1.7 stroke, round caps). Add new ones to PATHS.
const PATHS = {
  home: 'M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z',
  partners: 'M3 21V7l9-4 9 4v14M9 21v-6h6v6M7 10h2M15 10h2M7 14h2M15 14h2',
  contract: 'M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6',
  invoice: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3',
  support: 'M4 5h16v11H8l-4 4zM8 9h8M8 12h5',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  account: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
  plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6 6 18',
  money: 'M3 6h18v12H3zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 9v.01M18 15v.01',
  alert: 'M12 3 2 20h20zM12 10v4M12 17v.01',
  site: 'M3 21h18M5 21V7l7-4 7 4v14M9 9h2M13 9h2M9 13h2M13 13h2',
  key: 'M8 19a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM11 12l9-9M16 7l3 3',
  live: 'M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM6 12a6 6 0 0 1 12 0M2.5 12a9.5 9.5 0 0 1 19 0',
  plan: 'M4 6h16M4 12h16M4 18h10',
  event: 'M4 6h16v14H4zM4 10h16M9 3v4M15 3v4',
  team: 'M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c0-4 3-6 6.5-6s6.5 2 6.5 6M17 5a3 3 0 0 1 0 6M21 20c0-3-1.5-5-4-5.5',
  check: 'M5 12l5 5 9-10',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, className = 'i' }: { name: IconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}
