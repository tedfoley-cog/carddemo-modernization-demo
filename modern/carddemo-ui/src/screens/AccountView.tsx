import { FormEvent, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { AccountView as View } from '../api/types';
import { Button, Card, Message, ScreenHeader, TextInput, Value } from '../components/ui';

/** COACTVWC / CACTVWA. */
export default function AccountView() {
  const [params] = useSearchParams();
  const [accountId, setAccountId] = useState(params.get('accountId') ?? '');
  const [view, setView] = useState<View | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setView(await api.get<View>('/accounts', { accountId }));
      setError(null);
    } catch (err) {
      setView(null);
      setError(err as ApiError);
    }
  };

  return (
    <>
      <ScreenHeader title="Account view" program="COACTVWC" tran="CAVW" />
      <Card>
        <form className="search-row" onSubmit={submit}>
          <TextInput label="Account number" name="accountId" value={accountId} onChange={setAccountId} maxLength={11}
                     autoFocus error={!!error} />
          <Button type="submit" name="search">View account</Button>
        </form>
        <Message text={error?.message} />
      </Card>
      {view && (
        <div className="grid-2">
          <Card title="Account">
            <div className="values">
              <Value label="Account number" field="accountId" value={view.accountId} mono />
              <Value label="Active" field="activeStatus" value={view.activeStatus} />
              <Value label="Opened" field="openDate" value={view.openDate} />
              <Value label="Expiry" field="expirationDate" value={view.expirationDate} />
              <Value label="Reissued" field="reissueDate" value={view.reissueDate} />
              <Value label="Account group" field="groupId" value={view.groupId} />
              <Value label="Credit limit" field="creditLimit" value={view.creditLimit} mono />
              <Value label="Cash credit limit" field="cashCreditLimit" value={view.cashCreditLimit} mono />
              <Value label="Current balance" field="currentBalance" value={view.currentBalance} mono />
              <Value label="Current cycle credit" field="currentCycleCredit" value={view.currentCycleCredit} mono />
              <Value label="Current cycle debit" field="currentCycleDebit" value={view.currentCycleDebit} mono />
            </div>
          </Card>
          <Card title="Customer">
            <div className="values">
              <Value label="Customer ID" field="customerId" value={view.customerId} mono />
              <Value label="Name" field="customerName"
                     value={[view.firstName, view.middleName, view.lastName].map((s) => s.trim()).filter(Boolean).join(' ')} />
              <Value label="SSN" field="ssn" value={view.ssn} mono />
              <Value label="Date of birth" field="dateOfBirth" value={view.dateOfBirth} />
              <Value label="FICO score" field="ficoScore" value={view.ficoScore} />
              <Value label="Address" field="address" value={[view.addressLine1, view.addressLine2].map((s) => s.trim()).filter(Boolean).join(', ')} />
              <Value label="City / State / ZIP" field="cityStateZip" value={`${view.city.trim()}, ${view.state} ${view.zip}`} />
              <Value label="Country" field="country" value={view.country} />
              <Value label="Phone 1" field="phone1" value={view.phone1} mono />
              <Value label="Phone 2" field="phone2" value={view.phone2} mono />
              <Value label="Government ID" field="governmentId" value={view.governmentId} />
              <Value label="EFT account" field="eftAccountId" value={view.eftAccountId} mono />
              <Value label="Primary card holder" field="primaryCardHolder" value={view.primaryCardHolder} />
            </div>
          </Card>
        </div>
      )}
    </>
  );
}
