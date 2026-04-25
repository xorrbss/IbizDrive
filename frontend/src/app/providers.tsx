'use client'
import { QueryClient, QueryClientProvider, QueryCache } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { useState } from 'react'
import { Toaster } from 'sonner'
import { qk } from '@/lib/queryKeys'

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => {
    const c = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: true,
          retry: (count, err: unknown) => {
            const status = (err as { status?: number })?.status
            return status !== 401 && status !== 403 && count < 2
          },
        },
      },
      queryCache: new QueryCache({
        onError: (err: unknown) => {
          const status = (err as { status?: number })?.status
          if (status === 403) {
            c.invalidateQueries({ queryKey: qk.effectivePermissions() })
            console.warn('권한이 없거나 접근이 제한되었습니다')
          }
        },
      }),
    })
    return c
  })

  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster
        position="bottom-right"
        richColors
        closeButton
        toastOptions={{ duration: 4000 }}
      />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  )
}
