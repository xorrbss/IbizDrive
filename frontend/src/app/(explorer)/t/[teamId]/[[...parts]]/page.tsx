import { ClientFilesPage } from './ClientFilesPage'

export default async function TeamFilesPage({
  params,
}: {
  params: Promise<{ teamId: string; parts?: string[] }>
}) {
  const { teamId, parts } = await params
  return <ClientFilesPage teamId={teamId} parts={parts ?? []} />
}
