import { redirect } from 'next/navigation'

/**
 * Plan B legacy deep-link redirect — old `/files/<folderId>/...` URLs sent users to
 * specific folders. Without backend mapping (folderId→workspace), can't preserve folder
 * context across the workspace pivot. Simplest: redirect to root and let user re-navigate.
 */
export default function FilesLegacyRedirect() {
  redirect('/')
}
