/* Detalhamento: qualquer card ou item de card abre a lista das tasks que o compõem (janela, nova aba ou CSV). */
const dlJson = (kind, v1 = "", v2 = "", extra = null) => escx(JSON.stringify([kind, v1 ?? "", v2 ?? "", extra]));
const dlAttr = (kind, v1, v2, extra) => `data-drill="${dlJson(kind, v1, v2, extra)}" tabindex="0" role="button" title="Ver as tasks"`;
const dlLink = (text, kind, v1, v2, extra) => (text === "" || text == null || text === 0 || text === "0" ? escx(text ?? "") : `<a href="#" class="dl" data-drill="${dlJson(kind, v1, v2, extra)}" title="Ver as tasks">${escx(text)}</a>`);

function drillFilters(extra) {
  const f = { ...parseHash().f };
  ["kind", "v1", "v2", "granularity"].forEach((k) => delete f[k]);
  return { ...f, ...(extra || {}) };
}
function drillUrl(kind, v1, v2, extra) {
  const q = new URLSearchParams({ ...drillFilters(extra), kind, v1: v1 || "", v2: v2 || "" });
  return `${location.pathname}#/tarefas?${q}`;
}

const DRILL_COLS = (rows) => {
  const has = (k) => rows.some((r) => r[k] != null && r[k] !== "");
  const c = [
    { k: "key", label: "Issue", render: (r) => jiraLink(r.key) },
    { k: "summary", label: "Resumo", render: (r) => escx(r.summary.slice(0, 70)) },
    { k: "tipo", label: "Tipo" }, { k: "assignee", label: "Responsável" },
    { k: "status", label: "Status", render: (r) => pill(r.status, r.bloqueada ? "bad" : "info") }, { k: "priority", label: "Prior." },
    { k: "created", label: "Criada", render: (r) => fmtDate(r.created) }, { k: "due", label: "Venc.", render: (r) => fmtDate(r.due) },
  ];
  if (has("resolved")) c.push({ k: "resolved", label: "Resolvida", render: (r) => fmtDate(r.resolved) }, { k: "ciclo_d", label: "Ciclo (d)", num: true, render: (r) => fmtN(r.ciclo_d, 1) });
  c.push({ k: "idade_d", label: "Idade (d)", num: true, render: (r) => fmtN(r.idade_d, 1) });
  if (has("nivel_risco")) c.push(riskCol);
  if (has("extra")) c.push({ k: "extra", label: "Detalhe", render: (r) => escx(r.extra || "") });
  return c;
};

function drillCsv(d) {
  const cols = ["key", "summary", "tipo", "assignee", "status", "priority", "created", "due", "resolved", "idade_d", "extra"];
  const esc = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const body = [`# ${d.titulo} (${d.total} tasks) filtros: ${JSON.stringify(d.filtros)}`, cols.join(";"), ...d.issues.map((r) => cols.map((c) => esc(r[c])).join(";"))].join("\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob(["﻿" + body], { type: "text/csv;charset=utf-8" }));
  a.download = "tasks.csv";
  a.click();
  URL.revokeObjectURL(a.href);
}

function drillBody(d, tableId) {
  window.__drill = d;
  return `<p class="note" style="margin:2px 0 8px">${escx(d.descricao)}${d.truncado ? " <b>Mostrando as primeiras 1.000.</b>" : ""}</p>
  <div class="row" style="margin-bottom:8px"><input type="text" id="dq" placeholder="Filtrar por texto, chave ou pessoa…" size="34" aria-label="Filtrar tasks">
   <button class="ghost" id="dcsv">Exportar CSV</button></div>
  <div id="dt">${table(DRILL_COLS(d.issues), d.issues, { id: tableId, onClick: openIssue, max: 1000 })}</div>`;
}
function wireDrill(d, tableId) {
  const all = d.issues;
  $("#dcsv").onclick = () => drillCsv(d);
  $("#dq").oninput = (e) => {
    const t = e.target.value.toLowerCase();
    const rows = all.filter((r) => `${r.key} ${r.summary} ${r.assignee} ${r.status} ${r.extra || ""}`.toLowerCase().includes(t));
    window.__tables[tableId].rows = rows;
    $("#" + tableId).innerHTML = rows.length ? tableInner(tableId) : '<div class="empty">Nenhuma task com esse texto.</div>';
  };
}

async function openDrill(kind, v1, v2, extra) {
  closeDrill();
  const bg = document.createElement("div");
  bg.className = "modal-bg";
  bg.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-label="Tasks"><div class="empty">Carregando tasks…</div></div>`;
  bg.addEventListener("click", (e) => e.target === bg && closeDrill());
  document.body.appendChild(bg);
  const box = bg.querySelector(".modal");
  try {
    const d = await api("drill", { ...drillFilters(extra), kind, v1, v2 });
    box.setAttribute("aria-label", d.titulo);
    box.innerHTML = `<div class="row" style="justify-content:space-between;align-items:flex-start"><div><h2 style="margin:0;font-size:1.1rem">${escx(d.titulo)} <span class="pill info">${fmtN(d.total)} tasks</span></h2></div>
      <div class="row"><a href="${drillUrl(kind, v1, v2, extra)}" target="_blank" rel="noopener"><button class="ghost" type="button">Abrir em nova aba ↗</button></a><button class="ghost" onclick="closeDrill()" aria-label="Fechar">✕</button></div></div>
      ${d.total ? drillBody(d, "dtb") : `<p class="note">${escx(d.descricao)}</p><div class="empty">Nenhuma task neste item.</div>`}`;
    if (d.total) wireDrill(d, "dtb");
    box.querySelector("button.ghost[aria-label=Fechar]")?.focus();
  } catch (e) {
    box.innerHTML = `<h2>Não foi possível carregar</h2><p>${escx(e.message)}</p><button class="ghost" onclick="closeDrill()">Fechar</button>`;
  }
}
function closeDrill() { document.querySelector(".modal-bg")?.remove(); }

document.addEventListener("click", (e) => {
  const el = e.target.closest("[data-drill]");
  if (!el) return;
  if (e.target.closest("a:not([data-drill])") || e.target.closest("button:not([data-drill])")) return;  // link da issue, botões internos
  e.preventDefault();
  const [kind, v1, v2, extra] = JSON.parse(el.getAttribute("data-drill"));
  openDrill(kind, v1, v2, extra);
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !document.querySelector(".drawer")) closeDrill();
  if ((e.key === "Enter" || e.key === " ") && e.target.matches("[data-drill]:not(a)")) { e.preventDefault(); e.target.click(); }
});

/* tela cheia (nova aba): #/tarefas?kind=...&v1=...&v2=... */
SCREENS.tarefas = async function (root, f) {
  const d = await api("drill", f);
  $("#title").textContent = d.titulo;
  document.title = d.titulo + " · Painel de Squads";
  root.innerHTML = `<div class="card wide"><h3>${escx(d.titulo)} <span class="pill info">${fmtN(d.total)} tasks</span></h3><div class="note" style="margin-bottom:6px">Filtros: ${escx(Object.entries(d.filtros).map(([k, v]) => `${k}=${v}`).join(" · ") || "nenhum")}</div>${d.total ? drillBody(d, "dtp") : '<div class="empty">Nenhuma task neste item.</div>'}</div>`;
  if (d.total) wireDrill(d, "dtp");
};
