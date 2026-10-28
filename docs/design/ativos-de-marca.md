# Ativos de marca: o que existe, o que falta e como trocar

Data do registro: 28/10/2026 (Plano 15, Tarefa 1). Decisão: **caminho alternativo**, sem ativos novos.

## Ruling: caminho alternativo, sem perguntar ao usuário

O plano previa um ponto de parada com três perguntas. A execução foi autônoma, sem o usuário presente, e o `CLAUDE.md` já diz que não há arquivo limpo do logo nem manual de marca. Por isso valeram os padrões recomendados do plano para o que não depende de arquivo:

- Motivo: não inventar nem copiar ativo protegido que não temos; não baixar nada de terceiros.
- Como reverter: quando o usuário fornecer os arquivos, trocar só os pontos isolados descritos abaixo (um arquivo de ativos, um arquivo de fonte, os tokens). Nenhuma tela precisa mudar.

## Decisões

| Item | Estado | Decisão |
|---|---|---|
| Logo | Não há arquivo limpo (SVG ou PNG transparente oficial). Existem em `app/frontend/public/` duas imagens PNG já versionadas (`logo-sidia-azul.png`, `logo-sidia-branco.png`, 500 x 176) e um `favicon.svg` próprio (símbolo simples de barras, sem derivar do logo). | Manter o que já existe, centralizado no componente `Marca` e no módulo `marca-ativos.ts`; se a imagem faltar, o menu mostra o nome do painel em texto. Quando o usuário enviar o SVG limpo: sanear (ver abaixo), colocar em `src/assets/marca/` e trocar os caminhos em `marca-ativos.ts`. |
| Fonte (Mark) e licença | Nenhum arquivo `.woff2` nem licença recebidos. | Pilha de fonte do sistema permanente até haver arquivos e licença que permitam cópia em repositório privado (hoje o repositório é público, então a fonte não pode ser versionada). `theme/fontes.css` não declara `@font-face`. Para ligar a Mark: copiar os `.woff2` para `public/fonts/`, declarar as `@font-face` em `fontes.css` e antepor `"Mark"` ao `--fonte-texto`. |
| Manual de marca | Não há manual. | Manter o azul de marca único `#1329A0` e as cores do plano visual (seção 2.1). Reabrir só se um manual entregue divergir. |

## O que o usuário precisa fornecer

1. Logo limpo em SVG (de preferência com variante clara para o menu azul) ou PNG transparente de 512 px ou mais.
2. Arquivos `.woff2` da fonte Mark (regular, médio e negrito) e a licença, dizendo se permite uso web e cópia no repositório; como o repositório é público, sem essa permissão a fonte fica fora do git.
3. Manual de marca, se existir, para conferir cada cor.

## Saneamento obrigatório de qualquer SVG recebido

Sem `<script>`, sem atributos `on*`, sem `<foreignObject>`, sem `href` ou `xlink:href` externos, sem `<style>` com `@import`; com `viewBox` e `<title>`. O teste `src/a11y/marca.test.ts` varre os SVG de `public/` e `src/assets/marca/` e falha se encontrar qualquer um desses itens.
