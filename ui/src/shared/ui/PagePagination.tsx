import { Button } from './button'
import { uiText } from '../constants/uiText'

type PagePaginationProps = {
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
  className?: string
}

export function PagePagination({ page, size, total, onPageChange, className }: PagePaginationProps) {
  const safeSize = size > 0 ? size : 1
  const totalPages = Math.max(1, Math.ceil(total / safeSize))
  const currentPage = Math.min(Math.max(page, 0), totalPages - 1)
  const canGoPrev = currentPage > 0
  const canGoNext = currentPage + 1 < totalPages

  return (
    <div className={`flex items-center justify-end gap-2 ${className ?? ''}`}>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={!canGoPrev}
        onClick={() => onPageChange(currentPage - 1)}
      >
        {uiText.common.paginationPrevious}
      </Button>
      <p className="text-xs text-[var(--color-text-muted)]">
        Page {currentPage + 1} of {totalPages}
      </p>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={!canGoNext}
        onClick={() => onPageChange(currentPage + 1)}
      >
        {uiText.common.paginationNext}
      </Button>
    </div>
  )
}

export type { PagePaginationProps }
