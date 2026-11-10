import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refresh, session } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AuthResponse, Customer, LoginChallenge } from '../api/types'

type AuthState = {
  status: 'loading' | 'in' | 'out'
  me: Customer | null
  /** Step 1. Returns the challenge when a code was sent (finish with loginWith(verifyCode(...))), null when logged in. */
  login: (email: string, password: string) => Promise<LoginChallenge | null>
  /** After sign-up the API already returns tokens: log straight in. */
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
  const [me, setMe] = useState<Customer | null>(null)

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
    const r = await endpoints.login(email, password)
    if ('challengeToken' in r) return r
    await loginWith(r)
    return null
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

