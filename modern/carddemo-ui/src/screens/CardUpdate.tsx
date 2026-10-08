import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { CardDetail, Outcome } from '../api/types';
import { Button, Card, Message, ScreenHeader, TextInput, Tone } from '../components/ui';

type Changes = { embossedName: string; activeStatus: string; expiryMonth: string; expiryYear: string };
const pick = (d: CardDetail): Changes => ({
  embossedName: d.embossedName, activeStatus: d.activeStatus, expiryMonth: d.expiryMonth, expiryYear: d.expiryYear,
});

/** COCRDUPC / CCRDUPA. */
export default function CardUpdate() {
  const [params] = useSearchParams();
  const [acct, setAcct] = useState(params.get('accountId') ?? '');
  const [card, setCard] = useState(params.get('cardNumber') ?? '');
  const [orig, setOrig] = useState<Changes | null>(null);
  const [form, setForm] = useState<Changes | null>(null);
  const [validated, setValidated] = useState(false);
  const [msg, setMsg] = useState<{ text: string; tone: Tone; field?: string | null } | null>(null);
  const fail = (err: unknown) => {
    const e = err as ApiError;
    setMsg({ text: e.message, tone: 'error', field: e.field });
    setValidated(false);
  };

  const load = async (a: string, c: string) => {
    try {
      const d = await api.get<CardDetail>('/cards/update', { accountId: a, cardNumber: c });
      setOrig(pick(d));
      setForm(pick(d));
      setMsg({ text: d.info, tone: 'info' });
    } catch (err) {
      setOrig(null);
      fail(err);
    }
  };

  useEffect(() => {
    if (params.get('cardNumber')) load(params.get('accountId') ?? '', params.get('cardNumber') ?? '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const q = { accountId: acct, cardNumber: card };
  const validate = async () => {
    try {
      const o = await api.post<Outcome>('/cards/update/validate', { original: orig, changes: form }, q);
      setMsg({ text: o.message, tone: 'info' });
      setValidated(true);
    } catch (err) {
      fail(err);
    }
  };
  const save = async () => {
    try {
      const o = await api.put<Outcome>('/cards/update', { original: orig, changes: form }, q);
      setMsg({ text: o.message, tone: 'success' });
      setOrig(form);
      setValidated(false);
    } catch (err) {
      fail(err);
    }
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    load(acct, card);
  };
  const set = (k: keyof Changes) => (v: string) => {
    setForm((f) => (f ? { ...f, [k]: v } : f));
    setValidated(false);
  };

  return (
    <>
      <ScreenHeader title="Update card" program="COCRDUPC" tran="CCUP">
        {form && <Button variant="secondary" onClick={validate} name="validate">Validate (Enter)</Button>}
        {form && <Button onClick={save} disabled={!validated} name="save">Save (F5)</Button>}
      </ScreenHeader>
      <Card>
        <form className="search-row" onSubmit={submit}>
          <TextInput label="Account number" name="accountId" value={acct} onChange={setAcct} maxLength={11} />
          <TextInput label="Card number" name="cardNumber" value={card} onChange={setCard} maxLength={16} />
          <Button type="submit" variant="secondary" name="fetch">Fetch card</Button>
        </form>
        <Message text={msg?.text} tone={msg?.tone} />
      </Card>
      {form && (
        <Card title="Card">
          <div className="form-row">
            <TextInput label="Name on card" name="embossedName" value={form.embossedName} onChange={set('embossedName')}
                       maxLength={50} error={msg?.field === 'embossedName'} />
            <TextInput label="Active (Y/N)" name="activeStatus" value={form.activeStatus} onChange={set('activeStatus')}
                       maxLength={1} width="7rem" error={msg?.field === 'activeStatus'} />
            <TextInput label="Expiry month" name="expiryMonth" value={form.expiryMonth} onChange={set('expiryMonth')}
                       maxLength={2} width="7rem" error={msg?.field === 'expiryMonth'} />
            <TextInput label="Expiry year" name="expiryYear" value={form.expiryYear} onChange={set('expiryYear')}
                       maxLength={4} width="7rem" error={msg?.field === 'expiryYear'} />
          </div>
        </Card>
      )}
    </>
  );
}
