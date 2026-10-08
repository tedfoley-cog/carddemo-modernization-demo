import { writeFileSync } from 'node:fs';

export interface Comparison {
  check: string; expected: string; legacy: string; modern: string; legacyPass: boolean; modernPass: boolean;
  parity: boolean;
}
export interface StepResult {
  n: number; title: string; action: string; legacyShot: string; modernShot: string; comparisons: Comparison[];
  pass: boolean; legacyMessage: string; modernMessage: string;
}
export interface ScenarioResult {
  id: string; title: string; requirements: string[]; pass: boolean; steps: StepResult[]; error?: string;
}
export interface Report {
  generatedAt: string; legacyUrl: string; modernUrl: string; businessDate: string;
  summary: { scenarios: number; passed: number; failed: number; steps: number; comparisons: number; failedComparisons: number };
  scenarios: ScenarioResult[];
}

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));
const show = (s: string) => `<code>${esc(s).replace(/ /g, '&middot;') || '&empty;'}</code>`;
const badge = (ok: boolean) => `<span class="b ${ok ? 'ok' : 'ko'}">${ok ? 'PASS' : 'FAIL'}</span>`;

export function writeReports(dir: string, r: Report) {
  writeFileSync(`${dir}/report.json`, JSON.stringify(r, null, 2));
  const rows = r.scenarios.map((s) => `<tr><td><a href="#${s.id}">${s.id}</a></td><td>${esc(s.title)}</td>
    <td>${s.requirements.join(', ')}</td><td>${s.steps.length}</td><td>${badge(s.pass)}</td></tr>`).join('');
  const body = r.scenarios.map((s) => `
  <section id="${s.id}"><h2>${s.id} &middot; ${esc(s.title)} ${badge(s.pass)}</h2>
  <p class="req">Requirements: ${s.requirements.join(', ')}</p>${s.error ? `<p class="err">${esc(s.error)}</p>` : ''}
  ${s.steps.map((st) => `
    <div class="step"><h3>Step ${st.n}: ${esc(st.title)} <small>${esc(st.action)}</small> ${badge(st.pass)}</h3>
    <div class="shots"><figure><figcaption>Legacy CICS (3270)</figcaption><img loading="lazy" src="${st.legacyShot}"></figure>
    <figure><figcaption>Modern (React + Spring Boot)</figcaption><img loading="lazy" src="${st.modernShot}"></figure></div>
    ${st.comparisons.length ? `<table class="cmp"><tr><th>Check</th><th>Expected</th><th>Legacy</th><th>Modern</th><th>Legacy = expected</th><th>Modern = expected</th><th>Legacy = modern</th></tr>
    ${st.comparisons.map((c) => `<tr class="${c.legacyPass && c.modernPass && c.parity ? '' : 'bad'}"><td>${esc(c.check)}</td><td>${show(c.expected)}</td><td>${show(c.legacy)}</td><td>${show(c.modern)}</td><td>${badge(c.legacyPass)}</td><td>${badge(c.modernPass)}</td><td>${badge(c.parity)}</td></tr>`).join('')}</table>` : ''}
    </div>`).join('')}
  </section>`).join('');
  const sm = r.summary;
  writeFileSync(`${dir}/report.html`, `<!doctype html><html><head><meta charset="utf-8"><title>Online parity report</title>
<style>body{font:14px system-ui,sans-serif;margin:2rem;color:#1b2430;background:#f6f8fa}h1{margin:0 0 .25rem}table{border-collapse:collapse;background:#fff}
td,th{border:1px solid #dde3ea;padding:.3rem .5rem;text-align:left;vertical-align:top}th{background:#eef2f6}code{font:12px ui-monospace,monospace;white-space:pre-wrap}
.b{font:600 11px ui-monospace,monospace;padding:.1rem .35rem;border-radius:3px}.ok{background:#dcfae6;color:#067647}.ko{background:#fee4e2;color:#b42318}
section{background:#fff;border:1px solid #dde3ea;border-radius:8px;padding:1rem 1.25rem;margin:1.25rem 0}.step{border-top:1px solid #eef1f5;padding-top:.75rem;margin-top:.75rem}
.shots{display:grid;grid-template-columns:1fr 1fr;gap:1rem}figure{margin:0}figure img{width:100%;border:1px solid #dde3ea;border-radius:4px}figcaption{font-size:12px;color:#5d6b7b}
tr.bad td{background:#fff6f5}.req{color:#5d6b7b}.err{color:#b42318}.meta{color:#5d6b7b}small{color:#5d6b7b;font-weight:400}</style></head><body>
<h1>CardDemo online parity: legacy CICS vs modern stack</h1>
<p class="meta">Generated ${r.generatedAt} &middot; business date ${r.businessDate} &middot; legacy ${esc(r.legacyUrl)} &middot; modern ${esc(r.modernUrl)}</p>
<p><b>${sm.passed}/${sm.scenarios}</b> scenarios passed &middot; ${sm.steps} steps &middot; ${sm.comparisons - sm.failedComparisons}/${sm.comparisons} field comparisons matched.
Blanks are shown as &middot; so message spacing differences are visible.</p>
<table><tr><th>ID</th><th>Scenario</th><th>Requirements</th><th>Steps</th><th>Result</th></tr>${rows}</table>${body}</body></html>`);
}
