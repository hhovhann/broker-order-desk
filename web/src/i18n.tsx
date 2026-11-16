import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

export type Lang = 'en' | 'ar'

/** English text is the key. Arabic labels are a first draft and should be reviewed by a native speaker before production. */
const AR: Record<string, string> = {
  'Prices': 'الأسعار', 'Trading': 'التداول', 'Charts': 'الرسوم', 'Analytics': 'التحليل', 'News': 'الأخبار', 'Portfolio': 'المحفظة', 'Margin': 'الهامش', 'Statement': 'كشف الحساب',
  'Wallet': 'المحفظة النقدية', 'Alerts': 'التنبيهات', 'Liquidity': 'السيولة', 'Services': 'الخدمات', 'Risk': 'المخاطر', 'Audit': 'التدقيق', 'Sign out': 'تسجيل الخروج', 'Sign in': 'تسجيل الدخول',
  'Market Open': 'السوق مفتوح', 'Market Closed': 'السوق مغلق', 'Simulated session': 'جلسة تجريبية', 'Index': 'المؤشر', 'Trades': 'الصفقات', 'Shares': 'الأسهم', 'Riyal': 'ريال',
  'Symbol': 'الرمز', 'Company': 'الشركة', 'Offer shares': 'كمية العرض', 'Offer': 'العرض', 'Last': 'آخر سعر', 'Bid': 'الطلب', 'Bid shares': 'كمية الطلب', 'Average': 'المتوسط',
  'Prev close': 'الإغلاق السابق', 'High': 'الأعلى', 'Low': 'الأدنى', 'Trend': 'الاتجاه', 'Change': 'التغير', 'Change %': 'التغير %', 'Filter symbol or name': 'ابحث بالرمز أو الاسم',
  'Favorites': 'المفضلة', 'All': 'الكل', 'Export CSV': 'تصدير CSV', 'Balance': 'الرصيد', 'Portfolio value': 'قيمة المحفظة', 'Holdings': 'الحيازات', 'Buy orders value': 'قيمة أوامر الشراء',
  'Buy': 'شراء', 'Sell': 'بيع', 'Limit price (QAR)': 'سعر الحد (ريال)', 'Order value': 'قيمة الأمر', 'Orders': 'الأوامر', 'Cancel': 'إلغاء', 'Depth': 'العمق', 'Symbols': 'الرموز',
  'Cash balance': 'الرصيد النقدي', 'Equity': 'حقوق الملكية', 'Buying power': 'القوة الشرائية', 'Margin loan': 'قرض الهامش', 'Market value': 'القيمة السوقية', 'Maintenance': 'الحد الأدنى للصيانة',
  'MARGIN CALL': 'نداء الهامش', 'Deposit': 'إيداع', 'Withdraw': 'سحب', 'Withdrawable': 'قابل للسحب', 'Amount (QAR)': 'المبلغ (ريال)', 'Bank': 'البنك', 'Transfers': 'التحويلات',
  'Create alert': 'إنشاء تنبيه', 'Above': 'أعلى من', 'Below': 'أقل من', 'Active': 'نشط', 'Triggered': 'تم التفعيل', 'Statements': 'القوائم المالية', 'Ratios': 'النسب المالية',
  'Assets': 'الأصول', 'Liabilities': 'الالتزامات', 'Profit': 'الأرباح', 'Profit / Equity': 'الربح / الملكية', 'Dividend Yield': 'عائد التوزيعات', 'Book Value': 'القيمة الدفترية',
  'P/E': 'مكرر الربحية', 'P/B': 'مكرر القيمة الدفترية', 'Disclosures': 'الإفصاحات', 'Market': 'السوق', 'Compare': 'مقارنة', 'Candles': 'الشموع', 'Quote': 'عرض سعر', 'Members': 'الأعضاء',
  'Institutions': 'المؤسسات', 'Open Account': 'فتح حساب', 'Tariff': 'الرسوم', 'Profit Purification': 'تطهير الأرباح', 'Submit': 'إرسال', 'Full name': 'الاسم الكامل', 'Email': 'البريد الإلكتروني',
  'User name': 'اسم المستخدم', 'Password': 'كلمة المرور', 'Time': 'الوقت', 'Side': 'الجانب', 'Price': 'السعر', 'Status': 'الحالة', 'Account': 'الحساب', 'Loan': 'القرض',
  'Dividend': 'التوزيعات', 'Language': 'اللغة', 'Advancers': 'الصاعدة', 'Decliners': 'الهابطة', 'Unchanged': 'دون تغيير',
}

interface Ctx { lang: Lang; setLang: (l: Lang) => void; t: (s: string) => string }
const C = createContext<Ctx>({ lang: 'en', setLang: () => {}, t: s => s })
export const useI18n = () => useContext(C)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(() => { try { return (localStorage.getItem('lang') as Lang) || 'en' } catch { return 'en' } })
  useEffect(() => {
    document.documentElement.lang = lang; document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr'
    try { localStorage.setItem('lang', lang) } catch { /* private mode */ }
  }, [lang])
  const t = (s: string) => (lang === 'ar' ? AR[s] ?? s : s)
  return <C.Provider value={{ lang, setLang, t }}>{children}</C.Provider>
}
