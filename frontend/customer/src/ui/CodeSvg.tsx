import { useEffect, useState } from 'react'
import { drawCode, type PassFormat } from '../lib/barcode'

/** Any code drawn as a sharp SVG (QR, barcode...). Shows a soft placeholder while the drawing library loads. */
export function CodeSvg({ code, format, className }: { code: string; format: PassFormat; className?: string }) {
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let alive = true
    setSvg(null); setFailed(false)
    drawCode(code, format).then((s) => { if (alive) setSvg(s) }).catch(() => { if (alive) setFailed(true) })
    return () => { alive = false }
  }, [code, format])
  if (failed) return <code className={className}>{code}</code>
  return svg ? <span className={className} dangerouslySetInnerHTML={{ __html: svg }} /> : <span className={`${className ?? ''} code-wait`} />
}
