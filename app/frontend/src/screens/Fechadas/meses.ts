/** "09/2026" a partir de "2026-09": o mês como o painel o mostra. */
export function mesCurto(mes: string): string {
  const [a, m] = mes.split('-')
  return `${m}/${a}`
}
