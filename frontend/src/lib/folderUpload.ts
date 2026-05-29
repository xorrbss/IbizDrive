/**
 * 폴더 업로드 추출 유틸 — 드롭/입력을 `FolderUploadPlan`으로 정규화한다 (docs/01 §9.6).
 *
 * 순수 추출만 담당한다. 폴더 트리 생성/병합과 enqueue는 `hooks/useFolderUpload`(오케스트레이터).
 * 백엔드는 변경하지 않으며, 여기서 만든 경로는 기존 `createFolder`/`getFolderChildren`으로 materialize된다.
 */

/** 업로드할 파일 1건 + 대상 폴더의 (base 기준) 디렉토리 체인. pathSegments=[] → base 직속. */
export type UploadEntry = {
  file: File
  pathSegments: string[]
}

/**
 * 추출 결과.
 * - entries: 업로드할 파일 + 경로
 * - dirPaths: 생성해야 할 디렉토리 경로 전체(조상 포함, 빈 폴더 포함). dedupe됨, 정렬은 호출부 책임.
 */
export type FolderUploadPlan = {
  entries: UploadEntry[]
  dirPaths: string[][]
}

/** 경로 배열 → dedup 키. JSON 직렬화로 구분자 충돌 없이 고유. */
const dirKey = (segments: string[]): string => JSON.stringify(segments)

/**
 * `<input type="file" webkitdirectory>` 결과(File[])를 추출.
 * 각 File의 `webkitRelativePath`(예: `"proj/src/a.ts"`)에서 마지막 segment를 파일명,
 * 앞 segments를 디렉토리 체인으로 분해한다. webkitRelativePath가 없으면 base 직속 파일.
 */
export function extractInputFiles(files: File[]): FolderUploadPlan {
  const entries: UploadEntry[] = []
  const dirSeen = new Set<string>()
  const dirPaths: string[][] = []

  for (const file of files) {
    const rel = file.webkitRelativePath || file.name
    const parts = rel.split('/').filter((p) => p.length > 0)
    const pathSegments = parts.slice(0, -1)
    entries.push({ file, pathSegments })
    collectAncestors(pathSegments, dirSeen, dirPaths)
  }

  return { entries, dirPaths }
}

/**
 * drop 시점에 동기 캡처한 `FileSystemEntry[]`(null 가능)를 재귀 순회해 추출.
 *
 * - `isFile` → `entry.file()`로 File 획득
 * - `isDirectory` → 경로 기록 후 `readEntries()`를 empty까지 반복(브라우저는 한 번에 ≤100개 반환)
 * - top-level 파일은 base 직속, top-level 디렉토리는 그 이름이 첫 segment
 */
export async function extractDropEntries(
  entries: Array<FileSystemEntry | null>,
): Promise<FolderUploadPlan> {
  const out: FolderUploadPlan = { entries: [], dirPaths: [] }
  for (const entry of entries) {
    if (entry) await walk(entry, [], out)
  }
  // dirPaths dedupe (재귀 중 디렉토리는 1회 방문이지만 방어적으로 정리)
  const seen = new Set<string>()
  out.dirPaths = out.dirPaths.filter((p) => {
    const key = dirKey(p)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
  return out
}

async function walk(
  entry: FileSystemEntry,
  parentPath: string[],
  out: FolderUploadPlan,
): Promise<void> {
  if (entry.isFile) {
    const file = await entryFile(entry as FileSystemFileEntry)
    out.entries.push({ file, pathSegments: parentPath })
    return
  }
  if (entry.isDirectory) {
    const dirPath = [...parentPath, entry.name]
    out.dirPaths.push(dirPath)
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    let batch = await readEntries(reader)
    while (batch.length > 0) {
      for (const child of batch) await walk(child, dirPath, out)
      batch = await readEntries(reader)
    }
  }
}

function entryFile(entry: FileSystemFileEntry): Promise<File> {
  return new Promise((resolve, reject) => entry.file(resolve, reject))
}

function readEntries(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  return new Promise((resolve, reject) => reader.readEntries(resolve, reject))
}

function collectAncestors(
  pathSegments: string[],
  seen: Set<string>,
  out: string[][],
): void {
  for (let i = 1; i <= pathSegments.length; i++) {
    const sub = pathSegments.slice(0, i)
    const key = dirKey(sub)
    if (!seen.has(key)) {
      seen.add(key)
      out.push(sub)
    }
  }
}
