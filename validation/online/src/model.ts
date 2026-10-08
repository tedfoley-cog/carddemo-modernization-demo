/** A scenario is written once as abstract user steps; each driver turns a step into terminal keys or browser clicks. */
export type Step =
  | { do: 'signon'; user: string; password: string }
  | { do: 'menu'; option: number }
  | { do: 'back' }
  | { do: 'accountView'; accountId: string }
  | { do: 'accountUpdateFetch'; accountId: string }
  | { do: 'accountUpdateEdit'; changes: Record<string, string> }
  | { do: 'billPay'; accountId: string; confirm?: string }
  | { do: 'transactionAdd'; fields: Record<string, string>; confirm?: string }
  | { do: 'transactionList' }
  | { do: 'transactionView'; transactionId: string };

export interface Expect {
  /** exact legacy message text (trailing blanks ignored) */
  message?: string;
  /** regular expression the message must match (used where a generated id is embedded) */
  messageMatches?: string;
  /** business values, whitespace-normalised */
  fields?: Record<string, string>;
  /** where the user lands: legacy program that owns the screen */
  program?: string;
}

export interface ScenarioStep {
  title: string;
  step: Step;
  expect?: Expect;
  /** values captured from this step (e.g. generated transaction id) that must be identical on both sides */
  compareOnly?: string[];
}

export interface Scenario {
  id: string;
  title: string;
  requirements: string[];
  steps: ScenarioStep[];
}

export interface Observation {
  program?: string;
  message: string;
  fields: Record<string, string>;
  screenshot: string;
}

export interface Driver {
  readonly side: 'legacy' | 'modern';
  start(scenarioId: string): Promise<void>;
  perform(step: Step, shot: string): Promise<Observation>;
  stop(): Promise<void>;
}

export const norm = (s: string | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();
export const trimEnd = (s: string | undefined) => (s ?? '').replace(/\s+$/, '').replace(/^\s+/, '');
