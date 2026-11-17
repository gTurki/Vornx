/**
 * The VORNX logo from the design, drawn as a CSS mask so it takes the text color: white on the blue sidebar,
 * brand blue on the login card. "full" = wordmark + "للتقنية والأنظمة الذكية"; "mark" = wordmark only (phones).
 */
export function Logo({ variant = 'full' }: { variant?: 'full' | 'mark' }) {
  return <span className={`brand ${variant}`} role="img" aria-label="VORNX للتقنية والأنظمة الذكية" />
}
