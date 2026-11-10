import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { Card } from '../api/types'

/** The customer's cards. The newest active one is "the" card. */
export function useCards() {
  return useQuery({ queryKey: ['cards'], queryFn: endpoints.cards })
}

export function mainCard(cards: Card[] | undefined): Card | null {
  if (!cards?.length) return null
  return cards.find((c) => c.status === 'ACTIVE') ?? cards[0]
}

/** "2027-09-30" -> "09/27" */
function expiry(d: string) {
  return `${d.slice(5, 7)}/${d.slice(2, 4)}`
}

/**
 * The VORNX card, drawn like the physical one.
 * `reveal` shows the full number (card page); elsewhere only the last 4 digits.
 */
export function VCard({ card, name, reveal }: { card: Card | null; name: string; reveal?: boolean }) {
  const pan = card
    ? reveal ? card.cardNumber.replace(/(\d{4})(?=\d)/g, '$1 ') : `•••• •••• •••• ${card.last4}`
    : '•••• •••• •••• ••••'
  return (
    <div className={`vcard${card && card.status !== 'ACTIVE' ? ' off' : ''}`} role="img"
         aria-label={card ? `بطاقة VORNX تنتهي بالرقم ${card.last4}` : 'لا توجد بطاقة بعد'}>
      <div>
        <div className="brand">VORNX<small>ONE</small></div>
        <div className="tag-line">PAY · CAMPUS · WORK · TRANSIT · ACCESS</div>
      </div>
      <div className="pan">{pan}</div>
      <div className="foot">
        <span>{name}</span>
        <span className="num">{card ? expiry(card.expiresAt) : '--/--'}</span>
      </div>
    </div>
  )
}
