import { ClientDeptTrashWrapper } from './ClientDeptTrashWrapper'

export default async function DeptTrashPage({
  params,
}: {
  params: Promise<{ deptSlug: string }>
}) {
  const { deptSlug } = await params
  return <ClientDeptTrashWrapper deptSlug={deptSlug} />
}
