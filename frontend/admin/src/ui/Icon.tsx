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
  mail: 'M3 6h18v12H3zM3 7l9 6 9-6',
} as const

export type IconName = keyof typeof PATHS

export function Icon({ name, className = 'i' }: { name: IconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}
