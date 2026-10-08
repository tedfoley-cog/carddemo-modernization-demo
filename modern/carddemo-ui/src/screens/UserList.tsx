import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Page, UserRow } from '../api/types';
import { Button, Card, Message, Pager, ScreenHeader } from '../components/ui';

/** COUSR00C / COUSR0A. */
export default function UserList() {
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<UserRow> | null>(null);
  const [error, setError] = useState<string>();
  const load = async (q: Record<string, string | number | undefined>) => {
    try {
      setPage(await api.get<Page<UserRow>>('/admin/users', q));
      setError(undefined);
    } catch (err) {
      setError((err as ApiError).message);
    }
  };
  useEffect(() => {
    load({});
  }, []);
  const rows = page?.rows ?? [];
  return (
    <>
      <ScreenHeader title="Users" program="COUSR00C" tran="CU00">
        <Button onClick={() => navigate('/admin/users/add')}>Add user</Button>
      </ScreenHeader>
      <Card>
        <Message text={error} />
        <table className="table">
          <thead><tr><th>User ID</th><th>First name</th><th>Last name</th><th>Type</th><th /></tr></thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.userId}>
                <td className="mono">{u.userId}</td><td>{u.firstName}</td><td>{u.lastName}</td><td>{u.userType}</td>
                <td className="row-actions">
                  <Button variant="ghost" onClick={() => navigate(`/admin/users/update?userId=${u.userId}`)}>Update (U)</Button>
                  <Button variant="ghost" onClick={() => navigate(`/admin/users/delete?userId=${u.userId}`)}>Delete (D)</Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {page && (
          <Pager page={page.page} hasPrev={page.page > 1} hasNext={page.hasNext}
                 onPrev={() => load({ before: rows[0]?.userId, page: page.page })}
                 onNext={() => load({ after: rows[rows.length - 1]?.userId, page: page.page })} />
        )}
      </Card>
    </>
  );
}
