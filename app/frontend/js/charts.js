/* Gráficos SVG sem dependências. Cores vêm de variáveis CSS (tema claro/escuro). Cor nunca é o único canal. */
const C = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)", "var(--c7)"];
const escx = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const fmtN = (v, d = 0) => (v == null ? "—" : Number(v).toLocaleString("pt-BR", { minimumFractionDigits: d, maximumFractionDigits: d }));
const fmtDate = (iso) => (iso ? iso.slice(8, 10) + "/" + iso.slice(5, 7) + (iso.length >= 10 ? "/" + iso.slice(0, 4) : "") : "—");
const fmtDM = (iso) => (iso ? iso.slice(8, 10) + "/" + iso.slice(5, 7) : "");

function niceMax(v) {
  if (v <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v))), n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}

function colChart(items, { w = 560, h = 190, color = C[0], label = (d) => d.label, value = (d) => d.value, title = "" } = {}) {
  const pad = { l: 34, r: 8, t: 10, b: 24 }, iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const max = niceMax(Math.max(1, ...items.map(value)));
  const bw = iw / Math.max(1, items.length);
  let s = `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${escx(title)}" width="100%">`;
  for (let i = 0; i <= 4; i++) {
    const y = pad.t + ih - (ih * i) / 4;
    s += `<line class="grid-line" x1="${pad.l}" x2="${w - pad.r}" y1="${y}" y2="${y}"/><text x="${pad.l - 5}" y="${y + 4}" text-anchor="end">${fmtN((max * i) / 4)}</text>`;
  }
  items.forEach((d, i) => {
    const v = value(d), bh = (ih * v) / max, x = pad.l + i * bw + bw * 0.14;
    s += `<rect x="${x}" y="${pad.t + ih - bh}" width="${bw * 0.72}" height="${bh}" rx="3" fill="${color}"><title>${escx(label(d))}: ${fmtN(v)}</title></rect>`;
    if (items.length <= 16 || i % 2 === 0) s += `<text x="${x + bw * 0.36}" y="${h - 8}" text-anchor="middle">${escx(label(d))}</text>`;
  });
  return s + "</svg>";
}

function lineChart(series, { w = 560, h = 190, xs, title = "", yfmt = fmtN } = {}) {
  const pad = { l: 38, r: 10, t: 10, b: 24 }, iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const all = series.flatMap((s) => s.data.filter((v) => v != null));
  const max = niceMax(Math.max(1, ...all));
  const n = xs.length;
  const X = (i) => pad.l + (n <= 1 ? 0 : (iw * i) / (n - 1));
  const Y = (v) => pad.t + ih - (ih * v) / max;
  let s = `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${escx(title)}" width="100%">`;
  for (let i = 0; i <= 4; i++) {
    const y = pad.t + ih - (ih * i) / 4;
    s += `<line class="grid-line" x1="${pad.l}" x2="${w - pad.r}" y1="${y}" y2="${y}"/><text x="${pad.l - 5}" y="${y + 4}" text-anchor="end">${yfmt((max * i) / 4)}</text>`;
  }
  const step = Math.max(1, Math.ceil(n / 8));
  xs.forEach((x, i) => { if (i % step === 0) s += `<text x="${X(i)}" y="${h - 8}" text-anchor="middle">${escx(x)}</text>`; });
  series.forEach((sr, k) => {
    const pts = sr.data.map((v, i) => (v == null ? null : [X(i), Y(v)])).filter(Boolean);
    const col = sr.color || C[k % C.length];
    s += `<polyline fill="none" stroke="${col}" stroke-width="2.2" ${sr.dash ? 'stroke-dasharray="5 4"' : ""} points="${pts.map((p) => p.join(",")).join(" ")}"/>`;
    pts.forEach((p, i) => { if (pts.length <= 26) s += `<circle cx="${p[0]}" cy="${p[1]}" r="2.6" fill="${col}"><title>${escx(sr.name)}: ${fmtN(sr.data[i])}</title></circle>`; });
  });
  s += "</svg>";
  if (series.length > 1) s += legend(series.map((x, k) => [x.name, x.color || C[k % C.length]]));
  return s;
}

function stackedArea(rows, keys, { w = 700, h = 260, title = "" } = {}) {
  const pad = { l: 38, r: 10, t: 10, b: 24 }, iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const tot = rows.map((r) => keys.reduce((a, k) => a + (r[k] || 0), 0));
  const max = niceMax(Math.max(1, ...tot)), n = rows.length;
  const X = (i) => pad.l + (iw * i) / Math.max(1, n - 1), Y = (v) => pad.t + ih - (ih * v) / max;
  let s = `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${escx(title)}" width="100%">`;
  for (let i = 0; i <= 4; i++) { const y = pad.t + ih - (ih * i) / 4; s += `<line class="grid-line" x1="${pad.l}" x2="${w - pad.r}" y1="${y}" y2="${y}"/><text x="${pad.l - 5}" y="${y + 4}" text-anchor="end">${fmtN((max * i) / 4)}</text>`; }
  const base = rows.map(() => 0);
  // empilha da última etapa (concluído) para a primeira, como no CFD
  const order = [...keys].reverse();
  order.forEach((k) => {
    const top = rows.map((r, i) => base[i] + (r[k] || 0));
    const up = top.map((v, i) => `${X(i)},${Y(v)}`).join(" ");
    const dn = base.map((v, i) => `${X(i)},${Y(v)}`).reverse().join(" ");
    s += `<polygon points="${up} ${dn}" fill="${C[keys.indexOf(k) % C.length]}" fill-opacity=".78" stroke="var(--panel)" stroke-width="1"><title>${escx(k)}</title></polygon>`;
    top.forEach((v, i) => (base[i] = v));
  });
  const step = Math.max(1, Math.ceil(n / 8));
  rows.forEach((r, i) => { if (i % step === 0) s += `<text x="${X(i)}" y="${h - 8}" text-anchor="middle">${fmtDM(r.day)}</text>`; });
  return s + "</svg>" + legend(keys.map((k, i) => [k, C[i % C.length]]));
}

function scatter(points, lines, { w = 640, h = 240, title = "" } = {}) {
  const pad = { l: 38, r: 12, t: 10, b: 26 }, iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  if (!points.length) return '<div class="empty">Sem itens concluídos no período.</div>';
  const xs = points.map((p) => Date.parse(p.dia)), xmin = Math.min(...xs), xmax = Math.max(...xs, xmin + 1);
  const max = niceMax(Math.max(...points.map((p) => p.ciclo_d), ...lines.map((l) => l.v || 0)));
  const X = (t) => pad.l + (iw * (t - xmin)) / (xmax - xmin), Y = (v) => pad.t + ih - (ih * v) / max;
  let s = `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${escx(title)}" width="100%">`;
  for (let i = 0; i <= 4; i++) { const y = pad.t + ih - (ih * i) / 4; s += `<line class="grid-line" x1="${pad.l}" x2="${w - pad.r}" y1="${y}" y2="${y}"/><text x="${pad.l - 5}" y="${y + 4}" text-anchor="end">${fmtN((max * i) / 4)} d</text>`; }
  points.forEach((p) => (s += `<circle cx="${X(Date.parse(p.dia))}" cy="${Y(p.ciclo_d)}" r="3" fill="var(--c1)" fill-opacity=".45"><title>${escx(p.key)} · ${fmtN(p.ciclo_d, 1)} d · ${fmtDate(p.dia)}</title></circle>`));
  lines.forEach((l) => { if (l.v != null) s += `<line x1="${pad.l}" x2="${w - pad.r}" y1="${Y(l.v)}" y2="${Y(l.v)}" stroke="${l.color}" stroke-dasharray="6 4" stroke-width="1.6"><title>${l.name} ${fmtN(l.v, 1)} d</title></line>`; });
  [0, 0.5, 1].forEach((f) => { const t = xmin + (xmax - xmin) * f; s += `<text x="${X(t)}" y="${h - 8}" text-anchor="middle">${fmtDM(new Date(t).toISOString().slice(0, 10))}</text>`; });
  return s + "</svg>" + legend(lines.filter((l) => l.v != null).map((l) => [`${l.name} = ${fmtN(l.v, 1)} d`, l.color]));
}

function sparkline(vals, { w = 110, h = 28 } = {}) {
  if (!vals.length) return "";
  const max = Math.max(1, ...vals), n = vals.length;
  const pts = vals.map((v, i) => `${(w * i) / Math.max(1, n - 1)},${h - 3 - ((h - 6) * v) / max}`).join(" ");
  return `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="tendência"><polyline fill="none" stroke="var(--c1)" stroke-width="2" points="${pts}"/></svg>`;
}

function hbars(items, { color = C[0], max, fmt = (v) => fmtN(v), colorOf } = {}) {
  const m = max ?? Math.max(1, ...items.map((d) => d.value));
  if (!items.length) return '<div class="empty">Sem dados.</div>';
  return items.map((d) => `<div class="bar-row"><span title="${escx(d.label)}">${escx(d.label)}</span><span class="track"><span class="fill" style="display:block;width:${Math.max(1, (100 * d.value) / m)}%;background:${colorOf ? colorOf(d) : color}"></span></span><b style="text-align:right">${fmt(d.value)}</b></div>`).join("");
}

function legend(pairs) { return `<div class="legend">${pairs.map(([n, c]) => `<span><i style="background:${c}"></i>${escx(n)}</span>`).join("")}</div>`; }

/* Mapa de calor com valor numérico e símbolo em cada célula (não depende só da cor). */
function heat(rowsLabels, colLabels, matrix, { fmt = (v) => fmtN(v * 100) + "%", level = (v) => (v > 1.1 ? "bad" : v > 0.85 ? "warn" : v > 0 ? "ok" : "mute"), nameW = 130, title = "" } = {}) {
  const cols = colLabels.length;
  let s = `<div class="heat" role="table" aria-label="${escx(title)}" style="grid-template-columns:${nameW}px repeat(${cols},minmax(48px,1fr))"><span></span>${colLabels.map((c) => `<span class="h">${escx(c)}</span>`).join("")}`;
  rowsLabels.forEach((r, i) => {
    s += `<span class="n" title="${escx(r)}">${escx(r)}</span>`;
    matrix[i].forEach((v) => {
      if (v == null || typeof v === "string") { s += `<span class="c pill mute" style="border-radius:4px;justify-content:center">${v == null ? "—" : escx(v)}</span>`; return; }
      const lv = level(v), sym = lv === "bad" ? "▲ " : lv === "warn" ? "● " : "";
      s += `<span class="c pill ${lv}" style="border-radius:4px;justify-content:center">${sym}${fmt(v)}</span>`;
    });
  });
  return s + "</div>";
}

/* Colunas empilhadas por período; rows: [{label, ...chaves}] */
function stackedCols(rows, keys, colors, { w = 700, h = 230, title = "", line = null } = {}) {
  const pad = { l: 36, r: 8, t: 10, b: 26 }, iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const max = niceMax(Math.max(1, ...rows.map((r) => keys.reduce((a, k) => a + (r[k] || 0), 0))));
  const bw = iw / Math.max(1, rows.length);
  let s = `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${escx(title)}" width="100%">`;
  for (let i = 0; i <= 4; i++) { const y = pad.t + ih - (ih * i) / 4; s += `<line class="grid-line" x1="${pad.l}" x2="${w - pad.r}" y1="${y}" y2="${y}"/><text x="${pad.l - 5}" y="${y + 4}" text-anchor="end">${fmtN((max * i) / 4)}</text>`; }
  rows.forEach((r, i) => {
    let acc = 0; const x = pad.l + i * bw + bw * 0.12;
    keys.forEach((k, j) => { const v = r[k] || 0; if (!v) return; const bh = (ih * v) / max; acc += bh; s += `<rect x="${x}" y="${pad.t + ih - acc}" width="${bw * 0.76}" height="${bh}" fill="${colors[j]}"><title>${escx(r.label)} · ${escx(k)}: ${v}</title></rect>`; });
    if (rows.length <= 14 || i % 2 === 0) s += `<text x="${x + bw * 0.38}" y="${h - 8}" text-anchor="middle">${escx(r.label)}</text>`;
  });
  return s + "</svg>" + legend(keys.map((k, j) => [k, colors[j]]));
}

/* Barra de progresso com texto */
function progress(pct, { tone } = {}) {
  const t = tone || (pct >= 80 ? "var(--ok)" : pct >= 50 ? "var(--c1)" : "var(--warn)");
  return `<div style="min-width:110px"><div style="background:var(--bg);height:8px;border-radius:5px;overflow:hidden"><div style="height:100%;width:${Math.min(100, pct)}%;background:${t}"></div></div><small>${fmtN(pct, 1)}%</small></div>`;
}
