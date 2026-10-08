import { mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { scenarios } from '../scenarios/online.js';
import { LegacyDriver } from './legacy.js';
import { Driver, Observation, ScenarioStep, norm, trimEnd } from './model.js';
import { ReactDriver } from './react.js';
import { Comparison, Report, ScenarioResult, writeReports } from './report.js';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..');
const LEGACY = process.env.LEGACY_URL ?? 'http://localhost:3270';
const MODERN = process.env.MODERN_URL ?? 'http://localhost:4173';
const only = process.env.SCENARIOS?.split(',');

function compare(s: ScenarioStep, l: Observation, m: Observation): Comparison[] {
  const out: Comparison[] = [];
  const e = s.expect ?? {};
  const add = (check: string, expected: string, lv: string, mv: string, eq: (a: string, b: string) => boolean) =>
    out.push({ check, expected, legacy: lv, modern: mv, legacyPass: eq(lv, expected), modernPass: eq(mv, expected),
      parity: eq(lv, mv) });
  if (e.program) add('screen (program)', e.program, l.program ?? '', m.program ?? '', (a, b) => a === b);
  if (e.message !== undefined) add('message', e.message, trimEnd(l.message), trimEnd(m.message), (a, b) => a === b);
  if (e.messageMatches) {
    const re = new RegExp(e.messageMatches);
    out.push({ check: 'message', expected: `/${e.messageMatches}/`, legacy: l.message, modern: m.message,
      legacyPass: re.test(l.message), modernPass: re.test(m.message), parity: l.message === m.message });
  }
  for (const [k, v] of Object.entries(e.fields ?? {})) {
    add(`field ${k}`, v, norm(l.fields[k]), norm(m.fields[k]), (a, b) => norm(a) === norm(b));
  }
  for (const k of s.compareOnly ?? []) {
    const lv = norm(l.fields[k]);
    const mv = norm(m.fields[k]);
    out.push({ check: `field ${k}`, expected: '(legacy value)', legacy: lv, modern: mv, legacyPass: lv !== '',
      modernPass: mv === lv, parity: lv === mv });
  }
  return out;
}

async function runSide(d: Driver, id: string, steps: ScenarioStep[]): Promise<Observation[]> {
  const obs: Observation[] = [];
  await d.start(id);
  try {
    for (const [i, s] of steps.entries()) {
      // '$lastTranId' reads back the id this side just generated, so the persisted rows can be compared
      const lastId = obs.map((o) => /ID is (\d+)\./.exec(o.message)?.[1]).filter(Boolean).pop() ?? '';
      const step = JSON.parse(JSON.stringify(s.step).replaceAll('$lastTranId', lastId)) as typeof s.step;
      obs.push(await d.perform(step, join(OUT, 'screenshots', `${id}-${i + 1}-${d.side}.jpg`)));
    }
  } finally {
    await d.stop();
  }
  return obs;
}

const browser = await chromium.launch();
rmSync(join(OUT, 'screenshots'), { recursive: true, force: true });
mkdirSync(join(OUT, 'screenshots'), { recursive: true });
const results: ScenarioResult[] = [];
for (const sc of scenarios.filter((s) => !only || only.includes(s.id))) {
  const res: ScenarioResult = { id: sc.id, title: sc.title, requirements: sc.requirements, pass: false, steps: [] };
  try {
    const legacy = await runSide(new LegacyDriver(LEGACY, browser), sc.id, sc.steps);
    const modern = await runSide(new ReactDriver(MODERN, browser), sc.id, sc.steps);
    res.steps = sc.steps.map((s, i) => {
      const comparisons = compare(s, legacy[i], modern[i]);
      return { n: i + 1, title: s.title, action: JSON.stringify(s.step), comparisons,
        legacyShot: `screenshots/${sc.id}-${i + 1}-legacy.jpg`, modernShot: `screenshots/${sc.id}-${i + 1}-modern.jpg`,
        legacyMessage: legacy[i].message, modernMessage: modern[i].message,
        pass: comparisons.every((c) => c.legacyPass && c.modernPass && c.parity) };
    });
    res.pass = res.steps.every((s) => s.pass);
  } catch (err) {
    res.error = String(err);
  }
  results.push(res);
  console.log(`${res.pass ? 'PASS' : 'FAIL'} ${sc.id} ${sc.title}${res.error ? ` -- ${res.error}` : ''}`);
  for (const st of res.steps.filter((x) => !x.pass)) {
    for (const c of st.comparisons.filter((x) => !(x.legacyPass && x.modernPass && x.parity))) {
      console.log(`   step ${st.n} ${c.check}: expected=${JSON.stringify(c.expected)} legacy=${JSON.stringify(c.legacy)} modern=${JSON.stringify(c.modern)}`);
    }
  }
}
await browser.close();
const all = results.flatMap((r) => r.steps.flatMap((s) => s.comparisons));
const report: Report = {
  generatedAt: new Date().toISOString(), legacyUrl: LEGACY, modernUrl: MODERN, businessDate: '2022-07-06',
  summary: { scenarios: results.length, passed: results.filter((r) => r.pass).length,
    failed: results.filter((r) => !r.pass).length, steps: results.reduce((n, r) => n + r.steps.length, 0),
    comparisons: all.length, failedComparisons: all.filter((c) => !(c.legacyPass && c.modernPass && c.parity)).length },
  scenarios: results,
};
writeReports(OUT, report);
console.log(`\n${report.summary.passed}/${report.summary.scenarios} scenarios, ${all.length - report.summary.failedComparisons}/${all.length} comparisons`);
process.exit(report.summary.failed ? 1 : 0);
