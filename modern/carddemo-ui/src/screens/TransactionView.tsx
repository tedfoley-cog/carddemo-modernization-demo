import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { TranDetail } from '../api/types';
import { Button, Card, Message, ScreenHeader, TextInput, Value } from '../components/ui';

/** COTRN01C / COTRN1A. */
export default function TransactionView() {
  const [params] = useSearchParams();
  const [id, setId] = useState(params.get('transactionId') ?? '');
  const [t, setT] = useState<TranDetail | null>(null);
  const [error, setError] = useState<string>();
  const load = async (tid: string) => {
    try {
      setT(await api.get<TranDetail>('/transactions/detail', { transactionId: tid }));
      setError(undefined);
    } catch (err) {
      setT(null);
      setError((err as ApiError).message);
    }
  };
  useEffect(() => {
    if (params.get('transactionId')) load(params.get('transactionId')!);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    load(id);
  };

  return (
    <>
      <ScreenHeader title="Transaction details" program="COTRN01C" tran="CT01" />
      <Card>
        <form className="search-row" onSubmit={submit}>
          <TextInput label="Transaction ID" name="transactionId" value={id} onChange={setId} maxLength={16} />
          <Button type="submit" name="search">View</Button>
        </form>
        <Message text={error} />
      </Card>
      {t && (
        <Card title="Transaction">
          <div className="values">
            <Value label="Transaction ID" field="transactionId" value={t.transactionId} mono />
            <Value label="Card number" field="cardNumber" value={t.cardNumber} mono />
            <Value label="Type / category" field="typeCategory" value={`${t.typeCd} / ${t.categoryCd}`} />
            <Value label="Source" field="source" value={t.source} />
            <Value label="Description" field="description" value={t.description} />
            <Value label="Amount" field="amount" value={t.amount} mono />
            <Value label="Original date" field="origDate" value={t.origDate} />
            <Value label="Processed date" field="procDate" value={t.procDate} />
            <Value label="Merchant" field="merchant" value={`${t.merchantId} ${t.merchantName}`} />
            <Value label="Merchant city / ZIP" field="merchantCity" value={`${t.merchantCity} ${t.merchantZip}`} />
          </div>
        </Card>
      )}
    </>
  );
}
