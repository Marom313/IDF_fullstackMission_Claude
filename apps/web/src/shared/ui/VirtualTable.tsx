import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, tableStyles } from './index';

interface Props<T> {
  data: T[];
  columns: ColumnDef<T, unknown>[];
  caption: string;
  hasMore?: boolean;
  onLoadMore?: () => void;
  loadingMore?: boolean;
  rowHeight?: number;
}

/**
 * TanStack Table + TanStack Virtual: only visible rows are in the DOM, so 10,000 rows scroll smoothly.
 * Server-side keyset pagination via onLoadMore (no OFFSET).
 */
export function VirtualTable<T>({ data, columns, caption, hasMore, onLoadMore, loadingMore, rowHeight = 44 }: Props<T>) {
  const { t } = useTranslation();
  const table = useReactTable({ data, columns, getCoreRowModel: getCoreRowModel() });
  const parentRef = useRef<HTMLDivElement>(null);
  const rows = table.getRowModel().rows;
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => rowHeight,
    overscan: 10,
  });
  const items = virtualizer.getVirtualItems();
  const padTop = items.length ? items[0]!.start : 0;
  const padBottom = items.length ? virtualizer.getTotalSize() - items[items.length - 1]!.end : 0;
  const colCount = columns.length;

  return (
    <div>
      <div ref={parentRef} className={tableStyles.wrap} tabIndex={0} role="region" aria-label={caption}>
        <table className={tableStyles.table}>
          <caption className="visually-hidden">{caption}</caption>
          <thead>
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((h) => (
                  <th key={h.id} scope="col">{flexRender(h.column.columnDef.header, h.getContext())}</th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {padTop > 0 && <tr aria-hidden="true"><td colSpan={colCount} style={{ blockSize: padTop }} /></tr>}
            {items.map((vi) => {
              const row = rows[vi.index]!;
              return (
                <tr key={row.id}>
                  {row.getVisibleCells().map((c) => <td key={c.id}>{flexRender(c.column.columnDef.cell, c.getContext())}</td>)}
                </tr>
              );
            })}
            {padBottom > 0 && <tr aria-hidden="true"><td colSpan={colCount} style={{ blockSize: padBottom }} /></tr>}
            {rows.length === 0 && <tr><td colSpan={colCount}>{t('common.noResults')}</td></tr>}
          </tbody>
        </table>
      </div>
      {hasMore && onLoadMore && (
        <p>
          <Button variant="secondary" onClick={onLoadMore} disabled={loadingMore}>
            {loadingMore ? t('common.loading') : t('common.loadMore')}
          </Button>
        </p>
      )}
    </div>
  );
}
