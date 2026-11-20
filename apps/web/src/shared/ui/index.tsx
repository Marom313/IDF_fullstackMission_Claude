import { forwardRef, useEffect, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode,
  type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { useTranslation } from 'react-i18next';
import s from './ui.module.scss';

type Variant = 'primary' | 'secondary' | 'danger' | 'success';

export function Button({ variant = 'primary', large, className, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; large?: boolean }) {
  const cls = [s.btn, variant !== 'primary' && s[variant], large && s.large, className].filter(Boolean).join(' ');
  return <button type="button" className={cls} {...rest} />;
}

export function Card({ title, children, headingLevel = 2 }: { title?: ReactNode; children: ReactNode;
  headingLevel?: 2 | 3 }) {
  const H = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <section className={s.card}>
      {title && <H className={s.cardTitle}>{title}</H>}
      {children}
    </section>
  );
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: (ids: { id: string; describedBy?: string; invalid: boolean }) => ReactNode;
}

/** Label + control + hint + error; the error is linked with aria-describedby (WCAG 3.3.1). */
export function Field({ label, error, hint, required, children }: FieldProps) {
  const { t } = useTranslation();
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [hintId, errId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={s.field}>
      <label htmlFor={id} className={s.label}>
        {label}
        {required && <span className={s.required} aria-hidden="true">*</span>}
        {required && <span className="visually-hidden">({t('field.required')})</span>}
      </label>
      {children({ id, describedBy, invalid: !!error })}
      {hint && <span id={hintId} className={s.hint}>{hint}</span>}
      {error && <span id={errId} className={s.errorText} role="alert">{error}</span>}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  (props, ref) => <input ref={ref} className={s.input} {...props} />);
Input.displayName = 'Input';

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  (props, ref) => <select ref={ref} className={s.input} {...props} />);
Select.displayName = 'Select';

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  (props, ref) => <textarea ref={ref} className={s.input} {...props} />);
Textarea.displayName = 'Textarea';

export type Tone = 'allow' | 'deny' | 'refer' | 'neutral';

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  const cls = [s.badge, tone === 'allow' && s.badgeAllow, tone === 'deny' && s.badgeDeny,
    tone === 'refer' && s.badgeRefer].filter(Boolean).join(' ');
  return <span className={cls}>{children}</span>;
}

export function toneOfStatus(status?: string): Tone {
  switch (status) {
    case 'ACTIVE': case 'APPROVED': case 'ALLOW': case 'APPROVE': case 'CLOSED': case 'OVERTURNED':
      return 'allow';
    case 'REJECTED': case 'REVOKED': case 'DENY': case 'REJECT': case 'SANCTIONED': case 'SUSPENDED': case 'EXPIRED':
      return 'deny';
    case 'SUBMITTED': case 'IN_REVIEW': case 'REFER_TO_CLERK': case 'OPEN': case 'APPEALED':
      return 'refer';
    default:
      return 'neutral';
  }
}

export function Alert({ children, info }: { children: ReactNode; info?: boolean }) {
  return <div className={[s.alert, info && s.info].filter(Boolean).join(' ')} role={info ? 'status' : 'alert'}>{children}</div>;
}

export function QueryState({ isLoading, error, onRetry, children }: { isLoading: boolean; error: string | null;
  onRetry?: () => void; children: ReactNode }) {
  const { t } = useTranslation();
  if (isLoading) return <p role="status" aria-live="polite">{t('common.loading')}</p>;
  if (error) {
    return (
      <Alert>
        {error}{' '}
        {onRetry && <Button variant="secondary" onClick={onRetry}>{t('common.retry')}</Button>}
      </Alert>
    );
  }
  return <>{children}</>;
}

export function Grid({ children }: { children: ReactNode }) {
  return <div className={s.grid}>{children}</div>;
}

export function Row({ children }: { children: ReactNode }) {
  return <div className={s.row}>{children}</div>;
}

export function DefList({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <dl className={s.dl}>
      {items.map(([k, v]) => (
        <div key={k} className="contents">
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Mixed-direction data (IDs, plates, dates) inside RTL text must be isolated (<bdi>). */
export function Ltr({ children }: { children: ReactNode }) {
  return <bdi className="ltr-data" dir="ltr">{children}</bdi>;
}

/** Sets document.title per page (WCAG 2.4.2) and renders the page heading. */
export function PageTitle({ children }: { children: string }) {
  const { t } = useTranslation();
  useEffect(() => {
    document.title = `${children} · ${t('app.title')}`;
  }, [children, t]);
  return <h1 tabIndex={-1} id="page-title">{children}</h1>;
}

export const tableStyles = { wrap: s.tableWrap, table: s.table };
