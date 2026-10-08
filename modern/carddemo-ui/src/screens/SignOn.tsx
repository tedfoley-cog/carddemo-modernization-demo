import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError, Session, setSession } from '../api/client';
import { Button, LegacyCaption, Message, TextInput } from '../components/ui';
import { ROUTES } from '../routes';

/** COSGN00C / COSGN0A. */
export default function SignOn() {
  const navigate = useNavigate();
  const location = useLocation();
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const farewell = (location.state as { message?: string } | null)?.message;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      const s = await api.post<Session>('/auth/signon', { userId, password });
      setSession(s);
      navigate(ROUTES[s.nextProgram]?.path ?? '/menu');
    } catch (err) {
      setError(err as ApiError);
    }
  };

  return (
    <div className="signon">
      <form className="signon-panel" onSubmit={submit}>
        <div className="brand brand-lg">
          <span className="brand-mark">CD</span>
          <div><strong>CardDemo</strong><small>Credit card servicing</small></div>
        </div>
        <h1>Sign on</h1>
        <LegacyCaption program="COSGN00C" tran="CC00" />
        <Message text={error?.message ?? farewell} tone={error ? 'error' : 'info'} />
        <TextInput label="User ID" name="userId" value={userId} onChange={setUserId} maxLength={8} autoFocus
                   error={error?.field === 'userId'} />
        <TextInput label="Password" name="password" type="password" value={password} onChange={setPassword}
                   maxLength={8} error={error?.field === 'password'} />
        <Button type="submit" name="signon">Sign on</Button>
      </form>
    </div>
  );
}
