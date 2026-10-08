import type { Browser, BrowserContext, Page } from 'playwright';
import { Driver, Observation, Step } from './model.js';

const ROUTE_PROGRAM: [RegExp, string][] = [
  [/\/signon$/, 'COSGN00C'], [/\/menu$/, 'COMEN01C'], [/\/admin$/, 'COADM01C'], [/\/accounts\/view$/, 'COACTVWC'],
  [/\/accounts\/update$/, 'COACTUPC'], [/\/bill-pay$/, 'COBIL00C'], [/\/transactions$/, 'COTRN00C'],
  [/\/transactions\/view$/, 'COTRN01C'], [/\/transactions\/add$/, 'COTRN02C'], [/\/cards$/, 'COCRDLIC'],
];
const VIEW_FIELDS = ['accountId', 'activeStatus', 'openDate', 'creditLimit', 'cashCreditLimit', 'currentBalance',
  'currentCycleCredit', 'currentCycleDebit', 'customerId', 'ssn', 'ficoScore', 'dateOfBirth', 'customerName'];
const UPDATE_FIELDS = ['activeStatus', 'creditLimit', 'cashCreditLimit', 'ficoScore', 'firstName', 'state', 'zip',
  'openYear', 'openMonth'];
const TRAN_VIEW_FIELDS = ['transactionId', 'cardNumber', 'typeCd', 'categoryCd', 'source', 'description', 'amount',
  'origDate', 'procDate', 'merchantId', 'merchantName'];

/** Drives the React front end like a user: fills labelled inputs, clicks buttons, reads data-field values. */
export class ReactDriver implements Driver {
  readonly side = 'modern' as const;
  private ctx!: BrowserContext;
  private page!: Page;

  constructor(private readonly baseUrl: string, private readonly browser: Browser) {}

  async start() {
    this.ctx = await this.browser.newContext({ viewport: { width: 1280, height: 860 } });
    this.page = await this.ctx.newPage();
    await this.page.goto(`${this.baseUrl}/signon`);
  }

  async stop() {
    await this.ctx?.close();
  }

  /** Waits for the API round trip the click triggers to finish and the screen to settle. */
  private async act(fn: () => Promise<unknown>) {
    const done = this.page.waitForResponse((r) => r.url().includes('/api/v1/'), { timeout: 10_000 }).catch(() => null);
    await fn();
    await done;
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(150); // let React commit the state update before reading the DOM
  }

  private async fill(name: string, value: string) {
    await this.page.locator(`input[data-field="${name}"]`).fill(value);
  }

  private async text(field: string): Promise<string> {
    const loc = this.page.locator(`[data-field="${field}"]`).first();
    if (!(await loc.count())) return '';
    const tag = await loc.evaluate((e) => e.tagName);
    return tag === 'INPUT' ? loc.inputValue() : ((await loc.textContent()) ?? '');
  }

  async perform(step: Step, shot: string): Promise<Observation> {
    const p = this.page;
    const fields: Record<string, string> = {};
    switch (step.do) {
      case 'signon':
        await this.fill('userId', step.user);
        await this.fill('password', step.password);
        await this.act(() => p.click('button[name="signon"]'));
        break;
      case 'menu':
        await this.act(() => p.click(`button[data-option="${step.option}"]`));
        break;
      case 'back':
        await this.act(() => p.getByRole('link', { name: 'Home' }).click());
        break;
      case 'accountView':
        await this.fill('accountId', step.accountId);
        await this.act(() => p.click('button[name="search"]'));
        for (const f of VIEW_FIELDS) fields[f] = await this.text(f);
        if (!fields.creditLimit) Object.keys(fields).forEach((k) => delete fields[k]);
        break;
      case 'accountUpdateFetch':
        await this.fill('accountId', step.accountId);
        await this.act(() => p.click('button[name="fetch"]'));
        for (const f of UPDATE_FIELDS) fields[f] = await this.text(f);
        break;
      case 'accountUpdateEdit':
        for (const [k, v] of Object.entries(step.changes)) await this.fill(k, v);
        await this.act(() => p.click('button[name="validate"]'));
        break;
      case 'billPay':
        await this.fill('accountId', step.accountId);
        if (step.confirm && (await p.locator('input[data-field="confirm"]').count())) await this.fill('confirm', step.confirm);
        await this.act(() => p.click('button[name="submit"]'));
        fields.currentBalance = await this.text('currentBalance');
        break;
      case 'transactionAdd':
        for (const [k, v] of Object.entries(step.fields)) await this.fill(k, v);
        await this.fill('confirm', step.confirm ?? '');
        await this.act(() => p.click('button[name="submit"]'));
        break;
      case 'transactionList':
        for (let r = 1; r <= 3; r++) {
          const row = p.locator('[data-testid="tran-table"] tbody tr').nth(r - 1);
          const cell = async (f: string) => (await row.locator(`[data-field="${f}"]`).textContent()) ?? '';
          fields[`row${r}`] = [await cell('transactionId'), await cell('date'), await cell('description'),
            await cell('amount')].join(' | ');
        }
        break;
      case 'transactionView':
        await this.fill('transactionId', step.transactionId);
        await this.act(() => p.click('button[name="search"]'));
        if (await p.locator('[data-field="amount"]').count()) {
          const tc = (await this.text('typeCategory')).split('/').map((s) => s.trim());
          for (const f of TRAN_VIEW_FIELDS) fields[f] = await this.text(f);
          fields.typeCd = tc[0] ?? '';
          fields.categoryCd = tc[1] ?? '';
          const m = (await this.text('merchant')).trim();
          fields.merchantId = m.slice(0, 9);
          fields.merchantName = m.slice(10);
        }
        break;
    }
    const msg = p.locator('[data-testid="legacy-message"]');
    const message = (await msg.count()) ? ((await msg.first().textContent()) ?? '').trim() : '';
    await p.screenshot({ path: shot, type: 'jpeg', quality: 70, fullPage: true });
    const path = new URL(p.url()).pathname;
    return { program: ROUTE_PROGRAM.find(([re]) => re.test(path))?.[1], message, fields, screenshot: shot };
  }
}
