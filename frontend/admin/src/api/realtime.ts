import { API_URL, refresh, session } from './client'

export type RealtimeMessage = { type: string; data?: unknown }

/**
 * One WebSocket to the API (wss://<api>/ws) for live updates: new notifications, door activity...
 *
 * - Logs in with the first message ({"type":"auth","token":...}), never in the URL.
 * - The server closes it when the access token expires (code 4401): we refresh the token and reconnect.
 * - If the connection drops (Wi-Fi, server restart), we retry with growing pauses (1 s, 2 s, 4 s ... 30 s)
 *   and, once back, send {"type":"resync"} so the app reloads what it may have missed.
 * - A ping every 25 s keeps proxies (Railway) from closing an idle connection.
 *
 * Returns a function that closes it for good (on logout / leaving the app).
 */
export function connectRealtime(onMessage: (m: RealtimeMessage) => void): () => void {
  const url = API_URL.replace(/^http/, 'ws') + '/ws' // https -> wss, http -> ws
  let ws: WebSocket | null = null
  let stopped = false
  let attempt = 0
  let wasConnected = false
  let ping: number | undefined
  let timer: number | undefined

  const schedule = () => {
    if (stopped || !session.hasRefreshToken()) return
    const delay = Math.min(30_000, 1000 * 2 ** attempt++) + Math.random() * 500
    timer = window.setTimeout(open, delay)
  }

  async function open() {
    if (stopped) return
    let token = session.accessToken()
    if (!token) {
      if (!(await refresh())) return schedule()
      token = session.accessToken()
    }
    const socket = new WebSocket(url)
    ws = socket
    socket.onopen = () => socket.send(JSON.stringify({ type: 'auth', token }))
    socket.onmessage = (e) => {
      let msg: RealtimeMessage
      try { msg = JSON.parse(e.data) } catch { return }
      if (msg.type === 'ready') {
        attempt = 0
        if (wasConnected) onMessage({ type: 'resync' })
        wasConnected = true
        window.clearInterval(ping)
        ping = window.setInterval(() => socket.readyState === WebSocket.OPEN && socket.send('{"type":"ping"}'), 25_000)
      } else if (msg.type !== 'pong') {
        onMessage(msg)
      }
    }
    socket.onclose = async (e) => {
      window.clearInterval(ping)
      if (ws === socket) ws = null
      if (stopped) return
      if (e.code === 4401) await refresh() // token expired: get a fresh one first
      schedule()
    }
  }

  open()
  return () => {
    stopped = true
    window.clearTimeout(timer)
    window.clearInterval(ping)
    ws?.close(1000)
  }
}
