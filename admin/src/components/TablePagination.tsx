'use client';
// The bar under a table: which rows are showing, rows per page, and the page buttons (shadcn Pagination).
import { Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious } from '@/components/ui/pagination';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { PAGE_SIZES, pageWindow, type PageInfo } from '@/lib/paging';
import { cn } from '@/lib/utils';

export interface TablePaginationProps {
  info: PageInfo;
  size: number;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
  /** What the rows are, for "Showing 1–10 of 74 exercises". */
  noun: string;
}

const go = (action: () => void) => (event: { preventDefault: () => void }) => {
  event.preventDefault();
  action();
};

export function TablePagination({ info, size, onPage, onSize, noun }: TablePaginationProps) {
  const { page, pageCount } = info;
  const first = page <= 1;
  const last = page >= pageCount;
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted-foreground tabular-nums">
        {info.total === 0 ? `No ${noun}` : `Showing ${info.from}–${info.to} of ${info.total} ${noun}`}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          Rows per page
          <NativeSelect size="sm" aria-label="Rows per page" value={size} onChange={(event) => onSize(Number(event.target.value))}>
            {PAGE_SIZES.map((option) => <NativeSelectOption key={option} value={option}>{option}</NativeSelectOption>)}
          </NativeSelect>
        </label>
        {pageCount > 1 ? (
          <Pagination className="mx-0 w-auto">
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious href="#" text="Previous" aria-disabled={first} className={cn(first && 'pointer-events-none opacity-50')} onClick={go(() => onPage(page - 1))} />
              </PaginationItem>
              {pageWindow(page, pageCount).map((entry, index) => (
                <PaginationItem key={`${entry}-${index}`}>
                  {entry === 'gap' ? <PaginationEllipsis /> : (
                    <PaginationLink href="#" isActive={entry === page} aria-label={`Page ${entry}`} onClick={go(() => onPage(entry))}>{entry}</PaginationLink>
                  )}
                </PaginationItem>
              ))}
              <PaginationItem>
                <PaginationNext href="#" text="Next" aria-disabled={last} className={cn(last && 'pointer-events-none opacity-50')} onClick={go(() => onPage(page + 1))} />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        ) : null}
      </div>
    </div>
  );
}
