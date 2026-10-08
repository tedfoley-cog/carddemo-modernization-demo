import { ReactNode, useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { api, getSession, setSession } from '../api/client';
import type { MenuOption } from '../api/types';
import { ROUTES } from '../routes';

export default function Layout({ children }: { children: ReactNode }) {
  const session = getSession()!;
  const navigate = useNavigate();
  const [options, setOptions] = useState<MenuOption[]>([]);
  const home = session.userType === 'A' ? '/admin' : '/menu';

  useEffect(() => {
    api.get<MenuOption[]>('/menu').then(setOptions).catch(() => setOptions([]));
  }, []);

  const signOff = async () => {
    await api.post('/auth/signoff').catch(() => undefined);
    setSession(null);
    navigate('/signon', { state: { message: 'Thank you for using CardDemo application...' } });
  };

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">CD</span>
          <div>
            <strong>CardDemo</strong>
            <small>Card servicing</small>
          </div>
        </div>
        <nav>
          <NavLink to={home} end className="nav-item">Home</NavLink>
          {options.filter((o) => o.installed && ROUTES[o.program]).map((o) => (
            <NavLink key={o.number} to={ROUTES[o.program].path} end className="nav-item">
              <span>{o.name}</span>
              <small>{o.transaction}</small>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="main">
        <div className="topbar">
          <span className="env-badge">Business date 2022-07-06</span>
          <div className="user">
            <span data-testid="user-id">{session.userId}</span>
            <span className="role">{session.userType === 'A' ? 'Administrator' : 'Customer service'}</span>
            <button className="btn btn-ghost" onClick={signOff} name="signoff">Sign off (F3)</button>
          </div>
        </div>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
