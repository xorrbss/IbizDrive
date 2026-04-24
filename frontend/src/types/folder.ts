export type FolderNode = {
  id: string
  parentId: string | null
  name: string
  slug: string // NFC 정규화된 URL slug
  children?: FolderNode[]
}

export type BreadcrumbItem = {
  id: string
  name: string
  slugPath: string[] // ["영업팀", "계약서"]
}

export type FolderDetail = {
  id: string
  name: string
  slugPath: string[] // 루트에서 현재까지, URL slug용
  breadcrumb: BreadcrumbItem[]
  parentId: string | null
}
