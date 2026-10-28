// Aplica o tema ANTES da primeira pintura, para a página não piscar no tema errado.
// Arquivo próprio (a CSP do painel só aceita script-src 'self', nunca script inline) e
// carregado no <head> sem defer. Mesma regra e mesma chave do ThemeProvider:
// escolha salva (light, dark ou sidia) vence; sem escolha, segue prefers-color-scheme
// (claro ou escuro). O tema Sidia só entra por escolha.
(function () {
  var tema = null
  try {
    var salvo = localStorage.getItem('painel.tema')
    if (salvo === 'light' || salvo === 'dark' || salvo === 'sidia') tema = salvo
  } catch (e) {
    // armazenamento bloqueado: cai no prefers-color-scheme
  }
  if (tema === null) {
    try {
      tema = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    } catch (e) {
      tema = 'light'
    }
  }
  document.documentElement.dataset.theme = tema
})()
