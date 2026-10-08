import type { Browser, Page } from 'playwright';
import { Driver, Observation, Step } from './model.js';

interface Field { i: number; name: string | null; value: string; prot: boolean }
interface Screen { id: string; map: string | null; fields: Field[]; text: string[] }

/** BMS map -> owning program, so both sides report the same "where am I" value. */
const MAP_PROGRAM: Record<string, string> = {
  COSGN0A: 'COSGN00C', COMEN1A: 'COMEN01C', COADM1A: 'COADM01C', CACTVWA: 'COACTVWC', CACTUPA: 'COACTUPC',
  COBIL0A: 'COBIL00C', COTRN0A: 'COTRN00C', COTRN1A: 'COTRN01C', COTRN2A: 'COTRN02C', CCRDLIA: 'COCRDLIC',
};

/** Logical business field -> BMS field(s) on the legacy map. */
const VIEW_FIELDS: Record<string, string[]> = {
  accountId: ['ACCTSID'], activeStatus: ['ACSTTUS'], openDate: ['ADTOPEN'], creditLimit: ['ACRDLIM'],
  cashCreditLimit: ['ACSHLIM'], currentBalance: ['ACURBAL'], currentCycleCredit: ['ACRCYCR'],
  currentCycleDebit: ['ACRCYDB'], customerId: ['ACSTNUM'], ssn: ['ACSTSSN'], ficoScore: ['ACSTFCO'],
  dateOfBirth: ['ACSTDOB'], customerName: ['ACSFNAM', 'ACSMNAM', 'ACSLNAM'],
};
const UPDATE_FIELDS: Record<string, string> = {
  activeStatus: 'ACSTTUS', creditLimit: 'ACRDLIM', cashCreditLimit: 'ACSHLIM', ficoScore: 'ACSTFCO',
  firstName: 'ACSFNAM', state: 'ACSSTTE', zip: 'ACSZIPC', openYear: 'OPNYEAR', openMonth: 'OPNMON',
};
const TRAN_ADD_FIELDS: Record<string, string> = {
  accountId: 'ACTIDIN', cardNumber: 'CARDNIN', typeCd: 'TTYPCD', categoryCd: 'TCATCD', source: 'TRNSRC',
  description: 'TDESC', amount: 'TRNAMT', origDate: 'TORIGDT', procDate: 'TPROCDT', merchantId: 'MID',
  merchantName: 'MNAME', merchantCity: 'MCITY', merchantZip: 'MZIP', confirm: 'CONFIRM',
};
const TRAN_VIEW_FIELDS: Record<string, string> = {
  transactionId: 'TRNID', cardNumber: 'CARDNUM', typeCd: 'TTYPCD', categoryCd: 'TCATCD', source: 'TRNSRC',
  description: 'TDESC', amount: 'TRNAMT', origDate: 'TORIGDT', procDate: 'TPROCDT', merchantId: 'MID',
  merchantName: 'MNAME',
};

/** Drives the unchanged CICS region through its JSON 3270 API and screenshots the browser terminal. */
export class LegacyDriver implements Driver {
  readonly side = 'legacy' as const;
  private screen!: Screen;
  private page!: Page;

  constructor(private readonly baseUrl: string, private readonly browser: Browser) {}

  private async call(path: string, body?: unknown): Promise<Screen> {
    const res = await fetch(`${this.baseUrl}${path}`, body === undefined ? undefined : {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`legacy ${path} -> HTTP ${res.status}`);
    return (await res.json()) as Screen;
  }

  private field(name: string): Field | undefined {
    return this.screen.fields.find((f) => f.name === name);
  }

  private val(name: string): string {
    return this.field(name)?.value ?? '';
  }

  /** Type into named unprotected fields (others are sent unchanged, as a 3270 would) and press an AID key. */
  private async key(aid: string, values: Record<string, string> = {}, tran?: string) {
    const fields: Record<number, string> = {};
    for (const [name, v] of Object.entries(values)) {
      const f = this.field(name);
      if (!f) throw new Error(`legacy map ${this.screen.map} has no field ${name}`);
      fields[f.i] = v;
    }
    this.screen = await this.call('/api/key', { id: this.screen.id, aid, fields, ...(tran ? { tran } : {}) });
  }

  async start() {
    this.screen = await this.call('/api/session', {});
    await this.key('ENTER', {}, 'CC00');
    this.page = await this.browser.newPage({ viewport: { width: 980, height: 620 } });
  }

  async stop() {
    await this.page?.close();
  }

  async perform(step: Step, shot: string): Promise<Observation> {
    const fields: Record<string, string> = {};
    switch (step.do) {
      case 'signon':
        await this.key('ENTER', { USERID: step.user, PASSWD: step.password });
        break;
      case 'menu':
        await this.key('ENTER', { OPTION: String(step.option).padStart(2, '0') });
        break;
      case 'back':
        await this.key('PF3');
        break;
      case 'accountView':
        await this.key('ENTER', { ACCTSID: step.accountId });
        if (!this.val('ERRMSG').trim()) {
          for (const [k, names] of Object.entries(VIEW_FIELDS)) fields[k] = names.map((n) => this.val(n)).join(' ');
        }
        break;
      case 'accountUpdateFetch':
        await this.key('ENTER', { ACCTSID: step.accountId });
        for (const [k, n] of Object.entries(UPDATE_FIELDS)) fields[k] = this.val(n);
        break;
      case 'accountUpdateEdit': {
        const v: Record<string, string> = {};
        for (const [k, value] of Object.entries(step.changes)) v[UPDATE_FIELDS[k]] = value;
        await this.key('ENTER', v);
        break;
      }
      case 'billPay':
        await this.key('ENTER', { ACTIDIN: step.accountId, ...(step.confirm ? { CONFIRM: step.confirm } : {}) });
        fields.currentBalance = this.val('CURBAL');
        break;
      case 'transactionAdd': {
        const v: Record<string, string> = {};
        for (const [k, value] of Object.entries(step.fields)) v[TRAN_ADD_FIELDS[k]] = value;
        if (step.confirm) v.CONFIRM = step.confirm;
        await this.key('ENTER', v);
        break;
      }
      case 'transactionList':
        for (let r = 1; r <= 3; r++) {
          const n = String(r).padStart(2, '0');
          fields[`row${r}`] = [this.val(`TRNID${n}`), this.val(`TDATE${n}`), this.val(`TDESC${n}`),
            this.val(`TAMT0${n}`)].join(' | ');
        }
        break;
      case 'transactionView':
        await this.key('ENTER', { TRNIDIN: step.transactionId });
        if (!this.val('ERRMSG').trim()) for (const [k, n] of Object.entries(TRAN_VIEW_FIELDS)) fields[k] = this.val(n);
        break;
    }
    const message = this.val('ERRMSG').trim() || this.val('INFOMSG').trim();
    await this.page.goto(`${this.baseUrl}/?id=${encodeURIComponent(this.screen.id)}`);
    await this.page.waitForTimeout(150);
    await this.page.screenshot({ path: shot, type: 'jpeg', quality: 70 });
    return { program: MAP_PROGRAM[this.screen.map ?? ''] ?? this.screen.map ?? undefined, message, fields,
      screenshot: shot };
  }
}
