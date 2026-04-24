import type { Metadata } from 'next'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: '문서관리 시스템',
  description: '사내 문서관리 시스템',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" className="h-full">
      <body className="h-full">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
