import { useMutation } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { postarDecisao, type TipoDeSugestao } from './api'
import { rotuloDaDecisao } from './rotulos'
import './distribuicao.css'

const MAX_MOTIVO = 500

type Props = {
  tipo: TipoDeSugestao
  issueKey: string
  /** A pessoa sugerida (nula = ninguém tem capacidade: só dá para rejeitar). */
  sugerido: string | null
  /** Quem pode receber na troca (a tela já tira a sugerida e a origem; aqui ainda se ordena por nome). */
  candidatos: string[]
  /** Decisão já registrada hoje para esta sugestão; nula = ainda sem decisão. */
  decisao: string | null
  /** Chamado depois de gravar (ou de descobrir que já estava decidida) para a tela recarregar. */
  aoDecidir: () => void
}

/**
 * Aprovar, Trocar e Rejeitar uma sugestão (modo sombra: só grava no painel). "Trocar" abre a escolha da pessoa (lista em
 * ordem alfabética) e o motivo, que é OBRIGATÓRIO: o botão de confirmar fica desabilitado sem texto. Decisão repetida (duas
 * abas) mostra "já decidida" e recarrega a tela.
 */
export function DecisaoDeSugestao(props: Props) {
  const { t } = useLang()
  const idMotivo = useId()
  const idPessoa = useId()
  const [trocando, setTrocando] = useState(false)
  const [escolhido, setEscolhido] = useState('')
  const [motivo, setMotivo] = useState('')
  const [erro, setErro] = useState<string | null>(null)

  const envio = useMutation({
    mutationFn: postarDecisao,
    onSuccess: () => {
      setErro(null)
      setTrocando(false)
      props.aoDecidir()
    },
    onError: (e) => {
      const api = e instanceof ErroDaApi ? e : null
      if (api?.codigo === 'JA_DECIDIDA') {
        setErro(t('decisao.erro.jaDecidida'))
        props.aoDecidir()
      } else if (api?.codigo === 'SUGESTAO_MUDOU') setErro(t('decisao.erro.mudou'))
      else if (api?.status === 403) setErro(t('decisao.erro.semPermissao'))
      else setErro(t('decisao.erro.geral'))
    },
  })

  if (props.decisao) {
    return <span className="decisao-estado">{rotuloDaDecisao(props.decisao, t)}</span>
  }

  const opcoes = [...props.candidatos].filter((p) => p !== props.sugerido).sort((a, b) => a.localeCompare(b))
  const base = { tipo: props.tipo, issueKey: props.issueKey, sugerido: props.sugerido }
  const ocupado = envio.isPending

  return (
    <div className="decisao">
      <div className="decisao-botoes" role="group" aria-label={`${t('decisao.acoes')} ${props.issueKey}`}>
        <button
          type="button" className="btn" disabled={ocupado || props.sugerido === null}
          onClick={() => envio.mutate({ ...base, decisao: 'APROVADA' })}
        >
          {t('decisao.aprovar')}
        </button>
        <button type="button" className="btn" disabled={ocupado} aria-expanded={trocando} onClick={() => setTrocando((v) => !v)}>
          {t('decisao.trocar')}
        </button>
        <button type="button" className="btn" disabled={ocupado} onClick={() => envio.mutate({ ...base, decisao: 'REJEITADA' })}>
          {t('decisao.rejeitar')}
        </button>
      </div>
      {trocando && (
        <form
          className="decisao-troca"
          onSubmit={(ev) => {
            ev.preventDefault()
            if (!escolhido || !motivo.trim()) return
            envio.mutate({ ...base, decisao: 'TROCADA', escolhido, motivo: motivo.trim() })
          }}
        >
          <label htmlFor={idPessoa}>{t('decisao.paraQuem')}</label>
          <select id={idPessoa} value={escolhido} onChange={(e) => setEscolhido(e.target.value)}>
            <option value="">{t('decisao.escolha')}</option>
            {opcoes.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
          <label htmlFor={idMotivo}>{t('decisao.motivo')}</label>
          <textarea
            id={idMotivo} value={motivo} maxLength={MAX_MOTIVO} rows={2} required aria-required="true"
            onChange={(e) => setMotivo(e.target.value)}
          />
          <p className="legenda-meta">{t('decisao.motivo.dica').replace('{n}', String(MAX_MOTIVO))}</p>
          <button type="submit" className="btn btn--primario" disabled={ocupado || !escolhido || !motivo.trim()}>
            {t('decisao.confirmarTroca')}
          </button>
        </form>
      )}
      {erro && (
        <p className="decisao-erro" role="alert">
          {erro}
        </p>
      )}
    </div>
  )
}
