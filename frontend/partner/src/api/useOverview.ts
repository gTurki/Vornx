import { useQuery } from '@tanstack/react-query'
import { endpoints } from './endpoints'

/** The portal's numbers, shared by the sidebar and the pages (one request, refreshed every minute). */
export function useOverview() {
  return useQuery({ queryKey: ['overview'], queryFn: endpoints.overview, refetchInterval: 60_000 })
}
