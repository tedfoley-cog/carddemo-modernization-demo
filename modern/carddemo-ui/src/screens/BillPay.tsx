import { FormEvent, useState } from 'react';
import { api, ApiError } from '../api/client';
import { Button, Card, Message, ScreenHeader, TextInput, Tone, Value } from '../components/ui';

interface Result { accountId: string; currentBalance: string; message: string; transactionId: string | null }

/** COBIL00C / COBIL0A: pays the full current balance (the legacy map has no amount field). */
export default function BillPay() {
  const [accountId, setAccountId] = useState('');
  const [confirm, setConfirm] = useState('');
  const [balance, setBalance] = useState<string>();
  const [msg, setMsg] = useState<{ text: string; tone: Tone } | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const r = await api.post<Result>('/bill-payments', { accountId, confirm });
      setBalance(r.currentBalance);
      setMsg({ text: r.message, tone: r.transactionId ? 'success' : 'info' });
      if (r.transactionId) setConfirm('');
    } catch (err) {
      setMsg({ text: (err as ApiError).message, tone: 'error' });
    }
  };

  return (
    <>
      <ScreenHeader title="Bill payment" program="COBIL00C" tran="CB00" />
      <Card>
        <form className="stack" onSubmit={submit}>
          <div className="form-row">
            <TextInput label="Account number" name="accountId" value={accountId} onChange={setAccountId} maxLength={11}
                       autoFocus />
            {balance && <Value label="Current balance" field="currentBalance" value={balance} mono />}
          </div>
          {balance && (
            <div className="form-row">
              <TextInput label="Pay the full balance? (Y/N)" name="confirm" value={confirm} onChange={setConfirm}
                         maxLength={1} width="14rem" />
            </div>
          )}
          <Message text={msg?.text} tone={msg?.tone} />
          <div><Button type="submit" name="submit">{balance ? 'Submit' : 'Look up balance'}</Button></div>
        </form>
      </Card>
    </>
  );
}
