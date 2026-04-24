import { ClientFilesPage } from './ClientFilesPage'

export default async function FilesPage({
  params,
}: {
  params: Promise<{ parts: string[] }>
}) {
  const { parts } = await params
  return <ClientFilesPage parts={parts} />
}
