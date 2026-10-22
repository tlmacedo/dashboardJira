const wk = (iso) => fmtDM(iso);
const toneBy = (v, bad, warn) => (v >= bad ? "bad" : v >= warn ? "warn" : "ok");
const ocTone = (v) => (v > 1.1 ? "bad" : v > 0.85 ? "warn" : "ok");

const SCREENS = {
/* ------------------------------------------------------------------ 1 */
async visao(root, f) {
  const [d, al] = await Promise.all([api("overview", f), api("alerts", f)]), c = d.cards;
  const tp = d.throughput;
  root.innerHTML = `
  <div class="grid g-kpi">
    ${kpi("Abertas", c.abertas, { foot: `${c.em_andamento} em andamento` })}
    ${kpi("Concluídas (30 d)", c.concluidas_30d.value, { delta: c.concluidas_30d.delta_pct, foot: "vs. 30 d anteriores" })}
    ${kpi("Ciclo P50", c.ciclo_p50_d.value != null ? fmtN(c.ciclo_p50_d.value, 1) : null, { unit: "d", delta: c.ciclo_p50_d.delta_pct, lowerIsBetter: true, foot: `P85: ${fmtN(c.ciclo_p85_d, 1)} d` })}
    ${kpi("No prazo (30 d)", c.no_prazo_pct != null ? fmtN(c.no_prazo_pct, 1) + "%" : null, { tone: c.no_prazo_pct < 80 ? "warn" : "ok", foot: "entregues até o vencimento" })}
    ${kpi("Vencidas", c.vencidas, { tone: c.vencidas ? "bad" : "ok", foot: "abertas após o vencimento" })}
    ${kpi("Negligenciadas", c.negligenciadas, { tone: c.negligenciadas ? "warn" : "ok", foot: "em andamento sem atualização > 7 d" })}
    ${kpi("Bloqueadas", c.bloqueadas, { tone: c.bloqueadas ? "warn" : "ok" })}
    ${kpi("Risco alto", c.risco_alto, { tone: c.risco_alto ? "bad" : "ok", foot: `${c.risco_medio} de risco médio` })}
  </div>
  ${card("Central de atenção", "O que pede ação agora. Clique para abrir a tela.", `<div class="grid g-kpi" style="gap:8px">${al.itens.map((i) => `<a href="#/${i.tela}" style="text-decoration:none;color:inherit;border:1px solid var(--line);border-radius:10px;padding:8px 10px;display:block"><div class="note">${escx(i.titulo)}</div><div style="font-size:1.4rem;font-weight:650;color:var(--${i.tom === "bad" ? "bad" : i.tom === "warn" ? "warn" : i.tom === "ok" ? "ok" : "ink"})">${i.tom === "bad" ? "▲ " : i.tom === "warn" ? "● " : ""}${escx(i.valor ?? "—")}</div><div class="note">${escx(i.detalhe)}</div></a>`).join("")}</div>`, "wide")}
  <div class="grid g-2" style="margin-top:14px">
    ${card("Entregas por semana", "Itens concluídos (exclui cancelados e duplicados)", colChart(tp, { label: (x) => wk(x.week), value: (x) => x.count, title: "Entregas por semana" }))}
    ${card("Quando o backlog aberto termina?", `Monte Carlo sobre as últimas ${d.forecast_backlog.samples} semanas · confiança: ${escx(d.forecast_backlog.confidence)}`,
      d.forecast_backlog.weeks ? `<div class="grid g-3">${[["P50", "metade das simulações"], ["P85", "compromisso seguro"], ["P95", "cenário conservador"]].map(([k, t]) => `<div><div class="lbl note">${k} · ${t}</div><div style="font-size:1.7rem;font-weight:650">${d.forecast_backlog.weeks[k.toLowerCase()]} sem.</div></div>`).join("")}</div><p class="note">${c.abertas} itens abertos. Assume o ritmo recente e que nenhum item novo entra.</p>` : '<div class="empty">Sem histórico suficiente.</div>')}
  </div>
  <h2 style="font-size:1rem;margin:22px 0 8px">Squads</h2>
  <div class="grid g-3">${d.by_squad.map((s) => `<a class="card" style="text-decoration:none;color:inherit" href="#/aberta?squad=${s.key}">
     <h3>${escx(s.name)}</h3><div class="row" style="justify-content:space-between"><span>${sparkline(s.throughput)}</span><small class="note">entregas/sem.</small></div>
     <div class="grid" style="grid-template-columns:repeat(4,1fr);margin-top:8px;text-align:center">
       <div><b style="font-size:1.25rem">${s.abertas}</b><div class="note">abertas</div></div>
       <div><b style="font-size:1.25rem">${s.concluidas_30d}</b><div class="note">feitas 30 d</div></div>
       <div><b style="font-size:1.25rem;color:${s.vencidas ? "var(--bad)" : "inherit"}">${s.vencidas}</b><div class="note">vencidas</div></div>
       <div><b style="font-size:1.25rem;color:${s.risco_alto ? "var(--bad)" : "inherit"}">${s.risco_alto}</b><div class="note">risco alto</div></div></div>
     <div class="row" style="margin-top:8px">${pill("ocupação " + fmtN((s.ocupacao_media ?? 0) * 100) + "%", ocTone(s.ocupacao_media ?? 0))} ${pill("equidade (Jain) " + fmtN(s.equidade.jain, 2), s.equidade.jain >= 0.85 ? "ok" : s.equidade.jain >= 0.7 ? "warn" : "bad")}</div></a>`).join("")}</div>
  <p class="note" style="margin-top:12px">Equidade de Jain: 1,00 = carga proporcional à capacidade em todas as pessoas. Abaixo de 0,85 indica concentração de trabalho.</p>`;
},

/* ------------------------------------------------------------------ 2 */
async aberta(root, f) {
  const d = await api("open", f);
  const col = (cat) => ({ todo: "var(--c5)", active: "var(--c1)", wait: "var(--c6)", blocked: "var(--bad)" }[cat] || "var(--c5)");
  root.innerHTML = `
  <div class="grid g-kpi">
    ${kpi("Abertas", d.total)}
    ${kpi("Idade P50 histórica", d.aging.p50 != null ? fmtN(d.aging.p50, 1) : null, { unit: "d", foot: "tempo de ciclo dos concluídos" })}
    ${kpi("P85", d.aging.p85 != null ? fmtN(d.aging.p85, 1) : null, { unit: "d", foot: "itens mais velhos que isso pedem atenção" })}
    ${kpi("Acima do P85", d.issues.filter((i) => d.aging.p85 && i.category !== "todo" && i.idade_d > d.aging.p85).length, { tone: "warn", foot: "em andamento" })}
  </div>
  <div class="grid g-3" style="margin-top:14px">
    ${card("Por status", "Onde o trabalho está parado", hbars(d.by_status.map((s) => ({ label: s.status, value: s.count, cat: s.category })), { colorOf: (x) => col(x.cat) }))}
    ${card("Por prioridade", "", hbars(d.by_priority.map((p) => ({ label: p.priority, value: p.count })), { color: "var(--c4)" }))}
    ${card("Idade", "Desde o início do trabalho (ou criação)", hbars(d.age_buckets.map((p) => ({ label: p.bucket, value: p.count })), { color: "var(--c3)" }))}
    ${card("Vencimento", "", hbars(d.due_buckets.map((p) => ({ label: p.bucket, value: p.count })), { colorOf: (x) => (x.label === "vencidas" ? "var(--bad)" : x.label === "vence em 7 d" ? "var(--warn)" : "var(--c5)") }))}
  </div>
  ${card("Issues abertas", `Clique numa linha para o detalhe · <a href="/api/export/open.csv?${new URLSearchParams(f)}">exportar CSV</a>`, table(issueCols([{ k: "no_status_d", label: "No status (d)", num: true }, { k: "previsao", label: "Previsão", render: (r) => fmtDate(r.previsao) }, riskCol]), d.issues, { onClick: openIssue }), "wide")}`;
},

/* ------------------------------------------------------------------ 3 */
async negligenciadas(root, f) {
  const d = await api("neglected", f);
  const labels = { sem_atualizacao: "Sem atualização", sem_responsavel: "Sem responsável", vencidas: "Vencidas", bloqueadas: "Bloqueadas", paradas_no_status: "Paradas no status", a_fazer_antigas: "A fazer há > 30 d" };
  const help = { sem_atualizacao: `Em andamento e sem mudança há mais de ${d.idle_days} dias.`, sem_responsavel: "Sem pessoa atribuída.", vencidas: "Venceram e continuam abertas.", bloqueadas: "Em status de bloqueio ou com impedimento sinalizado.", paradas_no_status: "Há mais que 2× a mediana histórica do status atual (mín. 3 d).", a_fazer_antigas: "Criadas há mais de 30 dias e nunca iniciadas." };
  let active = Object.keys(labels).find((k) => d.counts[k]) || "sem_atualizacao";
  const draw = () => {
    root.innerHTML = `<div class="grid g-kpi">${Object.keys(labels).map((k) => kpi(labels[k], d.counts[k], { tone: d.counts[k] ? (k === "vencidas" ? "bad" : "warn") : "ok" })).join("")}</div>
    <div style="margin-top:14px">${tabs("tb", Object.keys(labels).map((k) => [k, labels[k], d.counts[k]]), active)}
    <p class="note">${help[active]}</p>${card(labels[active], "", table(issueCols([{ k: "no_status_d", label: "No status (d)", num: true }, riskCol]), d.groups[active], { onClick: openIssue }), "wide")}</div>`;
    $("#tb").onclick = (e) => { const b = e.target.closest("button"); if (b) { active = b.dataset.tab; draw(); } };
  };
  draw();
},

/* ------------------------------------------------------------------ 4 */
async fechadas(root, f) {
  const d = await api("closed", f);
  root.innerHTML = `
  <div class="grid g-kpi">
    ${kpi("Concluídas", d.totais.concluidas, { foot: `${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)}` })}
    ${kpi("Pontos entregues", fmtN(d.totais.pontos))}
    ${kpi("Ciclo P50 / P85", `${fmtN(d.ciclo.p50, 1)} / ${fmtN(d.ciclo.p85, 1)}`, { unit: "d", foot: `n = ${d.ciclo.n}` })}
    ${kpi("Lead time P50 / P85", `${fmtN(d.lead.p50, 1)} / ${fmtN(d.lead.p85, 1)}`, { unit: "d", foot: "da criação à resolução" })}
    ${kpi("No prazo", d.totais.no_prazo_pct != null ? fmtN(d.totais.no_prazo_pct, 1) + "%" : null, { tone: d.totais.no_prazo_pct < 80 ? "warn" : "ok" })}
    ${kpi("Canceladas/descartadas", d.totais.canceladas_ou_descartadas, { foot: "fora das métricas de entrega" })}
  </div>
  <div class="grid g-2" style="margin-top:14px">
    ${card("Entregas por semana", "", colChart(d.throughput, { label: (x) => wk(x.week), value: (x) => x.count, title: "Entregas por semana" }))}
    ${card("Dispersão do tempo de ciclo", "Cada ponto é uma issue; linhas = percentis do período", scatter(d.scatter, [{ name: "P50", v: d.ciclo.p50, color: "var(--ok)" }, { name: "P85", v: d.ciclo.p85, color: "var(--warn)" }, { name: "P95", v: d.ciclo.p95, color: "var(--bad)" }], { title: "Dispersão do tempo de ciclo" }))}
    ${card("Por tipo", "", hbars(d.by_type.map((x) => ({ label: x.type, value: x.count })), { color: "var(--c2)" }))}
    ${card("Por projeto", "", hbars(d.by_project.map((x) => ({ label: x.project, value: x.count })), { color: "var(--c4)" }))}
  </div>
  ${card("Entregas por pessoa", "Ordem alfabética, de propósito. 'Finalizou' = responsável na conclusão; 'participou' inclui quem trabalhou em algum momento. Use para conversar sobre fluxo, não para ranquear.", table([
    { k: "nome", label: "Pessoa" }, { k: "finalizadas", label: "Finalizou", num: true }, { k: "participou", label: "Participou", num: true },
    { k: "pontos", label: "Pontos", num: true }, { k: "ciclo_p50_d", label: "Ciclo P50 (d)", num: true, render: (r) => fmtN(r.ciclo_p50_d, 1) },
    { k: "ciclo_p85_d", label: "P85 (d)", num: true, render: (r) => fmtN(r.ciclo_p85_d, 1) }, { k: "tempo_ativo_h", label: "Tempo ativo (h)", num: true, render: (r) => fmtN(r.tempo_ativo_h, 0) },
    { k: "estimado_h", label: "Estimado (h)", num: true, render: (r) => fmtN(r.estimado_h, 0) }, { k: "gasto_h", label: "Registrado (h)", num: true, render: (r) => fmtN(r.gasto_h, 0) },
  ], d.pessoas), "wide")}
  ${card("Issues concluídas", `<a href="/api/export/closed.csv?${new URLSearchParams(f)}">exportar CSV</a>`, table([
    { k: "key", label: "Issue", render: (r) => jiraLink(r.key) }, { k: "summary", label: "Resumo", render: (r) => escx(r.summary.slice(0, 55)) },
    { k: "assignee", label: "Responsável" }, { k: "resolved", label: "Resolvida", render: (r) => fmtDate(r.resolved) },
    { k: "lead_d", label: "Lead (d)", num: true }, { k: "ciclo_d", label: "Ciclo (d)", num: true },
    { k: "no_prazo", label: "Prazo", render: (r) => (r.no_prazo == null ? "—" : r.no_prazo ? pill("✓ no prazo", "ok") : pill("▲ atrasou", "bad")) },
    { k: "razao_estimativa", label: "Real÷estim.", num: true, render: (r) => (r.razao_estimativa ? fmtN(r.razao_estimativa, 2) + "×" : "—") },
  ], d.issues, { onClick: openIssue }), "wide")}`;
},

/* ------------------------------------------------------------------ 5 */
async negativas(root, f) {
  const d = await api("negative", f);
  const labels = { reabertas: "Reabertas", devolvidas: "Devolvidas", canceladas_descartadas: "Canceladas/descartadas", concluidas_atrasadas: "Concluídas com atraso", abertas_vencidas: "Abertas vencidas", estouro_estimativa: "Estouro de estimativa", muitas_trocas_responsavel: "Muitas trocas de responsável" };
  const help = { reabertas: "Voltaram de Concluído para outro status.", devolvidas: "Retornaram a uma etapa anterior do fluxo (ex.: revisão → desenvolvimento).", canceladas_descartadas: "Resolução negativa: cancelado, duplicado, não será feito.", concluidas_atrasadas: "Resolvidas depois do vencimento.", abertas_vencidas: "Ainda abertas e já vencidas.", estouro_estimativa: "Horas registradas acima de 125% do estimado.", muitas_trocas_responsavel: "3 ou mais trocas de responsável." };
  let active = "reabertas";
  const draw = () => {
    root.innerHTML = `<div class="grid g-kpi">
      ${Object.keys(labels).map((k) => kpi(labels[k], d.counts[k], { tone: d.counts[k] ? "warn" : "ok" })).join("")}
      ${kpi("Taxa de reabertura", fmtN(d.taxas.reabertura_pct, 1) + "%", { foot: "sobre concluídas no período" })}
      ${kpi("Taxa de devolução", fmtN(d.taxas.devolucao_pct, 1) + "%")}
      ${kpi("Taxa de atraso", fmtN(d.taxas.atraso_pct, 1) + "%", { tone: d.taxas.atraso_pct > 20 ? "bad" : "" })}</div>
    <p class="note" style="margin-top:8px">Período: ${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)}.</p>
    <div style="margin-top:8px">${tabs("tb", Object.keys(labels).map((k) => [k, labels[k], d.counts[k]]), active)}<p class="note">${help[active]}</p>
    ${card(labels[active], "A coluna 'Maior tempo' mostra em qual status a issue mais ficou.", table(issueCols([
      { k: "maior", label: "Maior tempo", render: (r) => { const e = Object.entries(r.tempo_por_status_d || {})[0]; return e ? `${escx(e[0])}: ${fmtN(e[1], 1)} d` : "—"; } },
      { k: "reaberturas", label: "Reab.", num: true }, { k: "devolucoes", label: "Devol.", num: true }]), d.groups[active], { onClick: openIssue }), "wide")}</div>
    ${card("Por responsável", "Contagens por categoria. Ordem alfabética; olhe padrões do time (ex.: um status que devolve muito), não culpados.", table([{ k: "nome", label: "Responsável" }, ...Object.keys(labels).map((k) => ({ k, label: labels[k], num: true, render: (r) => r[k] || "" }))], d.por_responsavel), "wide")}`;
    $("#tb").onclick = (e) => { const b = e.target.closest("button"); if (b) { active = b.dataset.tab; draw(); } };
  };
  draw();
},

/* ------------------------------------------------------------------ 6 */
async tempo(root, f) {
  const d = await api("time-in-status", f);
  const mx = Math.max(1, ...d.status.map((s) => s.p85_d));
  root.innerHTML = `
  <div class="grid g-kpi">${kpi("Issues analisadas", d.n, { foot: `${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)}` })}
    ${kpi("Eficiência de fluxo", fmtN(d.eficiencia_fluxo_pct, 1) + "%", { foot: "tempo em status ativos ÷ tempo total" })}
    ${kpi("Paradas além do normal", d.abertas_paradas.length, { tone: d.abertas_paradas.length ? "warn" : "ok", foot: "abertas com tempo no status acima de 1,5× a mediana" })}</div>
  <div class="grid g-2" style="margin-top:14px">
  ${card("Tempo por status (concluídas)", "Mediana e P85 em dias · barra = P85", d.status.map((s) => `<div class="bar-row" style="grid-template-columns:120px 1fr 120px"><span>${escx(s.status)}</span><span class="track"><span class="fill" style="display:block;width:${(100 * s.p85_d) / mx}%;background:${s.categoria === "active" ? "var(--c1)" : s.categoria === "blocked" ? "var(--bad)" : "var(--c6)"}"></span></span><b style="text-align:right">${fmtN(s.p50_d, 1)} / ${fmtN(s.p85_d, 1)} d</b></div>`).join("") + legend([["ativo", "var(--c1)"], ["espera/revisão", "var(--c6)"], ["bloqueado", "var(--bad)"]]))}
  ${card("Onde o tempo é gasto", "Participação no tempo total das concluídas", hbars(d.status.map((s) => ({ label: s.status, value: s.participacao_pct })), { color: "var(--c4)", fmt: (v) => fmtN(v, 1) + "%" }))}</div>
  ${card("Mediana por pessoa e status (dias)", "Compara padrões de espera por etapa. Células vazias = sem amostra.", heat(d.matriz.map((m) => m.nome), d.colunas, d.matriz.map((m) => d.colunas.map((c) => m.valores[c] ?? null)), { fmt: (v) => fmtN(v, 1), level: (v) => (v > 6 ? "bad" : v > 3 ? "warn" : "ok"), title: "Mediana por pessoa e status" }), "wide")}
  ${card("Abertas paradas além do normal", "Tempo no status atual comparado com a mediana histórica desse status", table([{ k: "key", label: "Issue", render: (r) => jiraLink(r.key) }, { k: "summary", label: "Resumo", render: (r) => escx(r.summary.slice(0, 55)) }, { k: "assignee", label: "Responsável" }, { k: "status", label: "Status" }, { k: "dias", label: "Dias no status", num: true }, { k: "mediana_status_d", label: "Mediana (d)", num: true }], d.abertas_paradas), "wide")}`;
},

/* ------------------------------------------------------------------ 7 */
async pessoas(root, f) {
  const d = await api("people", f);
  root.innerHTML = `<div class="grid g-kpi">${kpi("Equidade de carga (Jain)", fmtN(d.equidade.jain, 2), { tone: d.equidade.jain >= 0.85 ? "ok" : d.equidade.jain >= 0.7 ? "warn" : "bad", foot: "1,00 = carga proporcional à capacidade" })}
    ${kpi("Maior ocupação", fmtN((d.equidade.max ?? 0) * 100) + "%", { foot: "demanda ÷ capacidade das próximas 4 semanas" })}
    ${kpi("Menor ocupação", fmtN((d.equidade.min ?? 0) * 100) + "%")}</div>
  ${card("Carga por pessoa", `Ordem ${escx(d.ordem)}. Ocupação = demanda das próximas 4 semanas (horas restantes distribuídas até cada vencimento) ÷ capacidade líquida do período (descontadas ausências e feriados).`, table([
    { k: "nome", label: "Pessoa" }, { k: "abertas", label: "Abertas", num: true }, { k: "wip", label: "WIP", num: true },
    { k: "horas_restantes", label: "Fila (h)", num: true, render: (r) => fmtN(r.horas_restantes, 0) },
    { k: "capacidade_4s_h", label: "Capacidade 4 sem. (h)", num: true, render: (r) => fmtN(r.capacidade_4s_h, 0) },
    { k: "utilizacao_4s", label: "Ocupação", num: true, render: (r) => `<div style="min-width:130px"><div class="track" style="background:var(--bg);height:8px;border-radius:5px;overflow:hidden"><div style="height:100%;width:${Math.min(100, r.utilizacao_4s * 100)}%;background:${r.utilizacao_4s > 1.1 ? "var(--bad)" : r.utilizacao_4s > 0.85 ? "var(--warn)" : "var(--ok)"}"></div></div><b>${r.utilizacao_4s > 1.1 ? "▲ " : ""}${fmtN(r.utilizacao_4s * 100)}%</b></div>` },
    { k: "dias_ausencia_4s", label: "Ausência (d)", num: true }, { k: "horas_registradas_30d", label: "Horas reg. 30 d", num: true, render: (r) => fmtN(r.horas_registradas_30d, 0) },
    { k: "itens_acima_p85", label: "> P85", num: true }, { k: "vencidas", label: "Vencidas", num: true }, { k: "itens_risco_alto", label: "Risco alto", num: true },
  ], d.pessoas), "wide")}`;
},

/* ------------------------------------------------------------------ 8 */
async riscos(root, f) {
  const d = await api("risks", f);
  root.innerHTML = `<div class="grid g-kpi">${kpi("Risco alto", d.resumo["alto"] || 0, { tone: "bad" })}${kpi("Risco médio", d.resumo["médio"] || 0, { tone: "warn" })}</div>
  <p class="note" style="margin-top:8px">${escx(d.nota)} Clique numa linha para ver cada fator e quantos pontos ele somou.</p>
  ${card("Issues em risco", "Somente risco médio e alto (pontuação ≥ 30 de 100), explicável fator a fator", table([...issueCols().slice(0, 4), { k: "idade_d", label: "Idade (d)", num: true }, { k: "due", label: "Venc.", render: (r) => fmtDate(r.due) }, { k: "previsao", label: "Previsão", render: (r) => fmtDate(r.previsao) }, riskCol,
    { k: "fatores", label: "Principal fator", render: (r) => escx(r.fatores_risco[0]?.why || "") }], d.issues, { onClick: openIssue }), "wide")}
  ${card("Sinais por pessoa", "Para apoiar e destravar, não para avaliar. Ordem alfabética.", table([{ k: "nome", label: "Pessoa" }, { k: "alto", label: "Risco alto", num: true, render: (r) => r.alto || "" }, { k: "medio", label: "Risco médio", num: true, render: (r) => r.medio || "" }, { k: "idade", label: "Idade > mediana", num: true, render: (r) => r.idade || "" }, { k: "parada", label: "Paradas", num: true, render: (r) => r.parada || "" }, { k: "estouro", label: "Estouro de horas", num: true, render: (r) => r.estouro || "" }], d.sinais_por_pessoa), "wide")}`;
},

/* ------------------------------------------------------------------ 9 */
async previsao(root, f) {
  const d = await api("forecast", f), q = d.quando_termina;
  root.innerHTML = `<div class="grid g-kpi">${kpi("Backlog aberto", d.backlog_aberto, { unit: "itens" })}
    ${["p50", "p85", "p95"].map((k) => kpi(`Termina em (${k.toUpperCase()})`, q.weeks ? q.weeks[k] : null, { unit: "semanas", foot: q.datas ? "até " + fmtDate(q.datas[k]) : "" })).join("")}
    ${kpi("Confiança", q.confidence, { tone: q.confidence === "ok" ? "ok" : "warn", foot: `${q.samples} semanas de histórico (mín. recomendado: 8)` })}</div>
  <div class="grid g-2" style="margin-top:14px">
   ${card("Throughput semanal", "Base da simulação", colChart(d.throughput_semanal, { label: (x) => wk(x.week), value: (x) => x.count, title: "Throughput semanal" }))}
   ${card("Quantos itens até…", "Estimativa conservadora (P85 = 85% das simulações entregam pelo menos isso)", `<table><thead><tr><th>Horizonte</th><th class="num">P50</th><th class="num">P85</th><th class="num">P95</th></tr></thead><tbody>${Object.entries(d.quantos_ate).map(([k, v]) => `<tr><td>${k.replace("s", " semanas")}</td>${["p50", "p85", "p95"].map((p) => `<td class="num">${v.items ? v.items[p] : "—"}</td>`).join("")}</tr>`).join("")}</tbody></table><p class="note">${escx(d.metodo)}</p>`)}</div>
  ${card("Previsão por issue em andamento", "P50/P85: itens históricos mais longos que a idade atual. 'Pela capacidade': fila da pessoa preenchida com horas líquidas (ausências e feriados). 'Atrasa' = alguma previsão passa do vencimento.", table([
    { k: "key", label: "Issue", render: (r) => jiraLink(r.key) }, { k: "assignee", label: "Responsável" }, { k: "status", label: "Status" }, { k: "idade_d", label: "Idade (d)", num: true },
    { k: "previsao_p50", label: "P50", render: (r) => fmtDate(r.previsao_p50) }, { k: "previsao_p85", label: "P85", render: (r) => fmtDate(r.previsao_p85) },
    { k: "previsao_capacidade", label: "Pela capacidade", render: (r) => fmtDate(r.previsao_capacidade) }, { k: "vencimento", label: "Venc.", render: (r) => fmtDate(r.vencimento) },
    { k: "atrasa", label: "Atrasa?", render: (r) => (r.atrasa ? pill("▲ sim", "bad") : pill("não", "ok")) }], d.issues), "wide")}`;
},

/* ------------------------------------------------------------------ 10 */
async gargalos(root, f) {
  const d = await api("bottlenecks", f);
  const keys = d.estados.map((e) => e.status);  // etapas abertas; o concluído acumulado esconderia as demais bandas
  root.innerHTML = `${card("Trabalho em aberto por etapa (60 dias)", "Diagrama de fluxo cumulativo sem a etapa final. Banda que alarga = trabalho acumulando naquela etapa; bandas estáveis = fluxo saudável.", stackedArea(d.cfd, keys, { title: "Trabalho em aberto por etapa" }), "wide")}
  <div class="grid g-2" style="margin-top:14px">
  ${card("Estado das etapas", escx(d.nota), table([{ k: "status", label: "Etapa" }, { k: "itens", label: "Itens", num: true }, { k: "limite_wip", label: "Limite WIP", num: true, render: (r) => r.limite_wip ?? "—" },
    { k: "uso_limite", label: "Uso", num: true, render: (r) => (r.uso_limite != null ? fmtN(r.uso_limite * 100) + "%" : "—") }, { k: "mediana_historica_d", label: "Mediana (d)", num: true }, { k: "variacao_15d", label: "Δ 15 d", num: true, render: (r) => (r.variacao_15d > 0 ? "+" : "") + r.variacao_15d },
    { k: "gargalo", label: "Gargalo?", render: (r) => (r.gargalo ? pill("▲ sim", "bad") : pill("não", "ok")) }], d.estados))}
  ${card("Pessoas acima da capacidade", "Demanda das próximas 4 semanas > 110% da capacidade", d.sobrecarregados.length ? hbars(d.sobrecarregados.map((u) => ({ label: u.nome, value: u.utilizacao_4s * 100 })), { color: "var(--bad)", fmt: (v) => fmtN(v) + "%", max: Math.max(150, ...d.sobrecarregados.map((u) => u.utilizacao_4s * 100)) }) : '<div class="empty">Ninguém acima da capacidade.</div>')}
  ${card("Folga disponível", "Ocupação abaixo de 50%: candidatos naturais para receber trabalho", d.ociosos.length ? hbars(d.ociosos.map((u) => ({ label: u.nome, value: u.utilizacao_4s * 100 })), { color: "var(--ok)", fmt: (v) => fmtN(v) + "%", max: 100 }) : '<div class="empty">Sem folga relevante.</div>')}</div>`;
},

/* ------------------------------------------------------------------ 11 */
async capacidade(root, f) {
  const d = await api("capacity", f);
  const rows = d.pessoas;
  root.innerHTML = `<div class="grid g-kpi">${kpi("Alertas de carga/ausência", d.alertas.length, { tone: d.alertas.length ? "warn" : "ok" })}
    ${kpi("Atrasos previstos", d.atrasos_previstos.length, { tone: d.atrasos_previstos.length ? "bad" : "ok", foot: "previsão pela capacidade > vencimento" })}
    ${kpi("Por causa de ausência", d.atrasos_previstos.filter((x) => x.por_ausencia).length, { tone: "warn", foot: "no prazo se ninguém se ausentasse" })}
    ${kpi("Equidade (Jain)", fmtN(d.equidade.jain, 2))}</div>
  ${card("Mapa de capacidade (próximas semanas)", "Demanda ÷ capacidade líquida de cada semana. Cada issue espalha suas horas até o vencimento (sem vencimento: 2 semanas se iniciada, 4 se não). ▲ = acima de 110%. ‘aus.’ = semana sem capacidade; as horas aparecem para replanejar.", heat(rows.map((r) => r.nome), d.semanas.map(wk), rows.map((r) => r.semanas.slice(0, d.semanas.length).map((s) => (s.capacidade_h < 0.5 ? (s.carga_h > 0.5 ? "▲ " + (s.ausente_dias >= 1 ? "aus. " : "") + fmtN(s.carga_h) + " h" : (s.ausente_dias >= 1 ? "aus." : null)) : s.ocupacao))), { title: "Mapa de capacidade" }), "wide")}
  <div class="grid g-2" style="margin-top:14px">
   ${card("Alertas", "O que olhar antes que vire atraso", d.alertas.length ? `<ul class="stack" style="padding-left:18px">${d.alertas.map((a) => `<li>${pill(a.tipo, a.tipo === "carga" ? "bad" : "warn")} <b>${escx(a.nome)}</b> · sem. ${wk(a.semana)} · ${escx(a.detalhe)}</li>`).join("")}</ul>` : '<div class="empty">Sem alertas.</div>')}
   ${card("Ausências planejadas", "Sem motivo detalhado (privacidade)", table([{ k: "nome", label: "Pessoa" }, { k: "inicio", label: "De", render: (r) => fmtDate(r.inicio) }, { k: "fim", label: "Até", render: (r) => fmtDate(r.fim) }, { k: "tipo", label: "Tipo" }, { k: "dias_uteis", label: "Dias úteis", num: true }], d.ausencias))}
   ${card("Feriados no horizonte", "", d.feriados.length ? `<ul>${d.feriados.map((h) => `<li>${fmtDate(h.dia)} · ${escx(h.nome)}</li>`).join("")}</ul>` : '<div class="empty">Nenhum.</div>')}
   ${card("Rebalancear?", "O sistema propõe mover só issues não iniciadas, o mínimo necessário.", `<a href="#/rebalanceamento"><button>Ver sugestões de rebalanceamento</button></a>`)}</div>
  ${card("Atrasos previstos pela capacidade", "Issues que, pela fila e pelas horas líquidas da pessoa, terminam depois do vencimento", table([{ k: "key", label: "Issue", render: (r) => jiraLink(r.key) }, { k: "summary", label: "Resumo", render: (r) => escx(r.summary.slice(0, 55)) }, { k: "assignee", label: "Responsável" }, { k: "vencimento", label: "Venc.", render: (r) => fmtDate(r.vencimento) }, { k: "previsao", label: "Previsão", render: (r) => (r.previsao.includes("-") ? fmtDate(r.previsao) : escx(r.previsao)) }, { k: "por_ausencia", label: "Causa", render: (r) => (r.por_ausencia ? pill("ausência", "warn") : pill("carga", "bad")) }], d.atrasos_previstos), "wide")}`;
},

/* ------------------------------------------------------------------ 12 */
async distribuicao(root, f) {
  const pend = await api("distribution/pending", f);
  const sugg = await api("distribution/suggestions", { status: "pendente", squad: f.squad });
  const draw = (preview) => {
    root.innerHTML = `<div class="grid g-kpi">${kpi("Aguardando distribuição", pend.issues.length, { foot: "A fazer, sem responsável ou no nome do gerente" })}${kpi("Sugestões pendentes", sugg.filter((s) => s.tipo === "novo").length)}</div>
    <div class="banner" style="margin:12px 0">Modo <b>sugerir e aprovar</b>: o sistema calcula e explica; nada é atribuído sem decisão de um gerente. ${state.sync?.escrita_no_jira ? "Escrita no Jira <b>ligada</b>." : state.sync?.mode === "demo" ? "Demonstração: a aprovação altera só os dados fictícios." : "Escrita no Jira <b>desligada</b> (modo sombra): a decisão fica registrada, sem gravar no Jira."}</div>
    <div class="row" style="margin-bottom:12px"><button id="gen">Gerar sugestões para a seleção atual</button><span class="note">Critério: carga ÷ capacidade (descontando ausências), limite de WIP, competência e afinidade. Produtividade entra apenas como peso limitado (±15%).</span></div>
    ${preview ? preview : ""}
    ${card("Pendentes de distribuição", "", table(issueCols([{ k: "gerente", label: "Gerente" }, { k: "horas", label: "Horas est.", num: true }]), pend.issues, { onClick: openIssue }), "wide")}
    ${card("Sugestões aguardando decisão", "", sugg.filter((s) => s.tipo === "novo").length ? suggList(sugg.filter((s) => s.tipo === "novo")) : '<div class="empty">Nenhuma sugestão pendente. Gere sugestões acima.</div>', "wide")}`;
    $("#gen").onclick = async () => { $("#gen").disabled = true; const r = await api("distribution/suggest", {}, { method: "POST", body: JSON.stringify({ squad: f.squad || null }) }); SCREENS.distribuicao(root, f, r); };
    wireDecisions(() => SCREENS.distribuicao(root, f));
  };
  let prev = "";
  if (arguments[2]) {
    prev = arguments[2].squads.map((s) => `<section class="card wide" style="margin-bottom:12px"><h3>${escx(s.squad)}: equidade antes ${fmtN(s.equidade_antes.jain, 2)} → depois ${fmtN(s.equidade_depois.jain, 2)}</h3><p class="sub">Maior ocupação projetada: ${fmtN((s.equidade_depois.max ?? 0) * 100)}% · menor: ${fmtN((s.equidade_depois.min ?? 0) * 100)}%. ${arguments[2].aviso}</p></section>`).join("");
    const fresh = await api("distribution/suggestions", { status: "pendente", squad: f.squad });
    sugg.length = 0; sugg.push(...fresh);
  }
  draw(prev);
},

/* ------------------------------------------------------------------ 13 */
async rebalanceamento(root, f) {
  const sugg = (await api("distribution/suggestions", { status: "pendente", squad: f.squad })).filter((s) => s.tipo === "rebalanceamento");
  root.innerHTML = `<div class="banner" style="margin:0 0 12px">Gatilho: ocupação projetada acima de 110% nas próximas 4 semanas (inclui o efeito de férias e licenças). Só issues <b>não iniciadas</b> são movidas, para não gerar troca de contexto. Há histerese: sem ganho claro, nada é movido.</div>
  <div class="row" style="margin-bottom:12px"><button id="gen">Calcular rebalanceamento</button></div><div id="res"></div>
  ${card("Movimentos aguardando decisão", "", sugg.length ? suggList(sugg) : '<div class="empty">Nenhum movimento pendente.</div>', "wide")}`;
  $("#gen").onclick = async () => {
    const r = await api("rebalance/suggest", { squad: f.squad }, { method: "POST" });
    $("#res").innerHTML = r.squads.map((s) => `<section class="card wide" style="margin-bottom:12px"><h3>${escx(s.squad)}: ${s.movimentos.length} movimento(s)</h3><p class="sub">Equidade (Jain) ${fmtN(s.equidade_antes.jain, 2)} → ${fmtN(s.equidade_depois.jain, 2)} · maior ocupação ${fmtN((s.equidade_antes.max ?? 0) * 100)}% → ${fmtN((s.equidade_depois.max ?? 0) * 100)}%</p>${s.movimentos.length ? "" : '<span class="note">Nada a mover: carga dentro do limite.</span>'}</section>`).join("");
    setTimeout(() => SCREENS.rebalanceamento(root, f), 600);
  };
  wireDecisions(() => SCREENS.rebalanceamento(root, f));
},

/* ------------------------------------------------------------------ 14 */
async qualidade(root, f) {
  const d = await api("quality", f);
  const names = { sem_estimativa: "Sem estimativa", sem_responsavel: "Sem responsável", sem_vencimento: "Sem vencimento", sem_epico: "Sem épico", em_andamento_sem_horas: "Em andamento sem horas registradas", sem_squad: "Sem squad mapeado", prioridade_padrao: "Prioridade padrão (média)" };
  root.innerHTML = `<p class="note">${escx(d.nota)}</p><div class="grid g-3" style="margin-top:10px">${d.checks.map((c) => `<section class="card"><h3>${names[c.id]}</h3><div class="val" style="font-size:1.8rem;font-weight:650">${c.qtd} <small class="note">de ${d.total_abertas} (${fmtN(c.pct, 1)}%)</small></div><div class="track" style="background:var(--bg);height:8px;border-radius:5px;margin:8px 0;overflow:hidden"><div style="height:100%;width:${c.pct}%;background:${c.pct > 30 ? "var(--bad)" : c.pct > 10 ? "var(--warn)" : "var(--ok)"}"></div></div><div class="note">${c.exemplos.map(escx).join(", ")}</div></section>`).join("")}</div>`;
},

/* ------------------------------------------------------------------ 15 */
async auditoria(root) {
  const [a, s] = await Promise.all([api("audit"), api("distribution/suggestions")]);
  root.innerHTML = `${card("Decisões de distribuição", "Quem decidiu, o que o sistema sugeriu e o que foi feito", table([{ k: "criada", label: "Quando", render: (r) => fmtDate(r.criada.slice(0, 10)) }, { k: "issue", label: "Issue", render: (r) => jiraLink(r.issue) }, { k: "tipo", label: "Tipo" }, { k: "de", label: "De", render: (r) => escx(r.de || "—") }, { k: "sugerido", label: "Sugerido" }, { k: "final", label: "Final", render: (r) => escx(r.final || "—") }, { k: "status", label: "Status", render: (r) => pill(r.status, r.status === "pendente" ? "warn" : r.status === "rejeitada" ? "mute" : "ok") }, { k: "decidido_por", label: "Decidido por", render: (r) => escx(r.decidido_por || "—") }, { k: "motivo_override", label: "Motivo da troca", render: (r) => escx(r.motivo_override || "") }], s), "wide")}
  ${card("Registro de auditoria", "", table([{ k: "quando", label: "Quando", render: (r) => r.quando.replace("T", " ").slice(0, 16) }, { k: "quem", label: "Quem" }, { k: "acao", label: "Ação" }, { k: "detalhe", label: "Detalhe", render: (r) => `<code>${escx(JSON.stringify(r.detalhe).slice(0, 140))}</code>` }], a), "wide")}`;
},

/* ------------------------------------------------------------------ 16 */
async meu(root, f) {
  if (!f.person) { root.innerHTML = '<div class="empty">Escolha uma pessoa no filtro “Pessoa” para ver o painel individual (visão da própria pessoa).</div>'; return; }
  const [o, p, c, r] = await Promise.all([api("open", f), api("people", { ...f }), api("capacity", { ...f }), api("risks", f)]);
  const me = p.pessoas[0];
  root.innerHTML = `<div class="grid g-kpi">${kpi("Minhas abertas", o.total)}${me ? kpi("Ocupação 4 semanas", fmtN(me.utilizacao_4s * 100) + "%", { tone: ocTone(me.utilizacao_4s) }) : ""}${kpi("Em risco", r.issues.length, { tone: r.issues.length ? "warn" : "ok" })}${kpi("Ausências futuras", c.ausencias.length)}</div>
  ${card("Minha fila", "Por prioridade e vencimento", table(issueCols([{ k: "previsao", label: "Previsão", render: (x) => fmtDate(x.previsao) }, riskCol]), o.issues, { onClick: openIssue }), "wide")}`;
},

/* ------------------------------------------------------------------ 17 */
async sync(root) {
  const s = await api("sync/status");
  root.innerHTML = `<div class="grid g-2">${card("Conexão", "", `<table><tbody>
    <tr><th>Modo</th><td>${s.mode === "demo" ? pill("DEMONSTRAÇÃO (dados fictícios)", "warn") : pill("Jira", "ok")}</td></tr>
    <tr><th>Variante</th><td>${escx(s.flavor)}</td></tr><tr><th>Servidor</th><td>${escx(s.jira || "—")}</td></tr>
    <tr><th>Última sincronização</th><td>${escx(s.ultima_sincronizacao || "—")}</td></tr><tr><th>Intervalo</th><td>${s.intervalo_min} min</td></tr>
    <tr><th>Escrita no Jira</th><td>${s.escrita_no_jira ? pill("ligada", "warn") : pill("desligada (modo sombra)", "ok")}</td></tr></tbody></table>
    <div class="row" style="margin-top:12px"><button id="s1" ${s.mode === "demo" ? "disabled" : ""}>Sincronizar agora</button><button id="s2" class="ghost" ${s.mode === "demo" ? "disabled" : ""}>Sincronização completa</button></div><div id="out" class="note"></div>`)}
    ${card("Como ligar ao seu Jira", "", `<ol class="stack"><li>Copie <code>config/equipes.exemplo.yaml</code> para <code>config/equipes.yaml</code> e informe squads, pessoas, status e projetos.</li><li>Defina <code>JIRADASH_MODE=jira</code>, <code>JIRA_FLAVOR</code> (cloud|dc), <code>JIRA_BASE_URL</code>, <code>JIRA_TOKEN</code> (+ <code>JIRA_EMAIL</code> no Cloud).</li><li>Rode <code>python -m jiradash.cli check</code> e depois <code>python -m jiradash.cli sync --full</code>.</li><li>Mantenha a escrita desligada até validar as sugestões em modo sombra.</li></ol><p class="note">Detalhes em <code>app/README.md</code>.</p>`)}</div>`;
  const run = (full) => async () => { $("#out").textContent = "Sincronizando…"; try { const r = await api("sync", { full }, { method: "POST" }); $("#out").textContent = JSON.stringify(r); } catch (e) { $("#out").textContent = e.message; } };
  $("#s1").onclick = run(false); $("#s2").onclick = run(true);
},
};

/* Lista de sugestões com explicação, aprovação, troca e rejeição */
function suggList(items) {
  return `<div class="stack">${items.map((s) => `<div class="card" style="box-shadow:none"><div class="row" style="justify-content:space-between"><div><b>${jiraLink(s.issue)}</b> ${escx(s.resumo.slice(0, 60))} ${s.tipo === "rebalanceamento" ? pill("mover de " + (s.de || "—"), "info") : ""}</div><div>→ <b>${escx(s.sugerido)}</b></div></div>
    <p class="note" style="margin:6px 0">${escx(s.motivo)}</p>
    <details><summary class="note">Ver ranking e fatores do score</summary>${table([{ k: "name", label: "Pessoa" }, { k: "score", label: "Score (menor é melhor)", num: true, render: (r) => fmtN(r.score, 2) }, { k: "carga", label: "Carga proj.", num: true, render: (r) => fmtN(r.factors.carga_projetada, 2) }, { k: "wip", label: "WIP", num: true, render: (r) => fmtN(r.factors.wip, 2) }, { k: "skill", label: "Competência", num: true, render: (r) => fmtN(r.factors.competencia_faltante, 2) }, { k: "afin", label: "Afinidade", num: true, render: (r) => fmtN(r.factors.afinidade, 2) }, { k: "cap", label: "Cap./carga (h)", render: (r) => `${fmtN(r.detail.capacidade_h)} / ${fmtN(r.detail.carga_h)}` }], s.ranking)}</details>
    <div class="row" style="margin-top:8px"><button data-ok="${s.id}">Aprovar</button>
      <select data-alt="${s.id}" aria-label="Escolher outra pessoa"><option value="">Escolher outra pessoa…</option>${(state.meta.people.filter((p) => p.role !== "gerente")).map((p) => `<option value="${p.id}">${escx(p.name)}</option>`).join("")}</select>
      <input type="text" data-why="${s.id}" placeholder="Motivo (obrigatório se trocar)" size="26"><button class="ghost" data-alt-ok="${s.id}">Aprovar com troca</button><button class="danger" data-no="${s.id}">Rejeitar</button></div></div>`).join("")}</div>`;
}
function wireDecisions(reload) {
  const post = async (id, body) => { try { await api(`distribution/${id}/decide`, {}, { method: "POST", body: JSON.stringify(body) }); reload(); } catch (e) { alert(e.message); } };
  document.querySelectorAll("[data-ok]").forEach((b) => (b.onclick = () => post(b.dataset.ok, { action: "aprovar" })));
  document.querySelectorAll("[data-no]").forEach((b) => (b.onclick = () => post(b.dataset.no, { action: "rejeitar", override_reason: prompt("Motivo da rejeição (opcional):") || null })));
  document.querySelectorAll("[data-alt-ok]").forEach((b) => (b.onclick = () => { const id = b.dataset.altOk, p = document.querySelector(`[data-alt="${id}"]`).value, w = document.querySelector(`[data-why="${id}"]`).value; if (!p) return alert("Escolha a pessoa."); post(id, { action: "aprovar", person_id: +p, override_reason: w }); }));
}
