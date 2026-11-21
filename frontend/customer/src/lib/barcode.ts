// Read a barcode / QR code (from the camera or a screenshot) and draw it again, with zxing-wasm.
// The library and its WebAssembly files are loaded only when needed, and from our own site (not a CDN).
import readerWasm from 'zxing-wasm/reader/zxing_reader.wasm?url'
import writerWasm from 'zxing-wasm/writer/zxing_writer.wasm?url'

/** The formats we store (same names as the API's CustomerPass.Format). */
export type PassFormat = 'QR_CODE' | 'AZTEC' | 'PDF417' | 'DATA_MATRIX' | 'CODE_128' | 'CODE_39' | 'CODE_93' | 'EAN_13' | 'EAN_8' | 'UPC_A' | 'UPC_E' | 'ITF' | 'CODABAR'

const TO_LIB: Record<PassFormat, string> = {
  QR_CODE: 'QRCode', AZTEC: 'Aztec', PDF417: 'PDF417', DATA_MATRIX: 'DataMatrix', CODE_128: 'Code128', CODE_39: 'Code39',
  CODE_93: 'Code93', EAN_13: 'EAN13', EAN_8: 'EAN8', UPC_A: 'UPCA', UPC_E: 'UPCE', ITF: 'ITF', CODABAR: 'Codabar',
}
const FROM_LIB: Record<string, PassFormat> = Object.fromEntries(Object.entries(TO_LIB).map(([k, v]) => [v, k as PassFormat]))

/** 1-D barcodes are wide and short; the others are square-ish. */
export const isLinear = (f: PassFormat) => !['QR_CODE', 'AZTEC', 'PDF417', 'DATA_MATRIX'].includes(f)

export type Scanned = { code: string; format: PassFormat }

let readerReady: Promise<typeof import('zxing-wasm/reader')> | null = null
function reader() {
  readerReady ??= import('zxing-wasm/reader').then((m) => {
    m.prepareZXingModule({ overrides: { locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? readerWasm : prefix + path) } })
    return m
  })
  return readerReady
}

let writerReady: Promise<typeof import('zxing-wasm/writer')> | null = null
function writer() {
  writerReady ??= import('zxing-wasm/writer').then((m) => {
    m.prepareZXingModule({ overrides: { locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? writerWasm : prefix + path) } })
    return m
  })
  return writerReady
}

const OPTIONS = { tryHarder: true, tryInvert: true, maxNumberOfSymbols: 1 } as const

function first(results: { text: string; format: string; isValid: boolean }[]): Scanned | null {
  const r = results.find((x) => x.isValid && x.text && FROM_LIB[x.format])
  return r ? { code: r.text, format: FROM_LIB[r.format] } : null
}

/** A photo or a screenshot (e.g. of an Apple Wallet pass). null = no code found. */
export async function scanImage(file: Blob): Promise<Scanned | null> {
  const m = await reader()
  return first(await m.readBarcodesFromImageFile(file, OPTIONS))
}

/** One camera frame. */
export async function scanFrame(frame: ImageData): Promise<Scanned | null> {
  const m = await reader()
  return first(await m.readBarcodesFromImageData(frame, OPTIONS))
}

/** The code drawn again as SVG (sharp at any size). */
export async function drawCode(code: string, format: PassFormat): Promise<string> {
  const m = await writer()
  const out = await m.writeBarcode(code, { format: TO_LIB[format] as never, scale: 4 })
  if (out.error) throw new Error(out.error)
  // let it scale to its box: give it a viewBox from its own width/height, and drop the fixed size
  return out.svg.replace(/<svg([^>]*)>/, (_m, attrs: string) => {
    const w = /\swidth="([\d.]+)"/.exec(attrs)?.[1], h = /\sheight="([\d.]+)"/.exec(attrs)?.[1]
    let a = attrs.replace(/\s(width|height)="[^"]*"/g, '')
    if (w && h && !/viewBox=/.test(a)) a += ` viewBox="0 0 ${w} ${h}"`
    // barcodes may stretch to fill their box (bars just get taller); 2-D codes keep their square shape
    return `<svg${a} preserveAspectRatio="${isLinear(format) ? 'none' : 'xMidYMid meet'}">`
  })
}

/** The card's main color, guessed from the top of the screenshot (where the logo / header is). */
export async function guessColor(file: Blob): Promise<string> {
  try {
    const bmp = await createImageBitmap(file)
    const c = document.createElement('canvas')
    c.width = 24; c.height = 24
    const g = c.getContext('2d')!
    // the band between 5% and 25% of the height: the pass header, under the phone's status bar
    g.drawImage(bmp, 0, bmp.height * 0.05, bmp.width, bmp.height * 0.2, 0, 0, 24, 24)
    const d = g.getImageData(0, 0, 24, 24).data
    let r = 0, gg = 0, b = 0
    for (let i = 0; i < d.length; i += 4) { r += d[i]; gg += d[i + 1]; b += d[i + 2] }
    const n = d.length / 4
    return '#' + [r / n, gg / n, b / n].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase()
  } catch {
    return '#1000B4'
  }
}
