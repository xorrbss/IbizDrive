'use client'
export default function Error({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold">문제가 발생했습니다</h2>
      <p className="text-sm text-gray-500 mt-2">{error.message}</p>
      <button onClick={reset} className="mt-4 px-3 py-1 border rounded">
        다시 시도
      </button>
    </div>
  )
}
