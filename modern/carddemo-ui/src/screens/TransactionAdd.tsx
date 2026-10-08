import { FormEvent, useState } from 'react';
import { api, ApiError } from '../api/client';
import { Button, Card, Message, ScreenHeader, TextInput, Tone } from '../components/ui';

const EMPTY = {
  accountId: '', cardNumber: '', typeCd: '', categoryCd: '', source: '', description: '', amount: '', origDate: '',
  procDate: '', merchantId: '', merchantName: '', merchantCity: '', merchantZip: '', confirm: '',
};
type Form = typeof EMPTY;

const LAYOUT: [keyof Form, string, number, string?][][] = [
  [['accountId', 'Account number', 11], ['cardNumber', 'or card number', 16]],
  [['typeCd', 'Type', 2, '5rem'], ['categoryCd', 'Category', 4, '7rem'], ['source', 'Source', 10]],
  [['description', 'Description', 60]],
  [['amount', 'Amount (-99999999.99)', 12], ['origDate', 'Original date (YYYY-MM-DD)', 10],
   ['procDate', 'Processed date (YYYY-MM-DD)', 10]],
  [['merchantId', 'Merchant ID', 9], ['merchantName', 'Merchant name', 30]],
  [['merchantCity', 'Merchant city', 25], ['merchantZip', 'Merchant ZIP', 10]],
  [['confirm', 'Confirm (Y/N)', 1, '9rem']],
];

/** COTRN02C / COTRN2A. */
export default function TransactionAdd() {
  const [form, setForm] = useState<Form>(EMPTY);
  const [msg, setMsg] = useState<{ text: string; tone: Tone; field?: string | null } | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const r = await api.post<{ message: string; transactionId: string | null }>('/transactions', form);
      setMsg({ text: r.message, tone: r.transactionId ? 'success' : 'info' });
      if (r.transactionId) setForm(EMPTY);
    } catch (err) {
      const ex = err as ApiError;
      setMsg({ text: ex.message, tone: 'error', field: ex.field });
    }
  };

  return (
    <>
      <ScreenHeader title="Add transaction" program="COTRN02C" tran="CT02" />
      <Card>
        <form className="stack" onSubmit={submit}>
          {LAYOUT.map((row, i) => (
            <div className="form-row" key={i}>
              {row.map(([k, label, max, width]) => (
                <TextInput key={k} label={label} name={k} value={form[k]} maxLength={max} width={width}
                           onChange={(v) => setForm((f) => ({ ...f, [k]: v }))}
                           error={msg?.tone === 'error' && msg.field === k} />
              ))}
            </div>
          ))}
          <Message text={msg?.text} tone={msg?.tone} />
          <div><Button type="submit" name="submit">Add transaction</Button></div>
        </form>
      </Card>
    </>
  );
}
