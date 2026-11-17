/**
 * The VORNX wordmark from the design (index_76), drawn as a CSS mask so it takes the text color:
 * dark on the light theme, white on the dark theme. "side" = the sidebar's, "mark" = the login card's.
 */
export function Logo({ variant = 'side' }: { variant?: 'side' | 'mark' }) {
  return <span className={`brand ${variant}`} role="img" aria-label="VORNX" />
}
