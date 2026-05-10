import { ClientFilesPage } from './ClientFilesPage'

export default async function SharedFilesPage({
  params,
}: {
  params: Promise<{ parts?: string[] }>
}) {
  const { parts } = await params
  return <ClientFilesPage parts={parts ?? []} />
}
