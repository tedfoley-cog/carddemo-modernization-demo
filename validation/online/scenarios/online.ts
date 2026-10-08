import type { Scenario } from '../src/model.js';

const ACCT1 = {
  creditLimit: '+ 2,020.00', cashCreditLimit: '+ 1,020.00', currentBalance: '+ 1,288.10',
  currentCycleCredit: '+ 1,164.87', currentCycleDebit: '- 70.77', customerName: 'Immanuel Madeline Kessler',
  ssn: '020-97-3888', ficoScore: '274', openDate: '2014-11-20', activeStatus: 'Y', customerId: '000000001',
};

const TRAN = {
  typeCd: '01', categoryCd: '0001', source: 'POS TERM', description: 'Parity harness purchase',
  amount: '+00000042.50', origDate: '2022-07-06', procDate: '2022-07-06', merchantId: '800000001',
  merchantName: 'Parity Coffee', merchantCity: 'Austin', merchantZip: '73301',
};

/**
 * Online parity scenarios. Written once; executed against the CICS region and the React application in the same
 * order, so state-changing scenarios run against identical data on both sides.
 */
export const scenarios: Scenario[] = [
  {
    id: 'P01', title: 'Sign-on rejects a wrong password', requirements: ['ONL-SEC-02'],
    steps: [
      { title: 'USER0001 / WRONGPWD', step: { do: 'signon', user: 'USER0001', password: 'WRONGPWD' },
        expect: { message: 'Wrong Password. Try again ...', program: 'COSGN00C' } },
      { title: 'Unknown user', step: { do: 'signon', user: 'NOBODY01', password: 'PASSWORD' },
        expect: { message: 'User not found. Try again ...', program: 'COSGN00C' } },
    ],
  },
  {
    id: 'P02', title: 'Admin is routed to the admin menu', requirements: ['ONL-SEC-03', 'ONL-NAV-01'],
    steps: [
      { title: 'ADMIN001 / PASSWORD', step: { do: 'signon', user: 'ADMIN001', password: 'PASSWORD' },
        expect: { program: 'COADM01C' } },
    ],
  },
  {
    id: 'P03', title: 'Account view shows account and customer', requirements: ['ONL-SEC-01', 'ONL-NAV-01', 'ONL-ACV-02'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' },
        expect: { program: 'COMEN01C' } },
      { title: 'Menu option 01', step: { do: 'menu', option: 1 }, expect: { program: 'COACTVWC' } },
      { title: 'Account 00000000001', step: { do: 'accountView', accountId: '00000000001' },
        expect: { fields: ACCT1 } },
    ],
  },
  {
    id: 'P04', title: 'Account view rejects invalid account ids', requirements: ['ONL-ACV-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 01', step: { do: 'menu', option: 1 } },
      { title: 'Account "1"', step: { do: 'accountView', accountId: '1' },
        expect: { message: 'Account Filter must  be a non-zero 11 digit number' } },
      { title: 'Account 00000000000', step: { do: 'accountView', accountId: '00000000000' },
        expect: { message: 'Account Filter must  be a non-zero 11 digit number' } },
      { title: 'Account not on file', step: { do: 'accountView', accountId: '99999999999' },
        expect: { message: 'Account:99999999999 not found in Cross ref file.  Resp:000000013  Reas:0000' } },
    ],
  },
  {
    id: 'P05', title: 'Account update field validation', requirements: ['ONL-ACU-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 02', step: { do: 'menu', option: 2 }, expect: { program: 'COACTUPC' } },
      { title: 'Fetch 00000000001', step: { do: 'accountUpdateFetch', accountId: '00000000001' },
        expect: { fields: { activeStatus: 'Y', ficoScore: '274', state: 'NC', zip: '12546' } } },
      { title: 'Status X (1220-EDIT-YESNO)', step: { do: 'accountUpdateEdit', changes: { activeStatus: 'X' } },
        expect: { message: 'Account Status must be Y or N.' } },
      { title: 'Open month 13 (EDIT-MONTH)', step: { do: 'accountUpdateEdit', changes: { activeStatus: 'Y', openMonth: '13' } },
        expect: { message: 'Open Date: Month must be a number between 1 and 12.' } },
      { title: 'Credit limit ABC (1250-EDIT-SIGNED-9V2)',
        step: { do: 'accountUpdateEdit', changes: { openMonth: '11', creditLimit: 'ABC' } },
        expect: { message: 'Credit Limit is not valid' } },
      { title: 'Seeded FICO 274 is out of range (QUIRK)',
        step: { do: 'accountUpdateEdit', changes: { creditLimit: '2020.00', firstName: 'Immanuelle' } },
        expect: { message: 'FICO Score: should be between 300 and 850' } },
    ],
  },
  {
    id: 'P06', title: 'Transaction list first page', requirements: ['ONL-LST-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 06', step: { do: 'menu', option: 6 }, expect: { program: 'COTRN00C' } },
      { title: 'First rows', step: { do: 'transactionList' }, compareOnly: ['row1', 'row2', 'row3'] },
    ],
  },
  {
    id: 'P07', title: 'Bill payment negative paths', requirements: ['ONL-BIL-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 10', step: { do: 'menu', option: 10 }, expect: { program: 'COBIL00C' } },
      { title: 'Blank account', step: { do: 'billPay', accountId: '' }, expect: { message: 'Acct ID can NOT be empty...' } },
      { title: 'Non-numeric account "ABC"', step: { do: 'billPay', accountId: 'ABC' },
        expect: { message: 'Account ID NOT found...' } },
    ],
  },
  {
    id: 'P08', title: 'Bill payment pays the full balance (state change)', requirements: ['ONL-BIL-02', 'ONL-BIL-03', 'ONL-LST-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 10', step: { do: 'menu', option: 10 } },
      { title: 'Account 00000000001', step: { do: 'billPay', accountId: '00000000001' },
        expect: { message: 'Confirm to make a bill payment...', fields: { currentBalance: '+0000001288.10' } } },
      { title: 'Confirm Y', step: { do: 'billPay', accountId: '00000000001', confirm: 'Y' },
        expect: { messageMatches: '^Payment successful\\.  Your Transaction ID is \\d+\\.$' } },
      { title: 'Pay again', step: { do: 'billPay', accountId: '00000000001', confirm: 'Y' },
        expect: { message: 'You have nothing to pay...' } },
      { title: 'Back to menu', step: { do: 'back' } },
      { title: 'Menu option 07', step: { do: 'menu', option: 7 }, expect: { program: 'COTRN01C' } },
      { title: 'Persisted TRANSACT row', step: { do: 'transactionView', transactionId: '$lastTranId' },
        expect: { fields: { typeCd: '02', categoryCd: '0002', source: 'POS TERM', description: 'BILL PAYMENT - ONLINE', amount: '+00001288.10' } },
        compareOnly: ['transactionId', 'cardNumber', 'merchantName', 'origDate', 'procDate'] },
      { title: 'Back to menu', step: { do: 'back' } },
      { title: 'Menu option 01', step: { do: 'menu', option: 1 } },
      { title: 'Balance is now zero', step: { do: 'accountView', accountId: '00000000001' },
        expect: { fields: { currentBalance: '+ .00', creditLimit: '+ 2,020.00' } } },
    ],
  },
  {
    id: 'P09', title: 'Transaction add (state change)', requirements: ['ONL-TRA-01'],
    steps: [
      { title: 'Sign on USER0001', step: { do: 'signon', user: 'USER0001', password: 'PASSWORD' } },
      { title: 'Menu option 08', step: { do: 'menu', option: 8 }, expect: { program: 'COTRN02C' } },
      { title: 'Amount not in -99999999.99 format', step: { do: 'transactionAdd', fields: { ...TRAN, accountId: '00000000001', amount: '42.50' } },
        expect: { message: 'Amount should be in format -99999999.99' } },
      { title: 'Valid data, no confirmation', step: { do: 'transactionAdd', fields: { ...TRAN, accountId: '00000000001' } },
        expect: { message: 'Confirm to add this transaction...' } },
      { title: 'Confirm Y', step: { do: 'transactionAdd', fields: { ...TRAN, accountId: '00000000001' }, confirm: 'Y' },
        expect: { messageMatches: '^Transaction added successfully\\.  Your Tran ID is \\d+\\.$' } },
      { title: 'Back to menu', step: { do: 'back' } },
      { title: 'Menu option 07', step: { do: 'menu', option: 7 }, expect: { program: 'COTRN01C' } },
      { title: 'Persisted TRANSACT row', step: { do: 'transactionView', transactionId: '$lastTranId' },
        expect: { fields: { typeCd: '01', categoryCd: '0001', description: 'Parity harness purchase', amount: '+00000042.50', merchantId: '800000001' } },
        compareOnly: ['transactionId', 'cardNumber', 'merchantName', 'origDate', 'procDate'] },
    ],
  },
];
