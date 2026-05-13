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
  // P2a — 현재 사용자 즐겨찾기 여부. backend `@JsonInclude(NON_NULL)`로 키 자체 생략될 수 있음.
  starred?: boolean
}
