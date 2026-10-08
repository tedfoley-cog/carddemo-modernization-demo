import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { CardDetail } from '../api/types';
import { Button, Card, Message, ScreenHeader, TextInput, Value } from '../components/ui';

/** COCRDSLC / CCRDSLA. */
export default function CardView() {
  const [params] = useSearchParams();
  const [acct, setAcct] = useState(params.get('accountId') ?? '');
  const [card, setCard] = useState(params.get('cardNumber') ?? '');
  const [detail, setDetail] = useState<CardDetail | null>(null);
  const [error, setError] = useState<string>();

  const load = async (a: string, c: string) => {
    try {
      setDetail(await api.get<CardDetail>('/cards/detail', { accountId: a, cardNumber: c }));
      setError(undefined);
    } catch (err) {
      setDetail(null);
      setError((err as ApiError).message);
    }
  };

  useEffect(() => {
    if (params.get('cardNumber')) load(params.get('accountId') ?? '', params.get('cardNumber') ?? '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    load(acct, card);
  };

  return (
    <>
      <ScreenHeader title="Card details" program="COCRDSLC" tran="CCDL" />
      <Card>
        <form className="search-row" onSubmit={submit}>
          <TextInput label="Account number" name="accountId" value={acct} onChange={setAcct} maxLength={11} />
          <TextInput label="Card number" name="cardNumber" value={card} onChange={setCard} maxLength={16} />
          <Button type="submit" name="search">View card</Button>
        </form>
        <Message text={error ?? (detail ? undefined : 'Please enter Account and Card Number')}
                 tone={error ? 'error' : 'info'} />
      </Card>
      {detail && (
        <Card title="Card">
          <div className="values">
            <Value label="Account number" field="accountId" value={detail.accountId} mono />
            <Value label="Card number" field="cardNumber" value={detail.cardNumber} mono />
            <Value label="Name on card" field="embossedName" value={detail.embossedName} />
            <Value label="Active" field="activeStatus" value={detail.activeStatus} />
            <Value label="Expires" field="expiry" value={`${detail.expiryMonth}/${detail.expiryYear}`} />
          </div>
        </Card>
      )}
    </>
  );
}
