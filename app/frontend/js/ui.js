const $ = (sel, root = document) => root.querySelector(sel);
const state = { filters: {}, meta: null, sync: null, cache: {} };

async function api(path, params = {}, opts = {}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v !== "" && v != null && q.set(k, v));
  const url = "/api/" + path + (q.toString() ? "?" + q : "");
  const headers = { "Content-Type": "application/json", "X-User": localStorage.getItem("usuario") || "gerente" };
  const key = localStorage.getItem("apiKey");
  if (key) headers["X-API-Key"] = key;
  const r = await fetch(url, { headers, ...opts });
  if (!r.ok) { let t = await r.text(); try { t = JSON.parse(t).detail || t; } catch (e) {} throw new Error(typeof t === "string" ? t : JSON.stringify(t)); }
  return r.json();
}

function kpi(label, value, { foot = "", tone = "", delta = null, unit = "" } = {}) {
  let d = "";
  if (delta != null) d = `<span class="delta ${delta > 0 ? "up" : delta < 0 ? "down" : "flat"}" title="variação vs. período anterior">${delta > 0 ? "▲" : delta < 0 ? "▼" : "■"} ${fmtN(Math.abs(delta), 1)}%</span>`;
  return `<div class="card kpi ${tone}"><div class="lbl">${escx(label)}</div><div class="val">${value == null ? "—" : escx(value)}${unit ? `<small style="font-size:.9rem;color:var(--mute)"> ${unit}</small>` : ""}${d}</div><div class="foot">${foot}</div></div>`;
}
const card = (title, sub, body, cls = "") => `<section class="card ${cls}"><h3>${escx(title)}</h3>${sub ? `<p class="sub">${sub}</p>` : ""}${body}</section>`;
const pill = (txt, tone = "mute") => `<span class="pill ${tone}">${escx(txt)}</span>`;
const levelPill = (l) => pill((l === "alto" ? "▲ " : l === "médio" ? "● " : "✓ ") + l, l);
const jiraLink = (key) => (state.sync?.jira ? `<a href="${escx(state.sync.jira)}/browse/${escx(key)}" target="_blank" rel="noopener">${escx(key)}</a>` : `<b>${escx(key)}</b>`);

/* Tabela ordenável. cols: [{k,label,num,render}] */
function table(cols, rows, { id = "t" + Math.random().toString(36).slice(2, 7), onClick = null, max = 200 } = {}) {
  if (!rows.length) return '<div class="empty">Nada por aqui. 🎉</div>';
  window.__tables = window.__tables || {};
  window.__tables[id] = { cols, rows, sort: null, dir: 1, onClick, max };
  return `<div class="tablewrap" id="${id}">${tableInner(id)}</div>` + (rows.length > max ? `<div class="note">Mostrando ${max} de ${rows.length}. Use os filtros para refinar.</div>` : "");
}
function tableInner(id) {
  const t = window.__tables[id];
  let rows = [...t.rows];
  if (t.sort) { const k = t.sort; rows.sort((a, b) => { const x = a[k], y = b[k]; return (x == null) - (y == null) || (x > y ? 1 : x < y ? -1 : 0) * t.dir; }); }
  rows = rows.slice(0, t.max);
  const head = t.cols.map((c) => `<th class="${c.num ? "num" : ""}" data-k="${c.k}" ${t.sort === c.k ? `aria-sort="${t.dir > 0 ? "ascending" : "descending"}"` : ""} tabindex="0" scope="col">${escx(c.label)}</th>`).join("");
  const body = rows.map((r, i) => `<tr class="${t.onClick ? "click" : ""}" data-i="${t.rows.indexOf(r)}">${t.cols.map((c) => `<td class="${c.num ? "num" : ""}">${c.render ? c.render(r) : escx(r[c.k] ?? "—")}</td>`).join("")}</tr>`).join("");
  return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
}
document.addEventListener("click", (e) => {
  const th = e.target.closest("th[data-k]");
  const wrap = e.target.closest(".tablewrap");
  if (th && wrap) { const t = window.__tables[wrap.id]; const k = th.dataset.k; if (t.sort === k) t.dir *= -1; else { t.sort = k; t.dir = 1; } wrap.innerHTML = tableInner(wrap.id); return; }
  const tr = e.target.closest("tr[data-i]");
  if (tr && wrap && window.__tables[wrap.id].onClick && !e.target.closest("a,button")) window.__tables[wrap.id].onClick(window.__tables[wrap.id].rows[+tr.dataset.i]);
});
document.addEventListener("keydown", (e) => { if ((e.key === "Enter" || e.key === " ") && e.target.matches("th[data-k]")) { e.preventDefault(); e.target.click(); } });

/* Gaveta com detalhe da issue */
function openIssue(r) {
  closeDrawer();
  const dur = r.tempo_por_status_d ? Object.entries(r.tempo_por_status_d) : [];
  const total = dur.reduce((a, [, v]) => a + v, 0) || 1;
  const html = `<aside class="drawer" role="dialog" aria-label="Detalhe da issue ${escx(r.key)}"><button class="ghost x" onclick="closeDrawer()" aria-label="Fechar">✕</button>
  <h2>${jiraLink(r.key)} · ${escx(r.summary)}</h2>
  <div class="row" style="margin:6px 0">${pill(r.status, "info")} ${pill(r.priority)} ${pill(r.type)} ${r.nivel_risco ? levelPill(r.nivel_risco) : ""}</div>
  <table><tbody>
   <tr><th>Responsável</th><td>${escx(r.assignee)}</td></tr><tr><th>Squad</th><td>${escx(r.squad)}</td></tr>
   <tr><th>Criada</th><td>${fmtDate(r.created)}</td></tr><tr><th>Vencimento</th><td>${fmtDate(r.due)}</td></tr>
   ${r.resolved ? `<tr><th>Resolvida</th><td>${fmtDate(r.resolved)} (${escx(r.resolution)})</td></tr>` : ""}
   <tr><th>Idade / no status</th><td>${fmtN(r.idade_d, 1)} d / ${fmtN(r.no_status_d, 1)} d</td></tr>
   <tr><th>Estimado / registrado</th><td>${r.estimado_h != null ? fmtN(r.estimado_h, 1) + " h" : "sem estimativa"} / ${fmtN(r.gasto_h, 1)} h</td></tr>
   <tr><th>Pontos</th><td>${r.pontos ?? "—"}</td></tr>
   <tr><th>Reaberturas · devoluções · trocas</th><td>${r.reaberturas} · ${r.devolucoes} · ${r.trocas_resp}</td></tr>
   <tr><th>Previsão pela capacidade</th><td>${fmtDate(r.previsao)}</td></tr>
  </tbody></table>
  ${dur.length ? `<h3 style="margin:14px 0 4px;font-size:.9rem">Tempo por status</h3>${hbars(dur.map(([k, v]) => ({ label: k, value: v })), { fmt: (v) => fmtN(v, 1) + " d" })}` : ""}
  ${(r.fatores_risco || []).length ? `<h3 style="margin:14px 0 4px;font-size:.9rem">Por que o risco é ${escx(r.nivel_risco)} (${r.risco}/100)</h3><ul>${r.fatores_risco.map((f) => `<li><b>+${f.points}</b> ${escx(f.why)}</li>`).join("")}</ul>` : ""}
  </aside>`;
  document.body.insertAdjacentHTML("beforeend", html);
}
function closeDrawer() { document.querySelector(".drawer")?.remove(); }
document.addEventListener("keydown", (e) => e.key === "Escape" && closeDrawer());

function tabs(id, items, active) {
  return `<div class="tabs" role="tablist" id="${id}">${items.map(([k, label, n]) => `<button role="tab" aria-selected="${k === active}" data-tab="${k}">${escx(label)}${n != null ? ` <b>${n}</b>` : ""}</button>`).join("")}</div>`;
}
const issueCols = (extra = []) => [
  { k: "key", label: "Issue", render: (r) => jiraLink(r.key) },
  { k: "summary", label: "Resumo", render: (r) => escx(r.summary.slice(0, 60)) },
  { k: "assignee", label: "Responsável" }, { k: "status", label: "Status", render: (r) => pill(r.status, r.bloqueada ? "bad" : "info") },
  { k: "priority", label: "Prior." }, { k: "idade_d", label: "Idade (d)", num: true, render: (r) => fmtN(r.idade_d, 1) },
  { k: "due", label: "Venc.", render: (r) => fmtDate(r.due) }, ...extra,
];
const riskCol = { k: "risco", label: "Risco", num: true, render: (r) => levelPill(r.nivel_risco) + " " + r.risco };
