import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from '../ui/Logo'
import { isEn } from '../i18n'
import { LangSwitch } from '../ui/LangSwitch'

/**
 * Terms of use + privacy policy (public pages: /terms, /privacy).
 * Each sign-up stores the version it accepted: when these texts change, change TERMS_VERSION here AND
 * com.example.vornx.customer.Legal.TERMS_VERSION in the backend.
 */
export const TERMS_VERSION = '2026-11-17'
const UPDATED = isEn ? '17 November 2026' : '17 نوفمبر 2026'
const EMAIL = 'vornx@outlook.sa'
const PHONE = '053 409 8244'

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="legal-page">
      <article className="legal">
        <LangSwitch />
        <div className="logo"><Logo variant="mark" /></div>
        <h1>{title}</h1>
        <p className="mu">{isEn ? 'Last updated:' : 'آخر تحديث:'} {UPDATED} · {isEn ? 'version' : 'الإصدار'} <span dir="ltr">{TERMS_VERSION}</span></p>
        {children}
        <hr />
        <p className="mu">
          {isEn ? 'Contact:' : 'للتواصل:'} <a href={`mailto:${EMAIL}`} dir="ltr">{EMAIL}</a> · <a href="tel:+966534098244" dir="ltr">{PHONE}</a>
        </p>
        <p className="legal-nav">
          {isEn
            ? <><Link to="/terms">Terms of use</Link> · <Link to="/privacy">Privacy policy</Link> · <Link to="/">Back to VORNX</Link></>
            : <><Link to="/terms">شروط الاستخدام</Link> · <Link to="/privacy">سياسة الخصوصية</Link> · <Link to="/">العودة إلى VORNX</Link></>}
        </p>
      </article>
    </div>
  )
}

/** The page in the app's language. */
export function TermsPage() {
  return isEn ? <TermsEn /> : <TermsAr />
}

export function PrivacyPage() {
  return isEn ? <PrivacyEn /> : <PrivacyAr />
}

function TermsAr() {
  return (
    <LegalPage title="شروط الاستخدام">
      <p>
        مرحبًا بك في VORNX للتقنية والأنظمة الذكية («VORNX» أو «نحن»). تنظّم هذه الشروط استخدامك لتطبيق VORNX وبطاقة VORNX
        والخدمات المرتبطة بهما («الخدمة»). بإنشاء حساب أو استخدام الخدمة فإنك توافق على هذه الشروط وعلى
        {' '}<Link to="/privacy">سياسة الخصوصية</Link>. إذا لم توافق عليها فلا تستخدم الخدمة.
      </p>

      <h2>1. الخدمة</h2>
      <p>
        VORNX منصة تقنية تجمع في بطاقة واحدة وفي تطبيقك صلاحيات دخول وخدمات تقدّمها جهات شريكة، مثل الفنادق والأندية
        وجهات العمل والمواقف والفعاليات. VORNX توفّر التقنية فقط: الجهة الشريكة هي من تصدر التصريح أو الاشتراك أو التذكرة،
        وهي المسؤولة عن خدمتها وأسعارها وشروطها الخاصة وقرار قبولك أو رفضك.
      </p>

      <h2>2. حسابك</h2>
      <ul>
        <li>يجب أن تكون قد بلغت السن النظامية لإبرام هذه الشروط، أو أن تستخدم الخدمة بموافقة وليّ أمرك.</li>
        <li>تقدّم معلومات صحيحة ومحدّثة باسمك، وتؤكّد بريدك الإلكتروني ورقم جوالك بالرموز التي نرسلها.</li>
        <li>الحساب شخصي ولا يجوز نقله أو مشاركته. أنت مسؤول عن سرية كلمة المرور ورموز الدخول ورموز الاسترداد.</li>
        <li>ننصحك بتفعيل تطبيق المصادقة من «حسابي». لن يطلب منك موظفو VORNX كلمة المرور أو أي رمز أبدًا.</li>
        <li>أبلغنا فورًا عند الاشتباه في استخدام حسابك دون إذنك.</li>
      </ul>

      <h2>3. البطاقة والتصاريح</h2>
      <ul>
        <li>بطاقة VORNX وتصاريحك الرقمية لاستخدامك الشخصي فقط، ولا يجوز إعارتها أو نسخها أو التلاعب بها.</li>
        <li>عند فقدان البطاقة أو الجوال أبلغ عنه من التطبيق فورًا لإيقافه. لا نتحمّل الاستخدام الذي يسبق إبلاغك.</li>
        <li>يُسجَّل كل دخول أو محاولة دخول (المكان والوقت والقرار) لأغراض الأمان، ويطّلع عليه الشريك صاحب المكان.</li>
        <li>للشريك أن يوقف تصريحًا أصدره أو يلغيه وفق شروطه.</li>
      </ul>

      <h2>4. المحفظة والمدفوعات</h2>
      <ul>
        <li>رصيد المحفظة بالريال السعودي، ويُستخدم داخل VORNX للدفع للاشتراكات والفعاليات والتحويل لعملاء VORNX الآخرين.
          الرصيد ليس حسابًا بنكيًا ولا يستحق أي عائد.</li>
        <li>يتم شحن المحفظة بالبطاقة عبر بوابة الدفع المرخّصة «ميسّر» (Moyasar). لا تمر بيانات بطاقتك البنكية على خوادم VORNX.</li>
        <li>يُضاف الرصيد فقط بعد أن نتحقق من نجاح الدفع لدى بوابة الدفع.</li>
        <li>راجع المبلغ والمستلم قبل أي تحويل: التحويلات المكتملة لا يمكن التراجع عنها من التطبيق.</li>
        <li>طلبات الاسترداد أو الاعتراض على عملية تُقدَّم عبر الدعم، وتُعالج وفق الأنظمة المعمول بها في المملكة. استرداد قيمة
          اشتراك أو تذكرة يخضع لشروط الشريك الذي يقدّمها.</li>
      </ul>

      <h2>5. الاستخدام الممنوع</h2>
      <p>لا يجوز لك:</p>
      <ul>
        <li>محاولة الدخول إلى أماكن أو حسابات أو بيانات ليست لك، أو تجاوز أي حماية أو حدّ في الخدمة.</li>
        <li>استخدام الخدمة لأي غرض مخالف للأنظمة، أو للاحتيال أو غسل الأموال.</li>
        <li>إرسال طلبات آلية مكثّفة، أو تعطيل الخدمة، أو الهندسة العكسية لها.</li>
        <li>انتحال شخصية غيرك أو التسجيل ببيانات غيرك.</li>
      </ul>

      <h2>6. الإيقاف والإنهاء</h2>
      <p>
        يحق لنا إيقاف حسابك أو تقييده عند مخالفة هذه الشروط، أو الاشتباه في احتيال، أو بطلب من جهة مختصة. يمكنك طلب إغلاق
        حسابك في أي وقت عبر الدعم؛ ونعالج حينها رصيدك المتبقي وفق الأنظمة، ونحتفظ بما يلزم نظامًا من السجلات.
      </p>

      <h2>7. المسؤولية</h2>
      <p>
        نبذل عناية معقولة لتكون الخدمة متاحة وآمنة، لكنها تُقدَّم «كما هي» وقد تتوقف مؤقتًا للصيانة أو لأسباب خارجة عن
        إرادتنا (مثل انقطاع الشبكة أو أعطال الأجهزة لدى الشريك). في حدود ما تسمح به الأنظمة، لا نتحمّل الأضرار غير المباشرة،
        ولا مسؤولية خدمات الشركاء أنفسهم. لا يُقيّد ذلك أي حق لا يجوز تقييده نظامًا.
      </p>

      <h2>8. التغييرات على الشروط</h2>
      <p>
        قد نحدّث هذه الشروط. عند تغيير جوهري نُعلمك في التطبيق أو بالبريد قبل سريانه، واستمرارك في الاستخدام بعد ذلك يعني
        موافقتك على الشروط المحدّثة.
      </p>

      <h2>9. النظام الواجب التطبيق</h2>
      <p>
        تخضع هذه الشروط لأنظمة المملكة العربية السعودية، وتختص محاكمها بأي نزاع ينشأ عنها. نسعى أولًا لحل أي خلاف وديًا
        عبر التواصل معنا.
      </p>
    </LegalPage>
  )
}

function PrivacyAr() {
  return (
    <LegalPage title="سياسة الخصوصية">
      <p>
        نحترم خصوصيتك ونحمي بياناتك الشخصية وفق نظام حماية البيانات الشخصية في المملكة العربية السعودية ولوائحه. توضّح هذه
        السياسة ما نجمعه عنك عند استخدام تطبيق VORNX وبطاقة VORNX، ولماذا، ومع من نشاركه، وحقوقك. جهة التحكم في بياناتك هي
        VORNX للتقنية والأنظمة الذكية.
      </p>

      <h2>1. البيانات التي نجمعها</h2>
      <ul>
        <li><b>بيانات الحساب:</b> الاسم، البريد الإلكتروني، رقم الجوال، اللغة، ورقم VORNX. كلمة المرور لا نحفظها أبدًا، بل
          بصمة مشفّرة منها فقط.</li>
        <li><b>بيانات التحقق:</b> وقت تأكيد بريدك وجوالك، ونسخة الشروط التي وافقت عليها ووقتها. رموز التحقق نحفظ بصمتها فقط
          وتُحذف خلال يوم.</li>
        <li><b>تطبيق المصادقة (إن فعّلته):</b> المفتاح السري للتطبيق محفوظًا مشفّرًا، وبصمات رموز الاسترداد.</li>
        <li><b>البطاقة والتصاريح والدخول:</b> بطاقتك، تصاريحك واشتراكاتك وتذاكرك، وسجل الدخول: المكان والباب والوقت وقرار
          السماح أو الرفض.</li>
        <li><b>المحفظة:</b> الرصيد، والعمليات (شحن، دفع، تحويل) ومبالغها وأطرافها، ورقم الفاتورة لدى بوابة الدفع. لا نحفظ
          رقم بطاقتك البنكية ولا نراه.</li>
        <li><b>الأجهزة والجلسات:</b> عنوان IP ونوع المتصفح أو الجهاز ووقت آخر استخدام لكل جلسة دخول، لحمايتك ولتتمكن من
          تسجيل الخروج من أي جهاز.</li>
        <li><b>الدعم والإشعارات:</b> رسائلك إلينا والإشعارات التي نرسلها لك.</li>
        <li><b>التحقق من الهوية (لاحقًا، عند طلبك خدمة تتطلبه فقط):</b> رقم الهوية الوطنية أو الإقامة.</li>
      </ul>

      <h2>2. لماذا نستخدمها</h2>
      <ul>
        <li>تقديم الخدمة: فتح الأبواب، إصدار التصاريح والاشتراكات والتذاكر، وتشغيل المحفظة.</li>
        <li>حماية حسابك: التحقق من البريد والجوال، رموز الدخول، اكتشاف الاحتيال وإساءة الاستخدام.</li>
        <li>التواصل معك: رموز التحقق، الإيصالات، تنبيهات الأمان، والرد على الدعم.</li>
        <li>الالتزام بالأنظمة، مثل حفظ السجلات المالية والاستجابة للجهات المختصة.</li>
      </ul>
      <p>الأساس النظامي: تنفيذ العقد بيننا (هذه الخدمة)، وموافقتك، والتزاماتنا النظامية، ومصلحتنا المشروعة في أمن الخدمة.</p>
      <p>لا نبيع بياناتك، ولا نستخدمها للإعلانات.</p>

      <h2>3. مع من نشاركها</h2>
      <ul>
        <li><b>الشركاء:</b> الجهة التي تصدر لك تصريحًا أو اشتراكًا أو تذكرة ترى اسمك ورقم VORNX وتصريحك وسجل دخولك إلى
          أماكنها فقط، لا بقية بياناتك.</li>
        <li><b>مزوّدو الخدمة الذين يعملون لصالحنا وفق عقود تحمي بياناتك:</b>
          <ul>
            <li>الاستضافة وقاعدة البيانات: Railway.</li>
            <li>إرسال البريد الإلكتروني: Brevo.</li>
            <li>رسائل واتساب لتأكيد الجوال: UltraMsg عبر WhatsApp.</li>
            <li>الدفع بالبطاقة: ميسّر (Moyasar).</li>
            <li>رموز الرسائل النصية (إن استُخدمت): Google Firebase.</li>
          </ul>
        </li>
        <li><b>الجهات المختصة:</b> عندما يلزمنا النظام بذلك.</li>
      </ul>

      <h2>4. نقل البيانات خارج المملكة</h2>
      <p>
        قد تُعالج بعض بياناتك أو تُحفظ لدى مزوّدينا خارج المملكة العربية السعودية (مثل خوادم الاستضافة). لا نفعل ذلك إلا
        وفق ما يسمح به نظام حماية البيانات الشخصية ولوائحه، ومع ضمانات مناسبة لحمايتها.
      </p>

      <h2>5. مدة الاحتفاظ</h2>
      <ul>
        <li>بيانات الحساب والبطاقة والتصاريح: طوال بقاء حسابك.</li>
        <li>السجلات المالية: المدة التي توجبها الأنظمة.</li>
        <li>سجلات الدخول والجلسات: ما دامت لازمة للأمان ومعالجة الاعتراضات.</li>
        <li>رموز التحقق: تُحذف تلقائيًا خلال يوم.</li>
      </ul>
      <p>بعد إغلاق حسابك نحذف بياناتك أو نُجهّلها، إلا ما يلزمنا النظام بحفظه.</p>

      <h2>6. حماية بياناتك</h2>
      <p>
        نستخدم اتصالًا مشفّرًا (HTTPS)، وكلمات مرور ورموزًا محفوظة كبصمات فقط، وتشفيرًا لمفاتيح تطبيق المصادقة، وصلاحيات
        تمنع أي عميل أو شريك من رؤية ما ليس له، وسجلًا لنشاط فريقنا. لا يوجد نظام آمن تمامًا؛ لذلك سنُبلغك والجهات المختصة
        دون تأخير إذا وقع تسرّب يمسّ بياناتك، وفق ما يتطلبه النظام.
      </p>

      <h2>7. حقوقك</h2>
      <p>وفق نظام حماية البيانات الشخصية يحق لك:</p>
      <ul>
        <li>أن تعرف كيف نعالج بياناتك (هذه السياسة).</li>
        <li>الاطلاع على بياناتك والحصول على نسخة منها.</li>
        <li>تصحيح بياناتك غير الصحيحة أو الناقصة.</li>
        <li>طلب حذف بياناتك عندما لا تعود لازمة، إلا ما يلزمنا النظام بحفظه.</li>
        <li>سحب موافقتك متى ما كانت هي الأساس، دون أن يؤثر ذلك على ما سبقه.</li>
      </ul>
      <p>
        راسلنا على <a href={`mailto:${EMAIL}`} dir="ltr">{EMAIL}</a> وسنرد خلال المدة النظامية. يحق لك أيضًا تقديم شكوى إلى
        الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا).
      </p>

      <h2>8. ملفات تعريف الارتباط والتخزين</h2>
      <p>
        لا نستخدم ملفات تعريف ارتباط للإعلانات أو التتبّع. يحفظ التطبيق في متصفحك رمز الجلسة لإبقائك مسجّل الدخول، واختيارك
        للمظهر (فاتح أو داكن) واللغة. عند تسجيل الخروج يُحذف رمز الجلسة.
      </p>

      <h2>9. الأطفال</h2>
      <p>الخدمة ليست موجّهة لمن هم دون السن النظامية دون موافقة وليّ الأمر، ولا نجمع بياناتهم عن علم دون هذه الموافقة.</p>

      <h2>10. التغييرات</h2>
      <p>عند تغيير جوهري في هذه السياسة نُعلمك في التطبيق أو بالبريد، ونحدّث تاريخ «آخر تحديث» أعلاه.</p>
    </LegalPage>
  )
}

// ---------------------------------------------------------------------------------------------------------------
// English versions. They say exactly what the Arabic texts say; if the two ever differ, update both together.
// ---------------------------------------------------------------------------------------------------------------

function TermsEn() {
  return (
    <LegalPage title="Terms of use">
      <p>
        Welcome to VORNX for Technology &amp; Smart Systems ("VORNX", "we"). These terms govern your use of the VORNX app,
        the VORNX card and the related services (the "Service"). By creating an account or using the Service you accept
        these terms and the <Link to="/privacy">privacy policy</Link>. If you don't accept them, don't use the Service.
      </p>

      <h2>1. The Service</h2>
      <p>
        VORNX is a technology platform that brings, into one card and your app, access permissions and services offered
        by partners such as hotels, clubs, workplaces, parking and events. VORNX provides the technology only: the partner
        issues the permit, subscription or ticket, and is responsible for its own service, prices, terms and the decision
        to accept or refuse you.
      </p>

      <h2>2. Your account</h2>
      <ul>
        <li>You must be of legal age to accept these terms, or use the Service with your guardian's consent.</li>
        <li>Give true, up-to-date information in your own name, and confirm your email and mobile with the codes we send.</li>
        <li>The account is personal and may not be transferred or shared. You are responsible for keeping your password,
          login codes and recovery codes secret.</li>
        <li>We recommend turning on the authenticator app in "My account". VORNX staff will never ask for your password or any code.</li>
        <li>Tell us immediately if you suspect someone used your account without permission.</li>
      </ul>

      <h2>3. The card and permits</h2>
      <ul>
        <li>The VORNX card and your digital permits are for your personal use only, and may not be lent, copied or tampered with.</li>
        <li>If you lose the card or your phone, report it in the app immediately to block it. We are not responsible for
          use before you report it.</li>
        <li>Every entry or entry attempt (place, time and decision) is recorded for security, and the partner who owns the place can see it.</li>
        <li>A partner may suspend or cancel a permit it issued, under its own terms.</li>
      </ul>

      <h2>4. Wallet and payments</h2>
      <ul>
        <li>The wallet balance is in Saudi riyals and is used inside VORNX to pay for subscriptions and events and to transfer
          to other VORNX customers. The balance is not a bank account and earns no return.</li>
        <li>The wallet is topped up by card through the licensed payment gateway Moyasar. Your bank card details never pass
          through VORNX's servers.</li>
        <li>The balance is added only after we confirm with the payment gateway that the payment succeeded.</li>
        <li>Check the amount and the recipient before any transfer: completed transfers can't be undone from the app.</li>
        <li>Refund requests or disputes go through support and are handled under the laws in force in Saudi Arabia. Refunds of
          a subscription or a ticket follow the terms of the partner who offers it.</li>
      </ul>

      <h2>5. Prohibited use</h2>
      <p>You may not:</p>
      <ul>
        <li>try to access places, accounts or data that aren't yours, or get around any protection or limit of the Service;</li>
        <li>use the Service for anything against the law, or for fraud or money laundering;</li>
        <li>send heavy automated requests, disrupt the Service, or reverse-engineer it;</li>
        <li>impersonate someone else or register with someone else's details.</li>
      </ul>

      <h2>6. Suspension and closing</h2>
      <p>
        We may suspend or restrict your account if these terms are broken, if fraud is suspected, or at the request of a
        competent authority. You can ask to close your account at any time through support; we then handle your remaining
        balance under the law, and keep the records the law requires.
      </p>

      <h2>7. Liability</h2>
      <p>
        We take reasonable care to keep the Service available and secure, but it is provided "as is" and may stop for a
        while for maintenance or for reasons beyond our control (such as network outages or a partner's device faults). As
        far as the law allows, we are not liable for indirect damage, or for the partners' own services. This does not limit
        any right that the law does not allow to be limited.
      </p>

      <h2>8. Changes to these terms</h2>
      <p>
        We may update these terms. For a material change we tell you in the app or by email before it takes effect, and
        continuing to use the Service after that means you accept the updated terms.
      </p>

      <h2>9. Governing law</h2>
      <p>
        These terms are governed by the laws of the Kingdom of Saudi Arabia, and its courts have jurisdiction over any
        dispute arising from them. We first try to settle any disagreement amicably: contact us.
      </p>
    </LegalPage>
  )
}

function PrivacyEn() {
  return (
    <LegalPage title="Privacy policy">
      <p>
        We respect your privacy and protect your personal data under Saudi Arabia's Personal Data Protection Law (PDPL)
        and its regulations. This policy explains what we collect about you when you use the VORNX app and the VORNX card,
        why, who we share it with, and your rights. The controller of your data is VORNX for Technology &amp; Smart Systems.
      </p>

      <h2>1. The data we collect</h2>
      <ul>
        <li><b>Account data:</b> name, email, mobile number, language, and VORNX ID. We never store your password, only a
          one-way encrypted fingerprint of it.</li>
        <li><b>Verification data:</b> when you confirmed your email and mobile, and which version of the terms you accepted
          and when. For verification codes we store only a fingerprint, deleted within a day.</li>
        <li><b>Authenticator app (if you turn it on):</b> the app's secret key, stored encrypted, and fingerprints of the recovery codes.</li>
        <li><b>Card, permits and entries:</b> your card, permits, subscriptions and tickets, and the access log: place, door,
          time and whether entry was allowed or denied.</li>
        <li><b>Wallet:</b> the balance and the transactions (top-ups, payments, transfers) with their amounts and parties,
          and the invoice number at the payment gateway. We never store or see your bank card number.</li>
        <li><b>Devices and sessions:</b> IP address, browser or device type, and the last time each login session was used,
          to protect you and so you can log out of any device.</li>
        <li><b>Support and notifications:</b> your messages to us and the notifications we send you.</li>
        <li><b>Identity verification (later, only when you ask for a service that needs it):</b> your national ID or iqama number.</li>
      </ul>

      <h2>2. Why we use it</h2>
      <ul>
        <li>To provide the Service: opening doors, issuing permits, subscriptions and tickets, and running the wallet.</li>
        <li>To protect your account: email and mobile verification, login codes, detecting fraud and abuse.</li>
        <li>To contact you: verification codes, receipts, security alerts, and support replies.</li>
        <li>To comply with the law, such as keeping financial records and answering competent authorities.</li>
      </ul>
      <p>Legal basis: performing our contract with you (this Service), your consent, our legal obligations, and our
        legitimate interest in keeping the Service secure.</p>
      <p>We don't sell your data, and we don't use it for advertising.</p>

      <h2>3. Who we share it with</h2>
      <ul>
        <li><b>Partners:</b> the partner who issues you a permit, subscription or ticket sees your name, VORNX ID, the permit,
          and your entries at their own places only, not the rest of your data.</li>
        <li><b>Service providers working for us, under contracts that protect your data:</b>
          <ul>
            <li>Hosting and database: Railway.</li>
            <li>Sending email: Brevo.</li>
            <li>WhatsApp messages to confirm your mobile: UltraMsg via WhatsApp.</li>
            <li>Card payments: Moyasar.</li>
            <li>SMS codes (if used): Google Firebase.</li>
          </ul>
        </li>
        <li><b>Competent authorities:</b> when the law requires it.</li>
      </ul>

      <h2>4. Transfers outside Saudi Arabia</h2>
      <p>
        Some of your data may be processed or stored by our providers outside the Kingdom of Saudi Arabia (such as hosting
        servers). We only do this as the PDPL and its regulations allow, with appropriate safeguards to protect it.
      </p>

      <h2>5. How long we keep it</h2>
      <ul>
        <li>Account, card and permit data: as long as your account exists.</li>
        <li>Financial records: as long as the law requires.</li>
        <li>Access logs and sessions: as long as needed for security and handling disputes.</li>
        <li>Verification codes: deleted automatically within a day.</li>
      </ul>
      <p>After your account is closed we delete or anonymize your data, except what the law requires us to keep.</p>

      <h2>6. How we protect it</h2>
      <p>
        We use an encrypted connection (HTTPS), store passwords and codes only as fingerprints, encrypt the authenticator
        app keys, use permissions that stop any customer or partner from seeing what isn't theirs, and keep a log of our
        team's activity. No system is perfectly secure, so if a breach affecting your data happens we will tell you and the
        competent authorities without delay, as the law requires.
      </p>

      <h2>7. Your rights</h2>
      <p>Under the PDPL you have the right to:</p>
      <ul>
        <li>know how we process your data (this policy);</li>
        <li>access your data and get a copy of it;</li>
        <li>correct data that is wrong or incomplete;</li>
        <li>ask us to delete your data when it is no longer needed, except what the law requires us to keep;</li>
        <li>withdraw your consent where it is the basis, without affecting what came before.</li>
      </ul>
      <p>
        Write to us at <a href={`mailto:${EMAIL}`} dir="ltr">{EMAIL}</a> and we will reply within the legal period. You can
        also complain to the Saudi Data &amp; AI Authority (SDAIA).
      </p>

      <h2>8. Cookies and storage</h2>
      <p>
        We don't use cookies for advertising or tracking. The app stores in your browser the session token that keeps you
        logged in, and your choices for theme (light or dark) and language. Logging out deletes the session token.
      </p>

      <h2>9. Children</h2>
      <p>The Service is not aimed at people under the legal age without a guardian's consent, and we don't knowingly collect
        their data without that consent.</p>

      <h2>10. Changes</h2>
      <p>For a material change to this policy we tell you in the app or by email, and update the "Last updated" date above.</p>
    </LegalPage>
  )
}
