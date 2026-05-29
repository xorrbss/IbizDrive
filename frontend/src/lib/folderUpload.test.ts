import { describe, it, expect } from 'vitest'
import { extractInputFiles, extractDropEntries } from './folderUpload'

/**
 * folder-upload P2 — 드롭/입력 → FolderUploadPlan 추출 유틸.
 *
 * - extractInputFiles: `<input webkitdirectory>` File[].webkitRelativePath 파싱
 * - extractDropEntries: drop 시 캡처한 FileSystemEntry[] 재귀 (readEntries ≤100 배치 반복)
 *
 * 백엔드 무변경 (docs/01 §9.6). 본 유닛은 순수 추출만 — 폴더 생성/병합은 useFolderUpload(P4).
 */

// ---- helpers ----

/** webkitRelativePath만 읽으므로 plain object cast로 충분 (File 생성자는 webkitRelativePath read-only). */
function inputFile(relativePath: string): File {
  const name = relativePath.split('/').pop() ?? relativePath
  return { name, webkitRelativePath: relativePath } as unknown as File
}

type MockEntry = FileSystemEntry

function fileEntry(name: string): MockEntry {
  const file = new File(['x'], name)
  return {
    isFile: true,
    isDirectory: false,
    name,
    file: (ok: (f: File) => void) => ok(file),
  } as unknown as MockEntry
}

/** children를 batchSize 단위로 나눠 readEntries가 ≤batchSize씩 반환하도록 시뮬레이트. */
function dirEntry(name: string, children: MockEntry[], batchSize = 100): MockEntry {
  return {
    isFile: false,
    isDirectory: true,
    name,
    createReader: () => {
      let offset = 0
      return {
        readEntries: (ok: (e: MockEntry[]) => void) => {
          const slice = children.slice(offset, offset + batchSize)
          offset += slice.length
          ok(slice)
        },
      }
    },
  } as unknown as MockEntry
}

const keys = (paths: string[][]) => paths.map((p) => p.join('/')).sort()

describe('extractInputFiles', () => {
  it('webkitRelativePath를 디렉토리 체인 + 파일명으로 분해', () => {
    const plan = extractInputFiles([
      inputFile('proj/src/index.ts'),
      inputFile('proj/README.md'),
    ])
    expect(plan.entries).toHaveLength(2)
    expect(plan.entries[0].pathSegments).toEqual(['proj', 'src'])
    expect(plan.entries[1].pathSegments).toEqual(['proj'])
  })

  it('모든 조상 디렉토리 경로를 dedupe해 수집', () => {
    const plan = extractInputFiles([
      inputFile('proj/src/index.ts'),
      inputFile('proj/src/util.ts'),
      inputFile('proj/README.md'),
    ])
    expect(keys(plan.dirPaths)).toEqual(['proj', 'proj/src'])
  })

  it('webkitRelativePath 없으면 루트 직속 파일', () => {
    const plan = extractInputFiles([inputFile('loose.txt')])
    expect(plan.entries[0].pathSegments).toEqual([])
    expect(plan.dirPaths).toEqual([])
  })
})

describe('extractDropEntries', () => {
  it('중첩 디렉토리를 재귀 순회해 파일 경로/디렉토리 경로 추출', async () => {
    const tree = dirEntry('proj', [
      dirEntry('src', [fileEntry('index.ts')]),
      fileEntry('README.md'),
    ])
    const plan = await extractDropEntries([tree])
    const filePaths = plan.entries
      .map((e) => [...e.pathSegments, e.file.name].join('/'))
      .sort()
    expect(filePaths).toEqual(['proj/README.md', 'proj/src/index.ts'])
    expect(keys(plan.dirPaths)).toEqual(['proj', 'proj/src'])
  })

  it('빈 디렉토리도 dirPaths에 포함 (파일 무관)', async () => {
    const tree = dirEntry('proj', [dirEntry('empty', [])])
    const plan = await extractDropEntries([tree])
    expect(plan.entries).toEqual([])
    expect(keys(plan.dirPaths)).toEqual(['proj', 'proj/empty'])
  })

  it('루트 직속 파일은 pathSegments 빈 배열', async () => {
    const plan = await extractDropEntries([fileEntry('a.txt')])
    expect(plan.entries).toHaveLength(1)
    expect(plan.entries[0].pathSegments).toEqual([])
    expect(plan.dirPaths).toEqual([])
  })

  it('readEntries 100개 배치 제한 — empty까지 반복 호출해 전부 수집', async () => {
    const many = Array.from({ length: 250 }, (_, i) => fileEntry(`f${i}.txt`))
    const tree = dirEntry('big', many, 100)
    const plan = await extractDropEntries([tree])
    expect(plan.entries).toHaveLength(250)
  })

  it('null entry(webkitGetAsEntry 미지원/비파일)는 무시', async () => {
    const plan = await extractDropEntries([null, fileEntry('a.txt')])
    expect(plan.entries).toHaveLength(1)
  })
})
