import type { Metadata } from 'next'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: '문서관리 시스템',
  description: '사내 문서관리 시스템',
}

// FOUC 방지: hydration 전 동기 실행으로 [data-theme] / [data-variant] / [data-density] 적용.
// localStorage 우선, 없으면 prefers-color-scheme. lib/theme.ts / lib/variant.ts / lib/density.ts 와 동일 규칙.
const themeInitScript = `(function(){try{var k='theme';var s=localStorage.getItem(k);var t=(s==='dark'||s==='light')?s:(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');if(t==='dark')document.documentElement.setAttribute('data-theme','dark');}catch(e){}})();`
const variantInitScript = `(function(){try{var v=localStorage.getItem('variant');if(v==='notion'||v==='dropbox'||v==='terminal')document.documentElement.setAttribute('data-variant',v);}catch(e){}})();`
const densityInitScript = `(function(){try{var d=localStorage.getItem('density');if(d==='compact'||d==='comfortable')document.documentElement.setAttribute('data-density',d);}catch(e){}})();`

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    // suppressHydrationWarning: themeInitScript가 hydration 전 <html data-theme>을 설정하므로
    // 서버 HTML과의 속성 mismatch는 의도된 것 — React가 경고/리하이드레이트하지 않도록 명시.
    <html lang="ko" className="h-full" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
        <script dangerouslySetInnerHTML={{ __html: variantInitScript }} />
        <script dangerouslySetInnerHTML={{ __html: densityInitScript }} />
      </head>
      <body className="h-full" suppressHydrationWarning>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
