import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { CardRow, Page } from '../api/types';
import { Button, Card, Message, Pager, ScreenHeader, TextInput } from '../components/ui';

/** COCRDLIC / CCRDLIA: 7 rows per page, account / card filters, S = view, U = update. */
export default function CardList() {
  const navigate = useNavigate();
  const [acct, setAcct] = useState('');
  const [card, setCard] = useState('');
  const [page, setPage] = useState<Page<CardRow> | null>(null);
  const [error, setError] = useState<string>();

  const load = useCallback(async (q: Record<string, string | number | undefined>) => {
    try {
      setPage(await api.get<Page<CardRow>>('/cards', { accountId: acct, cardNumber: card, ...q }));
      setError(undefined);
    } catch (err) {
      setError((err as ApiError).message);
    }
  }, [acct, card]);

  useEffect(() => {
    load({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const search = (e: FormEvent) => {
    e.preventDefault();
    load({});
  };
  const rows = page?.rows ?? [];
  const go = (path: string, r: CardRow) =>
    navigate(`${path}?accountId=${r.accountId}&cardNumber=${r.cardNumber}`);

  return (
    <>
      <ScreenHeader title="Credit cards" program="COCRDLIC" tran="CCLI" />
      <Card>
        <form className="search-row" onSubmit={search}>
          <TextInput label="Account number" name="accountId" value={acct} onChange={setAcct} maxLength={11} />
          <TextInput label="Card number" name="cardNumber" value={card} onChange={setCard} maxLength={16} />
          <Button type="submit" variant="secondary" name="search">Filter</Button>
        </form>
        <Message text={error ?? page?.info} tone={error ? 'error' : 'info'} />
        <table className="table" data-testid="card-table">
          <thead><tr><th>Account</th><th>Card number</th><th>Active</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.cardNumber}>
                <td className="mono" data-field="accountId">{r.accountId}</td>
                <td className="mono" data-field="cardNumber">{r.cardNumber}</td>
                <td data-field="activeStatus">{r.activeStatus}</td>
                <td className="row-actions">
                  <Button variant="ghost" onClick={() => go('/cards/view', r)}>View (S)</Button>
                  <Button variant="ghost" onClick={() => go('/cards/update', r)}>Update (U)</Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {page && (
          <Pager page={page.page} hasPrev={page.page > 1} hasNext={page.hasNext}
                 onPrev={() => load({ before: rows[0]?.cardNumber, page: page.page })}
                 onNext={() => load({ after: rows[rows.length - 1]?.cardNumber, page: page.page })} />
        )}
      </Card>
    </>
  );
}
