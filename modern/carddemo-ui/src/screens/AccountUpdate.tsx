import { FormEvent, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { AccountFields, AccountUpdateData, Outcome } from '../api/types';
import { Button, Card, Message, ScreenHeader, TextInput, Tone } from '../components/ui';

type Group = { title: string; rows: [string, string, number, string?][][] };

/** Field layout of COACTUP (CACTUPA); split date / SSN / phone parts are kept exactly as on the map. */
const GROUPS: Group[] = [
  { title: 'Account', rows: [
    [['activeStatus', 'Active (Y/N)', 1, '5rem'], ['groupId', 'Account group', 10]],
    [['openYear', 'Opened (YYYY)', 4, '7rem'], ['openMonth', 'MM', 2, '4rem'], ['openDay', 'DD', 2, '4rem']],
    [['expiryYear', 'Expiry (YYYY)', 4, '7rem'], ['expiryMonth', 'MM', 2, '4rem'], ['expiryDay', 'DD', 2, '4rem']],
    [['reissueYear', 'Reissued (YYYY)', 4, '7rem'], ['reissueMonth', 'MM', 2, '4rem'], ['reissueDay', 'DD', 2, '4rem']],
    [['creditLimit', 'Credit limit', 15], ['cashCreditLimit', 'Cash credit limit', 15]],
    [['currentBalance', 'Current balance', 15]],
    [['currentCycleCredit', 'Cycle credit', 15], ['currentCycleDebit', 'Cycle debit', 15]],
  ] },
  { title: 'Customer', rows: [
    [['firstName', 'First name', 25], ['middleName', 'Middle name', 25], ['lastName', 'Last name', 25]],
    [['ssn1', 'SSN', 3, '5rem'], ['ssn2', '', 2, '4rem'], ['ssn3', '', 4, '5.5rem'], ['ficoScore', 'FICO', 3, '5rem']],
    [['dobYear', 'Date of birth (YYYY)', 4, '9rem'], ['dobMonth', 'MM', 2, '4rem'], ['dobDay', 'DD', 2, '4rem']],
    [['addressLine1', 'Address line 1', 50], ['addressLine2', 'Address line 2', 50]],
    [['city', 'City', 50], ['state', 'State', 2, '5rem'], ['zip', 'ZIP', 5, '6rem'], ['country', 'Country', 3, '6rem']],
    [['phone1a', 'Phone 1', 3, '5rem'], ['phone1b', '', 3, '5rem'], ['phone1c', '', 4, '5.5rem']],
    [['phone2a', 'Phone 2', 3, '5rem'], ['phone2b', '', 3, '5rem'], ['phone2c', '', 4, '5.5rem']],
    [['governmentId', 'Government ID', 20], ['eftAccountId', 'EFT account', 10], ['primaryCardHolder', 'Primary holder (Y/N)', 1, '9rem']],
  ] },
];

/** COACTUPC / CACTUPA: fetch -> edit -> validate (Enter) -> commit (F5). */
export default function AccountUpdate() {
  const [accountId, setAccountId] = useState('');
  const [data, setData] = useState<AccountUpdateData | null>(null);
  const [fields, setFields] = useState<AccountFields>({});
  const [msg, setMsg] = useState<{ text: string; tone: Tone; field?: string | null } | null>(null);
  const [validated, setValidated] = useState(false);

  const fail = (err: unknown) => {
    const e = err as ApiError;
    setMsg({ text: e.message, tone: 'error', field: e.field });
    setValidated(false);
  };

  const fetchAccount = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const d = await api.get<AccountUpdateData>('/accounts/update', { accountId });
      setData(d);
      setFields(d.fields);
      setMsg({ text: d.info, tone: 'info' });
      setValidated(false);
    } catch (err) {
      setData(null);
      fail(err);
    }
  };

  const body = () => ({ original: data!.fields, changes: fields });

  const validate = async () => {
    try {
      const o = await api.post<Outcome>('/accounts/update/validate', body(), { accountId: data!.accountId });
      setMsg({ text: o.message, tone: 'info' });
      setValidated(true);
    } catch (err) {
      fail(err);
    }
  };

  const save = async () => {
    try {
      const o = await api.put<Outcome>('/accounts/update', body(), { accountId: data!.accountId });
      setMsg({ text: o.message, tone: 'success' });
      const d = await api.get<AccountUpdateData>('/accounts/update', { accountId: data!.accountId });
      setData(d);
      setFields(d.fields);
      setValidated(false);
    } catch (err) {
      fail(err);
    }
  };

  const set = (k: string) => (v: string) => {
    setFields((f) => ({ ...f, [k]: v }));
    setValidated(false);
  };

  return (
    <>
      <ScreenHeader title="Account update" program="COACTUPC" tran="CAUP">
        {data && <Button variant="secondary" onClick={validate} name="validate">Validate (Enter)</Button>}
        {data && <Button onClick={save} disabled={!validated} name="save">Save (F5)</Button>}
      </ScreenHeader>
      <Card>
        <form className="search-row" onSubmit={fetchAccount}>
          <TextInput label="Account number" name="accountId" value={accountId} onChange={setAccountId} maxLength={11}
                     autoFocus error={msg?.field === 'accountId'} />
          <Button type="submit" variant="secondary" name="fetch">Fetch account</Button>
        </form>
        <Message text={msg?.text} tone={msg?.tone} />
      </Card>
      {data && (
        <div className="grid-2">
          {GROUPS.map((g) => (
            <Card key={g.title} title={g.title}>
              {g.rows.map((row, i) => (
                <div className="form-row" key={i}>
                  {row.map(([k, label, max, width]) => (
                    <TextInput key={k} label={label} name={k} value={fields[k] ?? ''} onChange={set(k)} maxLength={max}
                               width={width} error={msg?.tone === 'error' && msg.field === k} />
                  ))}
                </div>
              ))}
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
