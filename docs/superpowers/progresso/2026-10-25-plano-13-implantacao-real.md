# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-13-implantacao-real.md

Branch: `feat/implantacao-codigo-plano-13` (parte de CÓDIGO e EMPACOTAMENTO: Tarefas 2, 3, 4, 5, 6, 9 e 10; linha A14 do roteiro `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`). Executor: Claude Code, sem o usuário presente; pontos de parada sem efeito fora do repositório resolvidos pela opção Recomendada. Base: `origin/main` em 588ef87 (migrações até V6). Nenhuma migração é criada aqui (o Plano 11 usa a V7).

## Fica para depois da flag (não é desta branch)

T1 (decisões com a infraestrutura), T7 passo 5 (tag e release), T8 (instalar o serviço no SO da empresa), T11 (homologação), T12 (produção) e T13 (guia final, depende de T8 e T11). Nenhuma tag, serviço, firewall, proxy ou certificado foi tocado.

## Estado herdado (conferido contra o código real em 29/10/2026)

- O Plano 8 já está na `main` e antecipou parte do Plano 13: `PAINEL_HOST` (escuta, padrão `127.0.0.1`), `OIDC_CLAIM_USERNAME`, `PerfilDeExecucao` (scripts do pacote fixam o perfil de produção), `app/scripts/montar-pacote.*`, `verificar-pacote.*` e `docs/implantacao.md`.
- O Plano 12 já está na `main`: `painel validar-config`, readiness, métricas em porta interna (loopback), backup `VACUUM INTO`.
- O Plano 11 (`LgpdSettings`, `DASHBOARD_DPO_ACEITE`) NÃO está na `main`.
- O módulo `ktor-server-forwarded-header` não está no cache do Gradle; `mutableOriginConnectionPoint` está no `ktor-server-core` 3.3.1, então o plugin de proxy confiável é próprio (sem dependência nova).

## Rulings

- Ruling (T3): plugin próprio (`instalarProxyConfiavel`, `createApplicationPlugin` + `mutableOriginConnectionPoint` do `ktor-server-core`) em vez do `XForwardedHeaders`, porque o módulo `ktor-server-forwarded-header` não está no cache offline do Gradle e a lista CIDR é código próprio de qualquer jeito. Só é instalado quando `DASHBOARD_PROXY_CONFIAVEL` não é vazia. O `access_log` do painel não grava IP hoje, então "IP no access_log" ficou como `call.request.origin.remoteAddress` já resolvido (o ponto de uso futuro).
- Ruling (T2): a variável da claim já existe (`OIDC_CLAIM_USERNAME`, Plano 8); só se acrescentou `OIDC_USERNAME_ESTRATEGIA` e `OidcSettings.estrategiaUsername`. Sem resposta do usuário sobre o provedor (T1), o padrão `claim_direto` é o do Plano 8 e nada muda para quem não definir a variável — custo se errado: nenhum; a troca é só a variável.
- Ruling (T2): o motivo `USERNAME_ILEGIVEL` (401) vale quando a estratégia não consegue extrair o username (claim sem o formato esperado). O `USUARIO_INVALIDO` do Plano 8 continua para username extraído fora de `[a-z0-9._-]{1,64}`: por isso `ana.silva@outro.invalid` com `claim_direto` é recusado com 401 (antes do 403 que o plano previa) e nunca abre sessão nem vira `ana.silva`. `CONTA_WINDOWS` e `PARTE_LOCAL_DO_EMAIL` exigem as duas partes não vazias.

