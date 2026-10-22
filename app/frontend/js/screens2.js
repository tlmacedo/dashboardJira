/* Telas: metas e prazos, checklist x review, integridade e épicos */
const BUCKET_COLORS = ["var(--ok)", "var(--c2)", "var(--c1)", "var(--c6)", "var(--c3)", "var(--bad)"];
const periodLabel = (k, g) => (g === "month" ? k.slice(5, 7) + "/" + k.slice(2, 4) : fmtDM(k));
const pctCell = (v, meta) => (v == null ? "—" : `${v >= meta ? "✓" : "▲"} ${fmtN(v, 1)}%`);

SCREENS.metas = async function (root, f) {
  const g = f.granularity || "week";
  KD({ "No prazo (até o vencimento)": ["meta", "no_prazo"], "No prazo pela data original": ["meta", "orig_no_prazo"], "Antecipadas (D-1 ou antes)": ["meta", "antecipadas"], "Em D-1 ou D-2": ["meta", "d1d2"], "No dia do vencimento (D)": ["meta", "no_dia"], "Atrasadas": ["meta", "atrasadas"], "Sem vencimento": ["meta", "sem_venc"] });
  const d = await api("goals", { ...f, granularity: g });
  const k = d.kpis, meta = d.metas.no_prazo_pct;
  const bk = d.buckets;
  const dropTone = k.no_prazo_pct != null && k.no_prazo_data_original_pct != null && k.no_prazo_pct - k.no_prazo_data_original_pct >= 5 ? "warn" : "";
  const dimOf = { Tipo: "tipo", Prioridade: "prioridade", Squad: "squad", Pessoa: "pessoa" };
  const groupTable = (rows, label) => table([{ k: "nome", label }, { k: "total", label: "Com venc.", num: true, render: (r) => dlLink(r.total, "meta", "all", `${dimOf[label]}=${label === "Pessoa" ? r.chave ?? "None" : r.nome}`) },
    ...bk.map((b) => ({ k: b, label: b, num: true, render: (r) => dlLink(r[b] || "", "meta", b, `${dimOf[label]}=${label === "Pessoa" ? r.chave ?? "None" : r.nome}`) })),
    { k: "no_prazo_pct", label: "No prazo", num: true, render: (r) => pillTone(r.no_prazo_pct, meta) },
    { k: "antecipadas_pct", label: "Antecipadas", num: true, render: (r) => fmtN(r.antecipadas_pct, 1) + "%" },
    { k: "no_prazo_data_original_pct", label: "Pela data original", num: true, render: (r) => fmtN(r.no_prazo_data_original_pct, 1) + "%" }], rows);
  root.innerHTML = `<div class="row" style="margin-bottom:12px"><span class="note">Período:</span>${[["day", "Dia"], ["week", "Semana"], ["month", "Mês"]].map(([v, l]) => `<button class="${g === v ? "" : "ghost"}" data-g="${v}">${l}</button>`).join("")}<span class="note">${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)} · meta de prazo ${meta}% · meta de antecipação ${d.metas.antecipadas_pct}%</span></div>
  <div class="grid g-kpi">
    ${kpi("No prazo (até o vencimento)", pctCell(k.no_prazo_pct, meta), { tone: k.no_prazo_pct >= meta ? "ok" : "bad", foot: `meta ${meta}%` })}
    ${kpi("No prazo pela data original", k.no_prazo_data_original_pct != null ? fmtN(k.no_prazo_data_original_pct, 1) + "%" : null, { tone: dropTone, foot: "ignora prorrogações de vencimento" })}
    ${kpi("Antecipadas (D-1 ou antes)", k.antecipadas_pct != null ? fmtN(k.antecipadas_pct, 1) + "%" : null, { foot: `meta ${d.metas.antecipadas_pct}%` })}
    ${kpi("Em D-1 ou D-2", k.d1_d2_pct != null ? fmtN(k.d1_d2_pct, 1) + "%" : null, { foot: "entregues 1 ou 2 dias antes" })}
    ${kpi("No dia do vencimento (D)", k.no_dia_pct != null ? fmtN(k.no_dia_pct, 1) + "%" : null, { foot: "concentração no limite merece atenção" })}
    ${kpi("Atrasadas", k.atrasadas_pct != null ? fmtN(k.atrasadas_pct, 1) + "%" : null, { tone: k.atrasadas_pct > 100 - meta ? "bad" : "ok" })}
    ${kpi("Sem vencimento", k.sem_vencimento, { tone: k.sem_vencimento ? "warn" : "ok", foot: `de ${k.concluidas} concluídas` })}</div>
  <div class="grid g-2" style="margin-top:14px">
   ${card("Quando as entregas acontecem", "Dia da resolução em relação ao vencimento, por período", stackedCols(d.serie.map((s) => ({ label: periodLabel(s.periodo, g), ...s })), bk, BUCKET_COLORS, { title: "Entregas por período e dia relativo ao vencimento", drillOf: (r, k) => ["meta", k, `periodo=${r.periodo}&g=${g}`] }))}
   ${card("% no prazo por período vs. meta", "Linha tracejada = meta", lineChart([{ name: "% no prazo", data: d.serie.map((s) => s.no_prazo_pct) }, { name: "Meta", data: d.serie.map(() => meta), dash: true, color: "var(--ok)" }], { xs: d.serie.map((s) => periodLabel(s.periodo, g)), yfmt: (v) => fmtN(v) + "%", title: "% no prazo por período" }))}
   ${card("Distribuição", "Todas as concluídas com vencimento", hbars(d.distribuicao.map((x) => ({ label: x.bucket, value: x.qtd, drill: ["meta", x.bucket] })), { colorOf: (x) => BUCKET_COLORS[bk.indexOf(x.label)] }))}
   ${card("Como ler", "", `<p class="note">“No prazo” conta D-3 ou antes, D-2, D-1 e D. “Pela data original” usa o vencimento antes de qualquer prorrogação: se for bem menor que o resultado atual, há prazos sendo empurrados (veja <a href="#/integridade">Integridade</a>). Muita entrega em D e pouca em D-1/D-2 indica trabalho no limite, e não necessariamente problema.</p>`)}</div>
  ${card("Por tipo de issue", "", groupTable(d.por_tipo, "Tipo"), "wide")}
  ${card("Por prioridade", "", groupTable(d.por_prioridade, "Prioridade"), "wide")}
  ${card("Por squad", "", groupTable(d.por_squad, "Squad"), "wide")}
  ${card("Por pessoa", "Ordem alfabética. ✓ = meta atingida. 'Pela data original' mais baixo que 'No prazo' mostra prazos prorrogados.", groupTable(d.por_pessoa, "Pessoa"), "wide")}`;
  root.querySelectorAll("[data-g]").forEach((b) => (b.onclick = () => go("metas", { ...parseHash().f, granularity: b.dataset.g })));
};
const pillTone = (v, meta) => (v == null ? "—" : pill((v >= meta ? "✓ " : "▲ ") + fmtN(v, 1) + "%", v >= meta ? "ok" : v >= meta - 10 ? "warn" : "bad"));

/* ------------------------------------------------------------------ checklist x review */
SCREENS.review = async function (root, f) {
  KD({ "Pares checklist + review": ["r_pares", "all"], "Checklists sem review": ["r_sem_review"], "Review vence depois do pai": ["r_pares", "alerta", "review vence depois do checklist"], "Mesma pessoa nos dois": ["r_pares", "alerta", "mesma pessoa no checklist e na review"], "Pares com alerta": ["r_pares", "com_alerta"] });
  const d = await api("review", f), k = d.kpis;
  const lab = (arr, cls = "mute") => (arr.length ? arr.map((l) => pill(l, cls)).join(" ") : '<span class="note">—</span>');
  root.innerHTML = `<div class="grid g-kpi">
    ${kpi("Pares checklist + review", k.pares, { foot: `${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)}` })}
    ${kpi("Checklists sem review", k.checklists_sem_review, { tone: k.checklists_sem_review ? "warn" : "ok" })}
    ${kpi("Ciclo da review (P50)", fmtN(k.review_ciclo_p50_d, 1), { unit: "d", foot: `checklist: ${fmtN(k.checklist_ciclo_p50_d, 1)} d` })}
    ${kpi("Review no ciclo do checklist", k.review_pct_do_ciclo_p50 != null ? fmtN(k.review_pct_do_ciclo_p50, 1) + "%" : null, { foot: "mediana da fatia de tempo" })}
    ${kpi("Review criada após o pai (P50)", fmtN(k.criacao_review_p50_h, 1), { unit: "h" })}
    ${kpi("Review vence depois do pai", k.venc_review_depois_pct != null ? fmtN(k.venc_review_depois_pct, 1) + "%" : null, { tone: k.venc_review_depois_pct > 10 ? "warn" : "ok" })}
    ${kpi("Mesma pessoa nos dois", k.mesma_pessoa_pct != null ? fmtN(k.mesma_pessoa_pct, 1) + "%" : null, { tone: k.mesma_pessoa_pct > 10 ? "warn" : "ok", foot: "sem revisão independente" })}
    ${kpi("Pares com alerta", k.com_alerta_pct != null ? fmtN(k.com_alerta_pct, 1) + "%" : null, { tone: k.com_alerta_pct > 30 ? "bad" : "" })}</div>
  ${card("O que os dados sugerem", "Frases geradas automaticamente quando há amostra suficiente. Correlação, não prova de causa.", d.aprendizados.length ? `<ul class="stack" style="padding-left:18px">${d.aprendizados.map((t) => `<li>${escx(t)}</li>`).join("")}</ul>` : '<div class="empty">Amostra ainda pequena para tirar conclusões.</div>', "wide")}
  <div class="grid g-2" style="margin-top:14px">
   ${card("Duração ativa da review x reabertura do checklist", "Reviews muito curtas deixam passar mais problemas?", colChart(d.por_duracao_review.map((x) => ({ label: `${x.faixa} (n=${x.n})`, value: x.reabertura_pct || 0, faixa: x.faixa })), { color: "var(--c3)", label: (x) => x.label, value: (x) => x.value, title: "Reabertura por duração da review", drill: (x) => ["r_pares", "faixa", x.faixa] }) + `<p class="note">Eixo: % de checklists reabertos em cada faixa de duração da review.</p>`)}
   ${card("Quem atrasa quem", "Efeito cruzado do prazo da review no checklist", `<table><tbody><tr><th>Review atrasada → checklist atrasado</th><td class="num">${fmtN(d.contingencia.review_atrasada_checklist_atrasado_pct, 1)}%</td></tr><tr><th>Review no prazo → checklist atrasado</th><td class="num">${fmtN(d.contingencia.review_no_prazo_checklist_atrasado_pct, 1)}%</td></tr></tbody></table><p class="note">Se a primeira linha for bem maior que a segunda, o atraso da review contamina o checklist.</p>`)}
   ${card("Labels e resultado do checklist", "Lift = reabertura com a label ÷ reabertura média. Amostras pequenas (n < 5) não são confiáveis.", table([{ k: "label", label: "Label", render: (r) => pill(r.label) }, { k: "n", label: "Pares", num: true, render: (r) => dlLink(r.n, "r_pares", "label", r.label) }, { k: "reabertura_pct", label: "Reabertura", num: true, render: (r) => fmtN(r.reabertura_pct, 1) + "%" }, { k: "lift_reabertura", label: "Lift", num: true, render: (r) => (r.n < 5 ? '<span class="note">n baixo</span>' : fmtN(r.lift_reabertura, 2) + "×") }, { k: "atraso_pct", label: "Checklist atrasado", num: true, render: (r) => fmtN(r.atraso_pct, 1) + "%" }, { k: "ciclo_p50_d", label: "Ciclo P50 (d)", num: true }], d.labels))}
   ${card("Diferenças de labels", "Labels que aparecem só de um lado do par (exclui a label técnica 'review')", table([{ k: "onde", label: "Onde" }, { k: "label", label: "Label", render: (r) => pill(r.label) }, { k: "pares", label: "Pares", num: true, render: (r) => dlLink(r.pares, "r_pares", r.onde === "só no checklist" ? "so_checklist" : "so_review", r.label) }], d.diferencas_labels))}</div>
  ${card("Pares", "Clique para comparar lado a lado. Ordenado por quantidade de alertas.", table([
    { k: "checklist", label: "Checklist", render: (r) => jiraLink(r.checklist) }, { k: "review", label: "Review", render: (r) => jiraLink(r.review) },
    { k: "checklist_resp", label: "Resp. checklist", render: (r) => dlLink(r.checklist_resp, "r_pares", "pessoa", String(r.checklist_resp_id ?? "None")) }, { k: "review_resp", label: "Resp. review", render: (r) => dlLink(r.review_resp, "r_pares", "pessoa", String(r.review_resp_id ?? "None")) },
    { k: "venc_checklist", label: "Venc. checklist", render: (r) => fmtDate(r.venc_checklist) }, { k: "venc_review", label: "Venc. review", render: (r) => fmtDate(r.venc_review) },
    { k: "dif_vencimento_d", label: "Δ venc. (d)", num: true, render: (r) => (r.dif_vencimento_d == null ? "—" : (r.dif_vencimento_d > 0 ? "▲ +" : "") + r.dif_vencimento_d) },
    { k: "ciclo_review_d", label: "Ciclo review (d)", num: true }, { k: "review_ativa_h", label: "Review ativa (h)", num: true },
    { k: "reaberturas_checklist", label: "Reab. pai", num: true },
    { k: "alertas", label: "Alertas", render: (r) => (r.alertas.length ? r.alertas.map((a) => dlLink(a, "r_pares", "alerta", a)).join(" · ") : pill("ok", "ok")) }], d.pares, { onClick: openPair }), "wide")}
  ${d.sem_review.length ? card("Checklists sem review", "", table([{ k: "key", label: "Issue", render: (r) => jiraLink(r.key) }, { k: "resumo", label: "Resumo" }, { k: "resp", label: "Responsável" }, { k: "status", label: "Status" }], d.sem_review), "wide") : ""}`;
};

async function openPair(row) {
  closeDrawer();
  const d = await api("review/pair", { key: row.checklist });
  const r = d.reviews[0] || {};
  const tl = (t) => `<ol style="padding-left:16px;margin:4px 0">${t.map((x) => `<li><small>${escx(x.quando)}</small> ${escx(x.de || "—")} → <b>${escx(x.para)}</b> <small>${escx(x.quem)}</small></li>`).join("")}</ol>`;
  const stat = (o) => hbars(Object.entries(o).map(([k, v]) => ({ label: k, value: v })), { fmt: (v) => fmtN(v, 1) + " d" });
  document.body.insertAdjacentHTML("beforeend", `<aside class="drawer" style="width:min(760px,100vw)" role="dialog" aria-label="Comparação checklist e review"><button class="ghost x" onclick="closeDrawer()" aria-label="Fechar">✕</button>
   <h2>${escx(row.resumo)}</h2><div class="row">${(r.alertas || []).map((a) => pill(a, "warn")).join(" ") || pill("sem alertas", "ok")}</div>
   <div class="grid" style="grid-template-columns:1fr 1fr;margin-top:10px"><section class="card"><h3>Checklist ${jiraLink(row.checklist)}</h3><p class="note">${escx(r.checklist_resp)} · ${escx(r.status_checklist)}</p>
     <table><tbody><tr><th>Criado</th><td>${fmtDate(r.criacao_checklist)}</td></tr><tr><th>Vencimento</th><td>${fmtDate(r.venc_checklist)}</td></tr><tr><th>Resolvido</th><td>${fmtDate(r.resolvido_checklist)}</td></tr><tr><th>Ciclo</th><td>${fmtN(r.ciclo_checklist_d, 1)} d</td></tr><tr><th>Horas</th><td>${fmtN(r.horas_checklist, 1)} h</td></tr><tr><th>Reaberturas/devol.</th><td>${r.reaberturas_checklist}/${r.devolucoes_checklist}</td></tr></tbody></table>
     <div style="margin-top:6px">${(d.checklist.labels || []).map((l) => pill(l)).join(" ")}</div>${tl(d.checklist.timeline)}${stat(d.checklist.status_d)}</section>
    <section class="card"><h3>Review ${jiraLink(row.review)}</h3><p class="note">${escx(r.review_resp)} · ${escx(r.status_review)}</p>
     <table><tbody><tr><th>Criada</th><td>${fmtDate(r.criacao_review)} (+${fmtN(r.atraso_criacao_review_h, 1)} h)</td></tr><tr><th>Vencimento</th><td>${fmtDate(r.venc_review)} (${r.dif_vencimento_d == null ? "—" : (r.dif_vencimento_d > 0 ? "+" : "") + r.dif_vencimento_d + " d vs. checklist"})</td></tr><tr><th>Resolvida</th><td>${fmtDate(r.resolvido_review)} (${fmtN(r.review_antes_do_checklist_d, 1)} d antes do pai)</td></tr><tr><th>Ciclo</th><td>${fmtN(r.ciclo_review_d, 1)} d (${fmtN(r.review_pct_do_ciclo, 0)}% do pai)</td></tr><tr><th>Ativa / horas</th><td>${fmtN(r.review_ativa_h, 1)} h / ${fmtN(r.horas_review, 1)} h</td></tr></tbody></table>
     <div style="margin-top:6px">${(d.reviews[0]?.labels || []).map((l) => pill(l)).join(" ")}</div>${d.reviews[0] ? tl(d.reviews[0].timeline) : ""}${d.reviews[0] ? stat(d.reviews[0].status_d) : ""}</section></div>
   <h3 style="margin:14px 0 4px;font-size:.9rem">Labels</h3><table><tbody><tr><th>Em comum</th><td>${(r.labels_comuns || []).map((l) => pill(l, "ok")).join(" ") || "—"}</td></tr><tr><th>Só no checklist</th><td>${(r.so_checklist || []).map((l) => pill(l, "info")).join(" ") || "—"}</td></tr><tr><th>Só na review</th><td>${(r.so_review || []).map((l) => pill(l, "warn")).join(" ") || "—"}</td></tr></tbody></table></aside>`);
}

/* ------------------------------------------------------------------ integridade */
SCREENS.integridade = async function (root, f) {
  KD({ "Evidências": ["i_sinal"], "Pessoas em atenção alta": ["i_alto", "alto"], "Em atenção média": ["i_alto", "médio"], "Issues afetadas": ["i_sinal"] });
  const d = await api("integrity", f), rs = d.resumo;
  let sig = "", who = "";
  const tone = { alto: "bad", médio: "warn", baixo: "ok" };
  const people = [...d.pessoas].sort((a, b) => b.pontos - a.pontos);
  const drawEv = () => {
    const rows = d.evidencias.filter((e) => (!sig || e.sinal === sig) && (!who || String(e.pessoa_id) === who));
    $("#ev").innerHTML = table([{ k: "gravidade", label: "Grav.", num: true, render: (r) => pill("▲".repeat(r.gravidade), r.gravidade >= 3 ? "bad" : r.gravidade === 2 ? "warn" : "mute") },
      { k: "sinal", label: "Sinal", render: (r) => escx((d.catalogo.find((c) => c.sinal === r.sinal) || {}).nome || r.sinal) }, { k: "pessoa", label: "Pessoa" }, { k: "issue", label: "Issue", render: (r) => jiraLink(r.issue) },
      { k: "resumo", label: "Resumo" }, { k: "quando", label: "Quando" }, { k: "detalhe", label: "Evidência" }], rows, { max: 150 });
  };
  root.innerHTML = `<div class="banner" style="margin:0 0 12px">⚠ ${escx(d.aviso)}</div>
  <div class="grid g-kpi">${kpi("Evidências", rs.evidencias, { foot: `${fmtDate(d.periodo.de)} a ${fmtDate(d.periodo.ate)}` })}${kpi("Pessoas em atenção alta", rs.pessoas_alto, { tone: rs.pessoas_alto ? "bad" : "ok" })}${kpi("Em atenção média", rs.pessoas_medio, { tone: rs.pessoas_medio ? "warn" : "ok" })}${kpi("Issues afetadas", rs.issues_afetadas)}${kpi("Mediana da taxa", fmtN(d.mediana_taxa, 2), { foot: "pontos por issue concluída; base de comparação" })}</div>
  <div class="grid g-2" style="margin-top:14px">
   ${card("Evidências por sinal", "Gravidade ▲ a ▲▲▲", hbars(rs.por_sinal.map((s) => ({ label: s.nome, value: s.qtd, g: s.gravidade, drill: ["i_sinal", s.sinal] })), { colorOf: (x) => (x.g >= 3 ? "var(--bad)" : x.g === 2 ? "var(--warn)" : "var(--c5)") }))}
   ${card("Impacto nas metas", "Diferença entre % no prazo atual e pela data original (pontos percentuais). Positivo alto = prazos empurrados.", hbars(people.filter((p) => p.impacto_na_meta_pp > 0).map((p) => ({ label: p.nome, value: p.impacto_na_meta_pp })), { color: "var(--warn)", fmt: (v) => "+" + fmtN(v, 1) + " pp", max: 50 }) )}</div>
  ${card("Pessoas por nível de atenção", `Taxa = pontos de gravidade ÷ concluídas (mín. 8) comparada com a mediana da equipe. ${escx(d.regras_nivel)} Ordenado por pontos; não é nota de desempenho.`, table([
    { k: "nome", label: "Pessoa", render: (r) => dlLink(r.nome, "i_pessoa", String(r.person_id)) }, { k: "nivel", label: "Atenção", render: (r) => pill((r.nivel === "alto" ? "▲ " : r.nivel === "médio" ? "● " : "✓ ") + r.nivel, tone[r.nivel]) }, { k: "pontos", label: "Pontos", num: true },
    { k: "vezes_a_mediana", label: "× mediana", num: true, render: (r) => fmtN(r.vezes_a_mediana, 1) + "×" }, { k: "concluidas", label: "Concluídas", num: true },
    { k: "sinais", label: "Sinais", render: (r) => Object.entries(r.sinais).map(([s, c]) => `<span class="pill mute">${dlLink(`${(d.catalogo.find((x) => x.sinal === s) || {}).nome || s}: ${c}`, "i_sinal", s, String(r.person_id))}</span>`).join(" ") },
    { k: "no_prazo_pct", label: "No prazo", num: true, render: (r) => (r.no_prazo_pct == null ? "—" : fmtN(r.no_prazo_pct, 1) + "%") }, { k: "no_prazo_data_original_pct", label: "Data original", num: true, render: (r) => (r.no_prazo_data_original_pct == null ? "—" : fmtN(r.no_prazo_data_original_pct, 1) + "%") },
    { k: "impacto_na_meta_pp", label: "Impacto", num: true, render: (r) => (r.impacto_na_meta_pp ? (r.impacto_na_meta_pp > 0 ? "+" : "") + fmtN(r.impacto_na_meta_pp, 1) + " pp" : "—") }], people, { onClick: (r) => { who = String(r.person_id); $("#fw").value = who; drawEv(); $("#ev").scrollIntoView({ behavior: "smooth" }); } }), "wide")}
  ${card("Evidências", "Cada linha é verificável no Jira pelo histórico da issue", `<div class="row" style="margin-bottom:8px"><select id="fs" aria-label="Sinal"><option value="">Todos os sinais</option>${d.catalogo.map((c) => `<option value="${c.sinal}">${escx(c.nome)}</option>`).join("")}</select><select id="fw" aria-label="Pessoa"><option value="">Todas as pessoas</option>${people.map((p) => `<option value="${p.person_id}">${escx(p.nome)}</option>`).join("")}</select></div><div id="ev"></div>`, "wide")}
  ${card("Catálogo de sinais", "O que cada sinal significa e por que aparece", `<table><tbody>${d.catalogo.map((c) => `<tr><td>${pill("▲".repeat(c.gravidade), c.gravidade >= 3 ? "bad" : c.gravidade === 2 ? "warn" : "mute")}</td><td><b>${escx(c.nome)}</b></td><td>${escx(c.explicacao)}</td></tr>`).join("")}</tbody></table>`, "wide")}`;
  $("#fs").onchange = (e) => { sig = e.target.value; drawEv(); };
  $("#fw").onchange = (e) => { who = e.target.value; drawEv(); };
  drawEv();
};

/* ------------------------------------------------------------------ épicos */
SCREENS.epicos = async function (root, f) {
  KD({});
  const d = await api("epics", f);
  root.innerHTML = `<p class="note">${escx(d.nota)}</p>` + card("Épicos", "Progresso, itens abertos, mais antigo e previsão", table([
    { k: "epico", label: "Épico" }, { k: "squads", label: "Squads", render: (r) => r.squads.map(escx).join(", ") }, { k: "itens", label: "Itens", num: true, render: (r) => dlLink(r.itens, "epico", r.epico) }, { k: "concluidos", label: "Concluídos", num: true, render: (r) => dlLink(r.concluidos, "epico", r.epico, "concluidos") }, { k: "abertos", label: "Abertos", num: true, render: (r) => dlLink(r.abertos, "epico", r.epico, "abertos") },
    { k: "progresso_pct", label: "Progresso", render: (r) => progress(r.progresso_pct) }, { k: "pontos", label: "Pontos", num: true, render: (r) => `${fmtN(r.pontos_concluidos)} / ${fmtN(r.pontos)}` },
    { k: "mais_antigo_d", label: "Mais antigo (d)", num: true }, { k: "vencidos", label: "Vencidos", num: true, render: (r) => (r.vencidos ? pill("▲ " + r.vencidos, "bad") : "") }, { k: "previsao_semanas", label: "Previsão (sem.)", num: true }, { k: "responsaveis", label: "Pessoas", num: true }], d.epicos), "wide");
};
