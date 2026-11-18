// The isometric night scenes at the top of the pages (from the VORNX design, index_76): a small 3D world drawn as
// SVG, floating gently, with glowing windows, pulsing signals, twinkling stars and a label.
// Pure drawing code: it only builds an SVG string from fixed shapes, so it is safe to inject.

type Pt = [number, number, number]
type Shade = [string, string, string] // top, left side, right side

const OX = 180, OY = 186, K = 0.866
let mark: string[] | null = null // where the dashed "you are here" line points (some scenes set it)

const N = (a: number) => a.toFixed(1)
const p = (x: number, y: number, z: number) => N(OX + (x - y) * K) + ',' + N(OY + (x + y) * 0.5 - z)
const pg = (a: Pt[], f: string, e = '') => `<polygon points="${a.map((q) => p(q[0], q[1], q[2])).join(' ')}" fill="${f}"${e}/>`
const H = (h: number, s: number, l: number): Shade => [`hsl(${h} ${s}% ${l + 14}%)`, `hsl(${h} ${s}% ${l}%)`, `hsl(${h} ${s}% ${l - 11}%)`]
const bx = (x: number, y: number, z: number, w: number, d: number, h: number, c: Shade) =>
  pg([[x + w, y, z], [x + w, y + d, z], [x + w, y + d, z + h], [x + w, y, z + h]], c[2]) +
  pg([[x, y + d, z], [x + w, y + d, z], [x + w, y + d, z + h], [x, y + d, z + h]], c[1]) +
  pg([[x, y, z + h], [x + w, y, z + h], [x + w, y + d, z + h], [x, y + d, z + h]], c[0])
const cls = (k?: string) => (k ? ` class="${k}"` : '')
const wL = (x: number, Y: number, z: number, w: number, h: number, f: string, k?: string) =>
  pg([[x, Y, z], [x + w, Y, z], [x + w, Y, z + h], [x, Y, z + h]], f, cls(k))
const wR = (X: number, y: number, z: number, w: number, h: number, f: string, k?: string) =>
  pg([[X, y, z], [X, y + w, z], [X, y + w, z + h], [X, y, z + h]], f, cls(k))
const WARM = 'rgba(255,214,140,.55)', DIM = 'rgba(150,185,255,.17)'

function win(x: number, y: number, w: number, d: number, z0: number, z1: number, cl: number, cr: number, seed: number) {
  let s = ''
  const rows = Math.round((z1 - z0) / 12), ch = (z1 - z0) / rows, hh = ch * 0.56
  for (let r = 0; r < rows; r++) {
    const z = z0 + r * ch + ch * 0.22
    for (let c = 0; c < cl; c++) {
      const cw = w / cl, lit = (r * 7 + c * 5 + seed) % 6 === 0 || (r * 3 + c * 11 + seed) % 7 === 0
      s += wL(x + c * cw + cw * 0.2, y + d, z, cw * 0.6, hh, lit ? WARM : DIM)
    }
    for (let c = 0; c < cr; c++) {
      const cw = d / cr, lit = (r * 5 + c * 3 + seed) % 5 === 0
      s += wR(x + w, y + c * cw + cw * 0.2, z, cw * 0.6, hh, lit ? WARM : DIM)
    }
  }
  return s
}
const ell = (x: number, y: number, z: number, r: number, k: string, delay = '') => {
  const q = p(x, y, z).split(',')
  return `<ellipse cx="${q[0]}" cy="${q[1]}" rx="${r}" ry="${r * 0.5}" fill="none" stroke="#42C8F5" stroke-width="1.6" class="${k}"${delay ? ` style="animation-delay:${delay}"` : ''}/>`
}
const plat = () => bx(-88, -88, -10, 176, 176, 10, ['#26386d', '#1a2750', '#121c3b']) + pg([[-82, -82, 0], [82, -82, 0], [82, 82, 0], [-82, 82, 0]], 'rgba(120,160,255,.07)')
const esc = (t: string) => t.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))
function badge(t: string, tg: string[] | null) {
  const w = t.length * 7.4 + 30
  let s = ''
  if (tg) s += `<path d="M28 42L${tg[0]} ${tg[1]}" stroke="#FFD98A" stroke-width="1.2" stroke-dasharray="3 3" fill="none"/><circle cx="${tg[0]}" cy="${tg[1]}" r="3.2" fill="#FFD98A" class="lit"/>`
  return s + `<rect x="12" y="14" width="${N(w)}" height="28" rx="14" fill="rgba(8,15,44,.85)" stroke="rgba(158,223,255,.5)"/><text x="${N(12 + w / 2)}" y="32.5" text-anchor="middle" fill="#fff" font-size="12.5" font-weight="700" font-family="IBM Plex Sans Arabic, system-ui, sans-serif">${esc(t)}</text>`
}

/** One scene per page (the design's shapes, unchanged). */
const SCENES = {
  perm: () => {
    let s = ''
    for (let i = 0; i < 3; i++) s += bx(-52, -34, 26 + i * 16, 104, 66, 5, H(215 + i * 12, 55, 42 + i * 5))
    return s + pg([[-40, -24, 57], [-22, -24, 57], [-22, -10, 57], [-40, -10, 57]], '#FFD98A', ' class="lit"') + pg([[-10, -6, 57], [44, -6, 57], [44, -2, 57], [-10, -2, 57]], 'rgba(255,255,255,.55)')
  },
  ev: () => {
    const s = bx(-62, -34, 0, 124, 68, 40, H(260, 35, 34)) + bx(-44, -22, 40, 88, 44, 14, H(260, 35, 40)) + wL(-18, 34, 0, 36, 26, 'rgba(255,214,140,.7)', 'lit') +
      wL(-50, 34, 28, 100, 9, 'rgba(66,200,245,.8)', 'litc') + wL(-54, 34, 10, 26, 14, WARM) + wL(28, 34, 10, 26, 14, WARM) + bx(-24, 36, 0, 5, 5, 34, H(0, 0, 80)) + bx(20, 36, 0, 5, 5, 34, H(0, 0, 80))
    mark = p(0, 34, 13).split(',')
    return s
  },
  wal: () => {
    let s = ''
    for (let i = 0; i < 4; i++) s += bx(-48 + i * 7, -30 + i * 3, 24 + i * 12, 96, 62, 6, H(190 + i * 10, 60, 40 + i * 4))
    return s + pg([[-14, -16, 70], [8, -16, 70], [8, -2, 70], [-14, -2, 70]], '#FFD98A', ' class="lit"')
  },
  subs: () => {
    let s = bx(-84, -62, 0, 168, 124, 8, ['#26386d', '#1a2750', '#121c3b'])
    const col = [[160, 55], [215, 62], [262, 50], [38, 82], [345, 62]], fr = [0.9, 0.7, 0.5, 0.2, 0.8], cc = ['#4ADE80', '#42C8F5', '#A78BFA', '#FFD98A', '#FB7185']
    s += ell(0, 0, 8, 64, 'sig') + ell(0, 0, 8, 92, 'sig', '.9s')
    for (let i = 0; i < 5; i++) {
      const x = -64 + i * 26, y = -44 + i * 18, h = 52 + (i % 2) * 8, c = H(col[i][0], col[i][1], i === 3 ? 52 : 44)
      s += bx(x, y, 8, 24, 5, h, c) + wL(x + 3, y + 5, 8 + h - 12, 8, 6, 'rgba(255,255,255,.85)') + wL(x + 13, y + 5, 8 + h - 11, 8, 2, 'rgba(255,255,255,.45)') +
        wL(x + 3, y + 5, 8 + h - 24, 16, 2, 'rgba(255,255,255,.3)') + wL(x + 3, y + 5, 15, 18, 4, 'rgba(6,12,40,.55)') + wL(x + 3, y + 5, 15, 18 * fr[i], 4, cc[i], i === 3 ? 'lit' : 'litc')
    }
    return s + ell(26, 6, 110, 24, 'sig') + `<polyline points="${p(14, 6, 110)} ${p(24, 6, 118)} ${p(40, 6, 100)}" stroke="#FFD98A" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round" class="lit"/>`
  },
  city: () =>
    bx(-78, -52, 0, 52, 48, 40, H(260, 35, 34)) + win(-78, -52, 52, 48, 4, 38, 3, 3, 1) + bx(-14, -58, 0, 40, 44, 110, H(222, 38, 36)) + win(-14, -58, 40, 44, 8, 108, 4, 4, 3) +
    bx(34, -40, 0, 44, 40, 66, H(200, 50, 40)) + win(34, -40, 44, 40, 6, 64, 4, 3, 5) + bx(-60, 10, 0, 50, 36, 22, H(220, 30, 66)) + bx(10, 24, 0, 60, 40, 12, H(215, 60, 50)) +
    wL(18, 64, 2, 44, 8, 'rgba(66,200,245,.5)', 'litc'),
  rep: () => {
    const h = [30, 50, 42, 70, 58, 96]
    let s = ''
    for (let i = 0; i < 6; i++) s += bx(-72 + i * 26, -12, 0, 18, 24, h[i], i === 5 ? H(190, 80, 50) : H(222, 40, 40))
    return s
  },
  sites: () => {
    let s = bx(-80, -60, 0, 160, 120, 6, H(200, 35, 32))
    for (let i = -3; i <= 3; i++) s += `<polyline points="${p(i * 24, -60, 6.2)} ${p(i * 24, 60, 6.2)}" stroke="rgba(255,255,255,.14)" fill="none"/>`
    s += pg([[-80, -6, 6.3], [80, -6, 6.3], [80, 6, 6.3], [-80, 6, 6.3]], 'rgba(255,255,255,.12)')
    for (const q of [[-40, -20], [30, -34], [10, 34]]) {
      const t = p(q[0], q[1], 34).split(',')
      s += ell(q[0], q[1], 6.4, 12, 'sig') + bx(q[0] - 2, q[1] - 2, 6, 4, 4, 24, H(0, 0, 80)) + `<circle cx="${t[0]}" cy="${t[1]}" r="8" fill="#42C8F5" class="litc"/>`
    }
    return s
  },
  offers: () =>
    bx(-32, -32, 0, 64, 64, 44, H(335, 60, 48)) + bx(-36, -36, 44, 72, 72, 12, H(335, 60, 55)) + wL(-6, 32, 0, 12, 44, '#FFD98A') + wR(32, -6, 0, 12, 44, '#E6B85A') +
    pg([[-6, -36, 56], [6, -36, 56], [6, 36, 56], [-6, 36, 56]], '#FFD98A') + pg([[-36, -6, 56], [36, -6, 56], [36, 6, 56], [-36, 6, 56]], '#FFD98A'),
}
export type IsoScene = keyof typeof SCENES

function build(kind: IsoScene, label: string) {
  mark = null
  const scene = SCENES[kind]()
  const tg = mark
  mark = null
  let stars = ''
  for (let i = 0; i < 16; i++) stars += `<circle cx="${N((i * 83) % 350 + 6)}" cy="${N((i * 47) % 70 + 8)}" r="${i % 3 ? 0.7 : 1.1}" fill="#fff" class="tw" style="animation-delay:${(i % 5) * 0.6}s"/>`
  return `<svg viewBox="0 0 360 290" aria-hidden="true">${stars}<g class="s3f">${plat()}${scene}${badge(label, tg)}</g></svg>`
}

/** The page's isometric scene, e.g. <IsoHero kind="subs" label={tr('اشتراكاتك')} />. */
export function IsoHero({ kind, label }: { kind: IsoScene; label: string }) {
  return <div className="s3" role="img" aria-label={label} dangerouslySetInnerHTML={{ __html: build(kind, label) }} />
}
