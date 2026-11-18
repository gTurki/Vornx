import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { date } from '../lib/format'
import { CARD_STATUS, TIER } from '../lib/labels'
import { ConfirmButton, Kv, Loading, Num, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'
import { mainCard, useCards, VCard } from '../ui/VCard'
import { CARD_SKINS, setCardSkin, useCardSkin } from '../ui/cardSkin'
import { tr } from '../i18n'

/** "بطاقتي": the card itself, its details, loyalty level, and "I lost my card". */
export function CardPage() {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const cards = useCards()
  const profile = useQuery({ queryKey: ['me'], queryFn: endpoints.me })
  const [reveal, setReveal] = useState(false)
  const lost = useMutation({
    mutationFn: endpoints.reportLost,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cards'] }); toast(tr('أوقفنا البطاقة فورًا. لن تفتح أي باب بعد الآن.')) },
    onError: (e) => toast(e instanceof ApiError ? e.message : tr('تعذّر الإبلاغ'), true),
  })
  const card = mainCard(cards.data)
  const p = profile.data
  const tier = p ? TIER[p.tier] : null
  const progress = p && tier?.next ? Math.min(100, Math.round((p.points / tier.next) * 100)) : 100

  if (cards.isLoading) return <Loading />

  return (
    <div className="cols">
      <section>
        <VCard card={card} name={`${me?.firstName} ${me?.lastName}`} reveal={reveal} vornxId={me?.vornxId} flippable />
        <p className="flip-hint">{tr('اضغط على البطاقة لقلبها')}</p>
        <SkinPicker />
        {card && (
          <div className="form-actions" style={{ marginTop: 14 }}>
            <button className="btn gh sm" onClick={() => setReveal(!reveal)}>{reveal ? tr('إخفاء الرقم') : tr('إظهار الرقم كاملًا')}</button>
          </div>
        )}
        {!card && (
          <div className="notice" style={{ marginTop: 16 }}>
            <b>{tr('بطاقتك قيد الإصدار')}</b>
            <p className="mu" style={{ margin: '4px 0 0' }}>{tr('إلى أن تصلك، استخدم جوالك: افتح الأبواب من صفحة التصاريح.')}</p>
          </div>
        )}
      </section>

      <section>
        {card && (
          <>
            <h2>{tr('تفاصيل البطاقة')}</h2>
            <div className="panel">
              <Kv k={tr('الحالة')}><Tag label={CARD_STATUS[card.status]} /></Kv>
              <Kv k={tr('آخر 4 أرقام')}><Num>{card.last4}</Num></Kv>
              <Kv k={tr('تاريخ الإصدار')}>{date(card.issuedAt)}</Kv>
              <Kv k={tr('تنتهي')}>{date(card.expiresAt)}</Kv>
              <Kv k={tr('رقم VORNX')}><Num>{me?.vornxId}</Num></Kv>
              {card.status === 'ACTIVE' && (
                <div className="form-actions" style={{ marginTop: 12 }}>
                  <ConfirmButton className="btn danger" onConfirm={() => lost.mutate(card.id)} disabled={lost.isPending}>{tr('فقدت بطاقتي، أوقفها الآن')}</ConfirmButton>
                </div>
              )}
            </div>
          </>
        )}

        <h2 className="section">{tr('العضوية')}</h2>
        <div className="panel">
          <div className="li" style={{ paddingTop: 0 }}>
            <span className="ent"><small>{tr('مستواك')}</small><b>{tier?.text}</b></span>
            <span className="big">{p?.points.toLocaleString('en-US')}</span>
          </div>
          <div className="bar"><i style={{ width: `${progress}%` }} /></div>
          <p className="mu" style={{ marginBottom: 0 }}>
            {tier?.next ? <>{tr('باقي')}{' '}<Num>{(tier.next - (p?.points ?? 0)).toLocaleString('en-US')}</Num>{' '}{tr('نقطة للمستوى التالي.')}</> : tr('أعلى مستوى. شكرًا لك!')}
          </p>
        </div>
      </section>
    </div>
  )
}

/** "تصميم البطاقة": pick the card's look (4 colors, 4 pictures). Saved on this device; the home card follows. */
function SkinPicker() {
  const skin = useCardSkin()
  return (
    <div className="skins">
      <b>{tr('تصميم البطاقة')}</b>
      <div className="skin-row" role="radiogroup" aria-label={tr('تصميم البطاقة')}>
        {CARD_SKINS.map((s) => (
          <button key={s.id} type="button" role="radio" aria-checked={skin.id === s.id} title={s.name}
                  className={`skin-sw skin-${s.id}${skin.id === s.id ? ' on' : ''}`} onClick={() => setCardSkin(s.id)}>
            <span className="sr">{s.name}</span>
          </button>
        ))}
      </div>
      <small className="mu">{skin.name}</small>
    </div>
  )
}
