import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchAlertas, type AlertaOperacional } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { useRole } from './RoleContext'

/** Fuso fixo do painel (CLAUDE.md): as datas da tela são sempre mostradas em Manaus. */
const FUSO = 'America/Manaus'
const CINCO_MINUTOS = 5 * 60_000

/**
 * Alertas operacionais, só para o GERENTE. A consulta se repete a cada 5 minutos. Quem não é gerente nem consulta (o
 * backend também recusa: isto é só conveniência de interface). Erro ou resposta estranha = nenhum alerta mostrado.
 */
function useAlertas(): AlertaOperacional[] {
  const papel = useRole()
  const consulta = useQuery({
    queryKey: ['alertas-operacionais'],
    queryFn: fetchAlertas,
    enabled: papel === 'GERENTE',
    refetchInterval: CINCO_MINUTOS,
    retry: false,
  })
  return papel === 'GERENTE' && consulta.data ? consulta.data : []
}

/** Faixa no topo: só aparece com alerta. A cor nunca é o único sinal: ▲ para crítico e ● para atenção, sempre com texto. */
export function AvisoDeAlertas() {
  const { t } = useLang()
  const alertas = useAlertas()
  if (alertas.length === 0) return null
  const criticos = alertas.filter((a) => a.gravidade === 'CRITICO').length
  const atencao = alertas.length - criticos
  return (
    <div className="alertas-faixa" role="status">
      {criticos > 0 && <span className="alertas-faixa-critico">{`▲ ${criticos} ${t('alertas.criticos')}`}</span>}
      {atencao > 0 && <span className="alertas-faixa-atencao">{`● ${atencao} ${t('alertas.atencao')}`}</span>}
      <Link to="/sincronizacao#alertas">{t('alertas.ver')}</Link>
    </div>
  )
}

function quando(iso: string, lang: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return new Intl.DateTimeFormat(lang === 'en' ? 'en-US' : 'pt-BR', {
    timeZone: FUSO,
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(d)
}

/** Lista de alertas da tela Sincronização (âncora `#alertas`): cada um com símbolo, texto, instância e "desde". */
export function ListaDeAlertas() {
  const { t, lang } = useLang()
  const papel = useRole()
  const alertas = useAlertas()
  if (papel !== 'GERENTE') return null
  return (
    <section id="alertas" className="alertas-lista" aria-labelledby="alertas-titulo">
      <h2 id="alertas-titulo">{t('alertas.titulo')}</h2>
      {alertas.length === 0 ? (
        <p>{t('alertas.nenhum')}</p>
      ) : (
        <ul aria-label={t('alertas.titulo')}>
          {alertas.map((a) => (
            <li key={`${a.codigo}:${a.instancia ?? ''}`} className={a.gravidade === 'CRITICO' ? 'alertas-critico' : 'alertas-atencao'}>
              <strong>{`${a.gravidade === 'CRITICO' ? '▲' : '●'} ${t(`alertas.${a.codigo}`)}`}</strong>
              {a.instancia && <span>{` — ${t('alertas.instancia')} ${a.instancia}`}</span>}
              <span>{` — ${t('alertas.desde')} ${quando(a.desde, lang)}`}</span>
              <code>{a.codigo}</code>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
