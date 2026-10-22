const ROUTES = [
  ["grp", "Visão"], ["visao", "Visão geral", "◧"], ["meu", "Meu painel", "☺"],
  ["grp", "Trabalho em curso"], ["aberta", "Abertas", "○"], ["epicos", "Épicos", "◈"], ["negligenciadas", "Negligenciadas", "⚠"], ["riscos", "Riscos", "◆"], ["previsao", "Previsão", "◷"],
  ["grp", "Resultados"], ["fechadas", "Fechadas", "✓"], ["negativas", "Negativas", "✕"], ["tempo", "Tempo em status", "⏱"],
  ["grp", "Prazos e qualidade"], ["metas", "Metas e prazos", "◎"], ["review", "Checklist × Review", "⇋"], ["integridade", "Integridade", "⚖"],
  ["grp", "Pessoas e capacidade"], ["pessoas", "Carga por pessoa", "▤"], ["capacidade", "Capacidade e ausências", "▦"], ["gargalos", "Gargalos e fluxo", "≋"],
  ["grp", "Ação"], ["distribuicao", "Distribuição", "⇉"], ["rebalanceamento", "Rebalanceamento", "⇄"],
  ["grp", "Governança"], ["qualidade", "Qualidade dos dados", "☑"], ["auditoria", "Auditoria", "☰"], ["sync", "Sincronização", "⟳"],
];
const TITLES = Object.fromEntries(ROUTES.filter((r) => r[0] !== "grp").map((r) => [r[0], r[1]]));
const FILTERS = [["squad", "Squad"], ["project", "Projeto"], ["person", "Pessoa"], ["type", "Tipo"], ["priority", "Prioridade"]];
const NO_FILTER = new Set(["auditoria", "sync"]);
// filtros que de fato alteram cada tela (os demais ficam ocultos para não enganar)
const SQ_PR = ["squad", "project"], SQ_PR_PE = ["squad", "project", "person"];
const FILTERS_BY_SCREEN = { epicos: SQ_PR, pessoas: SQ_PR_PE, capacidade: SQ_PR_PE, gargalos: SQ_PR_PE, distribuicao: ["squad"], rebalanceamento: ["squad"] };
const DATE_SCREENS = new Set(["fechadas", "negativas", "tempo", "metas", "review", "integridade"]);

function parseHash() {
  const [path, qs] = (location.hash.slice(2) || "visao").split("?");
  return { screen: SCREENS[path] ? path : "visao", f: Object.fromEntries(new URLSearchParams(qs || "")) };
}
function go(screen, f) {
  const q = new URLSearchParams(Object.entries(f).filter(([, v]) => v)).toString();
  location.hash = `#/${screen}${q ? "?" + q : ""}`;
}

function renderNav(cur, f) {
  const q = new URLSearchParams(Object.entries(f).filter(([, v]) => v)).toString();
  $("#nav").innerHTML = ROUTES.map((r) => (r[0] === "grp" ? `<div class="grp">${r[1]}</div>` : `<a href="#/${r[0]}${q ? "?" + q : ""}" ${r[0] === cur ? 'aria-current="page"' : ""}><span aria-hidden="true">${r[2]}</span>${r[1]}</a>`)).join("");
}

function renderFilters(cur, f) {
  const m = state.meta;
  if (NO_FILTER.has(cur)) { $("#filters").innerHTML = ""; return; }
  const opt = (list, val, all) => `<option value="">${all}</option>` + list.map(([v, l]) => `<option value="${escx(v)}" ${String(v) === String(val) ? "selected" : ""}>${escx(l)}</option>`).join("");
  const allow = FILTERS_BY_SCREEN[cur];
  let h = `<select data-f="squad" aria-label="Squad">${opt(m.squads.map((s) => [s.key, s.name]), f.squad, "Todos os squads")}</select>
  <select data-f="project" aria-label="Projeto">${opt(m.projects.map((x) => [x, x]), f.project, "Todos os projetos")}</select>
  <select data-f="person" aria-label="Pessoa">${opt(m.people.filter((p) => p.role !== "gerente").map((p) => [p.id, p.name]), f.person, "Todas as pessoas")}</select>
  <select data-f="type" aria-label="Tipo">${opt(m.types.map((x) => [x, x]), f.type, "Todos os tipos")}</select>
  <select data-f="priority" aria-label="Prioridade">${opt(m.priorities.map((x) => [x, x]), f.priority, "Todas as prioridades")}</select>`;
  if (allow) { const t = document.createElement("div"); t.innerHTML = h; t.querySelectorAll("select").forEach((x) => { if (!allow.includes(x.dataset.f)) x.remove(); }); h = t.innerHTML; }
  if (DATE_SCREENS.has(cur)) h += `<input type="date" data-f="date_from" value="${escx(f.date_from || "")}" aria-label="De"><input type="date" data-f="date_to" value="${escx(f.date_to || "")}" aria-label="Até">`;
  $("#filters").innerHTML = h;
  $("#filters").onchange = (e) => { const k = e.target.dataset.f; if (k) { const nf = { ...parseHash().f, [k]: e.target.value }; go(cur, nf); } };
}

async function render() {
  const { screen, f } = parseHash();
  $("#title").textContent = TITLES[screen];
  document.title = TITLES[screen] + " · Painel de Squads";
  renderNav(screen, f);
  renderFilters(screen, f);
  closeDrawer();
  $("#side").classList.remove("open");
  const root = $("#main");
  root.innerHTML = '<div class="empty">Carregando…</div>';
  try { await SCREENS[screen](root, f); } catch (e) { root.innerHTML = `<div class="card"><h3>Não foi possível carregar</h3><p>${escx(e.message)}</p></div>`; }
  root.focus({ preventScroll: true });
}

(async function init() {
  const t = localStorage.getItem("tema");
  if (t) document.documentElement.dataset.theme = t;
  $("#theme").onclick = () => { const dark = (document.documentElement.dataset.theme || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")) === "dark"; document.documentElement.dataset.theme = dark ? "light" : "dark"; localStorage.setItem("tema", document.documentElement.dataset.theme); };
  $("#menu").onclick = () => $("#side").classList.toggle("open");
  try {
    [state.meta, state.sync] = await Promise.all([api("meta"), api("sync/status")]);
  } catch (e) {
    $("#main").innerHTML = `<div class="card"><h3>Sem acesso à API</h3><p>${escx(e.message)}</p><p>Se a chave de API estiver ativa: <code>localStorage.setItem('apiKey','…')</code>.</p></div>`;
    return;
  }
  if (state.meta.mode === "demo") { $("#modo").textContent = "DADOS DE DEMONSTRAÇÃO"; const b = $("#banner"); b.hidden = false; b.textContent = "Dados fictícios gerados para demonstração. Nenhum número aqui vem do seu Jira."; }
  else $("#modo").textContent = "Jira · " + (state.sync.flavor === "dc" ? "Data Center" : "Cloud");
  window.addEventListener("hashchange", render);
  render();
})();
