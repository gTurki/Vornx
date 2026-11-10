// Simple line icons (1.7 stroke, round caps, like the design). Add new ones to PATHS.
const PATHS = {
  home: 'M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z',
  card: 'M3 6h18v12H3zM3 10h18M7 15h4',
  key: 'M8 19a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM11 12l9-9M16 7l3 3',
  wallet: 'M3 7h16v13H3zM3 7l12-4v4M15 13h2',
  ticket: 'M3 8a2 2 0 0 0 0 4v0a2 2 0 0 1 0 4v2h18v-2a2 2 0 0 1 0-4 2 2 0 0 0 0-4V6H3zM14 6v12',
  sub: 'M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3M18 3v4h-4M6 21v-4h4',
  log: 'M4 5h16M4 10h16M4 15h10M4 20h7',
  account: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
  plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6 6 18',
  send: 'M4 12 20 4l-6 16-3-7z',
  door: 'M6 21V3h12v18M3 21h18M14 12h.01',
  wifi: 'M12 18h.01M8.5 14.5a5 5 0 0 1 7 0M5.5 11.5a9 9 0 0 1 13 0M2.5 8.5a13 13 0 0 1 19 0',
  bed: 'M3 18V7M3 13h18v5M21 18v-3a3 3 0 0 0-3-3H10v1M6.5 11a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
  work: 'M4 21V4h11v17M15 9h5v12M8 8h3M8 12h3M8 16h3',
  gym: 'M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12',
  parking: 'M4 4h16v16H4zM10 16V8h3a2.5 2.5 0 0 1 0 5h-3',
  bus: 'M5 4h14v12H5zM5 10h14M8 20v-4M16 20v-4',
  car: 'M5 16l1.5-5a2 2 0 0 1 2-1.5h7a2 2 0 0 1 2 1.5L19 16M4 16h16v3H4z',
  star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z',
  calendar: 'M4 6h16v14H4zM4 10h16M9 3v4M15 3v4',
  pin: 'M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11zM12 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, className = 'i' }: { name: IconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}
