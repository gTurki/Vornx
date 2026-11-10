import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useToast } from '../ui/Toast'
import { connectRealtime } from './realtime'
import type { AppNotification } from './types'

/**
 * Live updates while logged in. Mounted once, in the layout.
 * The server only says "something happened"; we then re-load from the REST API, which stays the
 * single source of truth (and checks permissions).
 */
export function useRealtime() {
  const qc = useQueryClient()
  const toast = useToast()
  useEffect(
    () =>
      connectRealtime((m) => {
        if (m.type === 'notification') {
          toast('🔔 ' + (m.data as AppNotification).titleAr)
          qc.invalidateQueries() // the thing the notification is about changed too: reload what's on screen
        } else if (m.type === 'access-event') {
          qc.invalidateQueries({ queryKey: ['access-events'] })
        } else if (m.type === 'resync') {
          qc.invalidateQueries() // we were offline for a while: reload everything on screen
        }
      }),
    [qc, toast],
  )
}
