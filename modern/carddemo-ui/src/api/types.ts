export interface MenuOption {
  number: number;
  name: string;
  program: string;
  transaction: string;
  userType: string;
  installed: boolean;
}

export interface AccountView {
  accountId: string; activeStatus: string; openDate: string; creditLimit: string; expirationDate: string;
  cashCreditLimit: string; reissueDate: string; currentBalance: string; currentCycleCredit: string; groupId: string;
  currentCycleDebit: string; customerId: string; ssn: string; dateOfBirth: string; ficoScore: string;
  firstName: string; middleName: string; lastName: string; addressLine1: string; addressLine2: string; city: string;
  state: string; zip: string; country: string; phone1: string; phone2: string; governmentId: string;
  eftAccountId: string; primaryCardHolder: string; cardNumber: string;
}

export type AccountFields = Record<string, string>;

export interface AccountUpdateData {
  accountId: string; customerId: string; cardNumber: string; info: string; fields: AccountFields;
}

export interface Outcome { state: string; message: string }

export interface Page<R> { page: number; rows: R[]; hasNext: boolean; info?: string; message?: string }

export interface CardRow { accountId: string; cardNumber: string; activeStatus: string }
export interface CardDetail {
  accountId: string; cardNumber: string; embossedName: string; activeStatus: string;
  expiryMonth: string; expiryYear: string; info: string;
}

export interface TranRow { transactionId: string; date: string; description: string; amount: string }
export interface TranDetail {
  transactionId: string; cardNumber: string; typeCd: string; categoryCd: string; source: string; description: string;
  amount: string; origDate: string; procDate: string; merchantId: string; merchantName: string; merchantCity: string;
  merchantZip: string;
}

export interface UserRow { userId: string; firstName: string; lastName: string; userType: string }
