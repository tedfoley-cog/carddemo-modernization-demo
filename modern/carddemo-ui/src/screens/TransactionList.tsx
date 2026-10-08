import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Page, TranRow } from '../api/types';
import { Button, Card, Message, Pager, ScreenHeader, TextInput } from '../components/ui';

/** COTRN00C / COTRN0A: 10 rows per page from the TRNIDIN start key. */
export default function TransactionList() {
  const navigate = useNavigate();
  const [fromId, setFromId] = useState('');
  const [page, setPage] = useState<Page<TranRow> | null>(null);
  const [error, setError] = useState<string>();

  const load = async (q: Record<string, string | number | undefined>) => {
    try {
      setPage(await api.get<Page<TranRow>>('/transactions', q));
      setError(undefined);
    } catch (err) {
      setError((err as ApiError).message);
    }
  };
  useEffect(() => {
    load({});
  }, []);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    load({ fromId });
  };
  const rows = page?.rows ?? [];

  return (
    <>
      <ScreenHeader title="Transactions" program="COTRN00C" tran="CT00" />
      <Card>
        <form className="search-row" onSubmit={submit}>
          <TextInput label="Start from transaction ID" name="fromId" value={fromId} onChange={setFromId} maxLength={16} />
          <Button type="submit" variant="secondary" name="search">Search</Button>
        </form>
        <Message text={error} />
        <table className="table" data-testid="tran-table">
          <thead><tr><th>Transaction ID</th><th>Date</th><th>Description</th><th className="num">Amount</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.transactionId}>
                <td className="mono" data-field="transactionId">{r.transactionId}</td>
                <td data-field="date">{r.date}</td>
                <td data-field="description">{r.description}</td>
                <td className="mono num" data-field="amount">{r.amount}</td>
                <td className="row-actions">
                  <Button variant="ghost" onClick={() => navigate(`/transactions/view?transactionId=${r.transactionId}`)}>
                    View (S)
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {page && (
          <Pager page={page.page} hasPrev={page.page > 1} hasNext={page.hasNext}
                 onPrev={() => load({ before: rows[0]?.transactionId, page: page.page })}
                 onNext={() => load({ after: rows[rows.length - 1]?.transactionId, page: page.page })} />
        )}
      </Card>
    </>
  );
}
