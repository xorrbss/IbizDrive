import { ClientFilesPage } from './ClientFilesPage'

export default async function DeptFilesPage({
  params,
}: {
  params: Promise<{ deptId: string; parts?: string[] }>
}) {
  const { deptId, parts } = await params
  return <ClientFilesPage deptId={deptId} parts={parts ?? []} />
}
