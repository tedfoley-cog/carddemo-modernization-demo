import { render, screen } from '@testing-library/react';
import { LegacyCaption, Message, Value } from '../components/ui';
import { ROUTES } from '../routes';
import { getSession, setSession } from '../api/client';

describe('legacy traceability', () => {
  it('every screen route names its legacy program and TRANID', () => {
    for (const [program, r] of Object.entries(ROUTES)) {
      expect(program).toMatch(/^CO[A-Z0-9]{5}C$/);
      expect(r.tran).toMatch(/^C[A-Z0-9]{3}$/);
      expect(r.path.startsWith('/')).toBe(true);
    }
    expect(ROUTES.COACTVWC).toMatchObject({ path: '/accounts/view', tran: 'CAVW' });
  });

  it('renders the legacy caption', () => {
    render(<LegacyCaption program="COACTVWC" tran="CAVW" />);
    expect(screen.getByTestId('legacy-caption')).toHaveTextContent('legacy: COACTVWC/CAVW');
  });
});

describe('Message', () => {
  it('keeps legacy message text byte for byte (double spaces included)', () => {
    const text = 'Account Filter must  be a non-zero 11 digit number';
    render(<Message text={text} />);
    const el = screen.getByTestId('legacy-message');
    expect(el.textContent).toBe(text);
    expect(el).toHaveAttribute('role', 'alert');
  });

  it('renders nothing without text and uses status role for info', () => {
    const { container, rerender } = render(<Message text={null} />);
    expect(container).toBeEmptyDOMElement();
    rerender(<Message text="Confirm to make a bill payment..." tone="info" />);
    expect(screen.getByRole('status')).toHaveTextContent('Confirm to make a bill payment...');
  });
});

describe('Value', () => {
  it('exposes a data-field hook for the parity harness', () => {
    const { container } = render(<Value label="Credit limit" field="creditLimit" value="+      2,020.00" />);
    expect(container.querySelector('[data-field="creditLimit"]')?.textContent).toBe('+      2,020.00');
  });
});

describe('session storage', () => {
  it('stores and clears the stateless session token', () => {
    setSession({ token: 't', userId: 'USER0001', userType: 'U' } as never);
    expect(getSession()?.userId).toBe('USER0001');
    setSession(null);
    expect(getSession()).toBeNull();
  });
});
