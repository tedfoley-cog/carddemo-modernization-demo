import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, getSession } from '../api/client';
import type { MenuOption } from '../api/types';
import { Card, Message, ScreenHeader } from '../components/ui';
import { ROUTES } from '../routes';

/** COMEN01C / COMEN1A and COADM01C / COADM1A. */
export default function Menu() {
  const admin = getSession()?.userType === 'A';
  const navigate = useNavigate();
  const [options, setOptions] = useState<MenuOption[]>([]);
  const [error, setError] = useState<string>();

  useEffect(() => {
    api.get<MenuOption[]>('/menu').then(setOptions);
  }, []);

  const select = async (o: MenuOption) => {
    try {
      const s = await api.post<{ program: string }>('/menu/select', { option: String(o.number) });
      navigate(ROUTES[s.program].path);
    } catch (err) {
      setError((err as ApiError).message);
    }
  };

  return (
    <>
      <ScreenHeader title={admin ? 'Administration' : 'Main menu'} program={admin ? 'COADM01C' : 'COMEN01C'}
                    tran={admin ? 'CA00' : 'CM00'} />
      <Message text={error} />
      <Card>
        <ol className="menu-grid">
          {options.map((o) => (
            <li key={o.number}>
              <button className={`menu-tile ${o.installed ? '' : 'menu-tile-disabled'}`} data-option={o.number}
                      onClick={() => select(o)}>
                <span className="menu-num">{String(o.number).padStart(2, '0')}</span>
                <span className="menu-name">{o.name}</span>
                <span className="menu-meta">{o.program} / {o.transaction}{o.installed ? '' : ' \u00b7 not installed'}</span>
              </button>
            </li>
          ))}
        </ol>
      </Card>
    </>
  );
}
