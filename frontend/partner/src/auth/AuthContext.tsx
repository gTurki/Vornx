import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refresh, session } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AuthResponse, PartnerRole, PartnerUser } from '../api/types'

type AuthState = {
  status: 'loading' | 'in' | 'out'
  me: PartnerUser | null
  login: (email: string, password: string) => Promise<void>
  /** After accepting an invite the API already returns tokens: log straight in. */
  loginWith: (auth: AuthResponse) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

/**
 * Who is logged in, for the whole app.
 * On page load: if a refresh token is saved, get a fresh access token and load /me. Otherwise -> login page.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthState['status']>('loading')
  const [me, setMe] = useState<PartnerUser | null>(null)

  const end = useCallback(() => {
    session.clear()
    setMe(null)
    setStatus('out')
    queryClient.clear() // never show the previous user's data to the next one
  }, [queryClient])

  useEffect(() => {
    session.onEnded(end)
    ;(async () => {
      if (session.hasRefreshToken() && (await refresh())) {
        try {
          setMe(await endpoints.me())
          setStatus('in')
          return
        } catch {
          /* fall through */
        }
      }
      setStatus('out')
    })()
  }, [end])

  const loginWith = useCallback(async (auth: AuthResponse) => {
    session.save(auth)
    setMe(await endpoints.me())
    setStatus('in')
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    await loginWith(await endpoints.login(email, password))
  }, [loginWith])

  const logout = useCallback(async () => {
    const rt = session.refreshToken()
    if (rt) await endpoints.logout(rt).catch(() => undefined) // end the session on the server too
    end()
  }, [end])

  const value = useMemo(() => ({ status, me, login, loginWith, logout }), [status, me, login, loginWith, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}

/**
 * What the logged-in role may do. These mirror the backend's @PreAuthorize rules, and are only used to
 * HIDE buttons. The backend still checks every request: hiding a button is not security.
 */
const ROLES: Record<'admin' | 'edit' | 'operate', PartnerRole[]> = {
  admin: ['PARTNER_ADMIN'],                          // team, device keys, contract renewal
  edit: ['PARTNER_ADMIN', 'MANAGER'],                // sites, access points, plans, events
  operate: ['PARTNER_ADMIN', 'MANAGER', 'FRONT_DESK'], // permits, requests, tickets, room status, support
}

export function useCan() {
  const { me } = useAuth()
  return (what: keyof typeof ROLES) => !!me && ROLES[what].includes(me.role)
}
