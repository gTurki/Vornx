import { isEn, setLang } from '../i18n'

/** "English" / "العربية": switches the app's language (saved on this device, then the page reloads). */
export function LangSwitch({ className = 'lang' }: { className?: string }) {
  return (
    <button type="button" className={className} lang={isEn ? 'ar' : 'en'} onClick={() => setLang(isEn ? 'ar' : 'en')}>
      {isEn ? 'العربية' : 'English'}
    </button>
  )
}
