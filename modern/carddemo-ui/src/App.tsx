import { Navigate, Route, Routes } from 'react-router-dom';
import { getSession } from './api/client';
import Layout from './components/Layout';
import SignOn from './screens/SignOn';
import Menu from './screens/Menu';
import AccountView from './screens/AccountView';
import AccountUpdate from './screens/AccountUpdate';
import CardList from './screens/CardList';
import CardView from './screens/CardView';
import CardUpdate from './screens/CardUpdate';
import TransactionList from './screens/TransactionList';
import TransactionView from './screens/TransactionView';
import TransactionAdd from './screens/TransactionAdd';
import BillPay from './screens/BillPay';
import UserList from './screens/UserList';
import { UserAdd, UserDelete, UserUpdate } from './screens/UserMaintenance';

function Protected({ admin, children }: { admin?: boolean; children: JSX.Element }) {
  const s = getSession();
  if (!s) return <Navigate to="/signon" replace />;
  if (admin && s.userType !== 'A') return <Navigate to="/menu" replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/signon" element={<SignOn />} />
      <Route path="/menu" element={<Protected><Menu /></Protected>} />
      <Route path="/admin" element={<Protected admin><Menu /></Protected>} />
      <Route path="/accounts/view" element={<Protected><AccountView /></Protected>} />
      <Route path="/accounts/update" element={<Protected><AccountUpdate /></Protected>} />
      <Route path="/cards" element={<Protected><CardList /></Protected>} />
      <Route path="/cards/view" element={<Protected><CardView /></Protected>} />
      <Route path="/cards/update" element={<Protected><CardUpdate /></Protected>} />
      <Route path="/transactions" element={<Protected><TransactionList /></Protected>} />
      <Route path="/transactions/view" element={<Protected><TransactionView /></Protected>} />
      <Route path="/transactions/add" element={<Protected><TransactionAdd /></Protected>} />
      <Route path="/bill-pay" element={<Protected><BillPay /></Protected>} />
      <Route path="/admin/users" element={<Protected admin><UserList /></Protected>} />
      <Route path="/admin/users/add" element={<Protected admin><UserAdd /></Protected>} />
      <Route path="/admin/users/update" element={<Protected admin><UserUpdate /></Protected>} />
      <Route path="/admin/users/delete" element={<Protected admin><UserDelete /></Protected>} />
      <Route path="*" element={<Navigate to={getSession() ? '/menu' : '/signon'} replace />} />
    </Routes>
  );
}
