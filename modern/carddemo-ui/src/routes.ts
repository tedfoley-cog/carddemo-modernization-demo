/** One route per legacy BMS map; the program -> route table mirrors the XCTL targets of the menus. */
export const ROUTES: Record<string, { path: string; title: string; tran: string }> = {
  COSGN00C: { path: '/signon', title: 'Sign on', tran: 'CC00' },
  COMEN01C: { path: '/menu', title: 'Main menu', tran: 'CM00' },
  COADM01C: { path: '/admin', title: 'Administration', tran: 'CA00' },
  COACTVWC: { path: '/accounts/view', title: 'Account view', tran: 'CAVW' },
  COACTUPC: { path: '/accounts/update', title: 'Account update', tran: 'CAUP' },
  COCRDLIC: { path: '/cards', title: 'Credit cards', tran: 'CCLI' },
  COCRDSLC: { path: '/cards/view', title: 'Card details', tran: 'CCDL' },
  COCRDUPC: { path: '/cards/update', title: 'Update card', tran: 'CCUP' },
  COTRN00C: { path: '/transactions', title: 'Transactions', tran: 'CT00' },
  COTRN01C: { path: '/transactions/view', title: 'Transaction details', tran: 'CT01' },
  COTRN02C: { path: '/transactions/add', title: 'Add transaction', tran: 'CT02' },
  COBIL00C: { path: '/bill-pay', title: 'Bill payment', tran: 'CB00' },
  COUSR00C: { path: '/admin/users', title: 'Users', tran: 'CU00' },
  COUSR01C: { path: '/admin/users/add', title: 'Add user', tran: 'CU01' },
  COUSR02C: { path: '/admin/users/update', title: 'Update user', tran: 'CU02' },
  COUSR03C: { path: '/admin/users/delete', title: 'Delete user', tran: 'CU03' },
};
