import { ReactNode } from 'react';

export function LegacyCaption({ program, tran }: { program: string; tran: string }) {
  return <span className="legacy-caption" data-testid="legacy-caption">legacy: {program}/{tran}</span>;
}

export function ScreenHeader({ title, program, tran, children }: {
  title: string; program: string; tran: string; children?: ReactNode;
}) {
  return (
    <header className="screen-header">
      <div>
        <h1>{title}</h1>
        <LegacyCaption program={program} tran={tran} />
      </div>
      <div className="screen-actions">{children}</div>
    </header>
  );
}

export type Tone = 'error' | 'info' | 'success';

export function Message({ text, tone = 'error' }: { text?: string | null; tone?: Tone }) {
  if (!text) return null;
  return <div className={`message message-${tone}`} role={tone === 'error' ? 'alert' : 'status'}
              data-testid="legacy-message">{text}</div>;
}

export function Card({ title, children, className }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className ?? ''}`}>
      {title && <h2 className="card-title">{title}</h2>}
      {children}
    </section>
  );
}

export function Value({ label, field, value, mono }: { label: string; field: string; value?: string; mono?: boolean }) {
  return (
    <div className="value">
      <span className="value-label">{label}</span>
      <span className={`value-text ${mono ? 'mono' : ''}`} data-field={field}>{value?.trim() || '\u2014'}</span>
    </div>
  );
}

export function TextInput({ label, name, value, onChange, error, width, ...rest }: {
  label: string; name: string; value: string; onChange: (v: string) => void; error?: boolean; width?: string;
  maxLength?: number; type?: string; autoFocus?: boolean; placeholder?: string;
}) {
  return (
    <label className={`field ${error ? 'field-error' : ''}`} style={width ? { width } : undefined}>
      <span>{label}</span>
      <input name={name} data-field={name} value={value} onChange={(e) => onChange(e.target.value)}
             aria-invalid={error || undefined} {...rest} />
    </label>
  );
}

export function Button({ children, variant = 'primary', ...rest }: {
  children: ReactNode; variant?: 'primary' | 'secondary' | 'ghost'; type?: 'button' | 'submit';
  onClick?: () => void; disabled?: boolean; name?: string;
}) {
  return <button className={`btn btn-${variant}`} type={rest.type ?? 'button'} {...rest}>{children}</button>;
}

export function Pager({ page, hasPrev, hasNext, onPrev, onNext }: {
  page: number; hasPrev: boolean; hasNext: boolean; onPrev: () => void; onNext: () => void;
}) {
  return (
    <div className="pager">
      <Button variant="secondary" onClick={onPrev} disabled={!hasPrev} name="prev">Previous (F7)</Button>
      <span data-field="page">Page {page}</span>
      <Button variant="secondary" onClick={onNext} disabled={!hasNext} name="next">Next (F8)</Button>
    </div>
  );
}
