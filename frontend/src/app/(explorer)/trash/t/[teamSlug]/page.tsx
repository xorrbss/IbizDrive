import { ClientTeamTrashWrapper } from './ClientTeamTrashWrapper'

export default async function TeamTrashPage({
  params,
}: {
  params: Promise<{ teamSlug: string }>
}) {
  const { teamSlug } = await params
  return <ClientTeamTrashWrapper teamSlug={teamSlug} />
}
