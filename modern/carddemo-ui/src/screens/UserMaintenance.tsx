import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { Button, Card, Message, ScreenHeader, TextInput, Tone } from '../components/ui';

type Mode = 'add' | 'update' | 'delete';
const META: Record<Mode, { title: string; program: string; tran: string }> = {
  add: { title: 'Add user', program: 'COUSR01C', tran: 'CU01' },
  update: { title: 'Update user', program: 'COUSR02C', tran: 'CU02' },
  delete: { title: 'Delete user', program: 'COUSR03C', tran: 'CU03' },
};
const EMPTY = { userId: '', firstName: '', lastName: '', password: '', userType: '' };

/** COUSR01C / COUSR02C / COUSR03C. The password is never echoed back (see legacy quirks). */
function UserMaintenance({ mode }: { mode: Mode }) {
  const [params] = useSearchParams();
  const [form, setForm] = useState({ ...EMPTY, userId: params.get('userId') ?? '' });
  const [loaded, setLoaded] = useState(mode === 'add');
  const [msg, setMsg] = useState<{ text: string; tone: Tone } | null>(null);
  const fail = (err: unknown) => setMsg({ text: (err as ApiError).message, tone: 'error' });

  const fetchUser = async (userId: string) => {
    try {
      const u = await api.get<typeof EMPTY & { message: string }>('/admin/users/detail',
        { userId, forDelete: mode === 'delete' });
      setForm({ ...EMPTY, userId: u.userId, firstName: u.firstName, lastName: u.lastName, userType: u.userType });
      setLoaded(true);
      setMsg({ text: u.message, tone: 'info' });
    } catch (err) {
      setLoaded(false);
      fail(err);
    }
  };
  useEffect(() => {
    if (mode !== 'add' && params.get('userId')) fetchUser(params.get('userId')!);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!loaded) return fetchUser(form.userId);
    try {
      const r = mode === 'add' ? await api.post<{ message: string }>('/admin/users', form)
        : mode === 'update' ? await api.put<{ message: string }>('/admin/users', form)
          : await api.del<{ message: string }>('/admin/users', { userId: form.userId });
      setMsg({ text: r.message, tone: 'success' });
      if (mode !== 'update') setForm(EMPTY);
    } catch (err) {
      fail(err);
    }
  };
  const set = (k: keyof typeof EMPTY) => (v: string) => setForm((f) => ({ ...f, [k]: v }));
  const m = META[mode];
  const readOnly = mode === 'delete';

  return (
    <>
      <ScreenHeader title={m.title} program={m.program} tran={m.tran} />
      <Card>
        <form className="stack" onSubmit={submit}>
          <div className="form-row">
            <TextInput label="User ID" name="userId" value={form.userId} onChange={set('userId')} maxLength={8} />
            {loaded && <TextInput label="User type (A/U)" name="userType" value={form.userType}
                                  onChange={readOnly ? () => undefined : set('userType')} maxLength={1} width="9rem" />}
          </div>
          {loaded && (
            <div className="form-row">
              <TextInput label="First name" name="firstName" value={form.firstName}
                         onChange={readOnly ? () => undefined : set('firstName')} maxLength={20} />
              <TextInput label="Last name" name="lastName" value={form.lastName}
                         onChange={readOnly ? () => undefined : set('lastName')} maxLength={20} />
              {!readOnly && <TextInput label="Password" name="password" type="password" value={form.password}
                                       onChange={set('password')} maxLength={8} />}
            </div>
          )}
          <Message text={msg?.text} tone={msg?.tone} />
          <div>
            <Button type="submit" name="submit">
              {!loaded ? 'Fetch user' : mode === 'add' ? 'Add user' : mode === 'update' ? 'Save (F5)' : 'Delete (F5)'}
            </Button>
          </div>
        </form>
      </Card>
    </>
  );
}

export const UserAdd = () => <UserMaintenance mode="add" />;
export const UserUpdate = () => <UserMaintenance mode="update" />;
export const UserDelete = () => <UserMaintenance mode="delete" />;
