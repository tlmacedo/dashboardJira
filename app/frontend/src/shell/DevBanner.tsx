// Faixa fixa de modo desenvolvimento. Texto vem do backend (/api/meta); role="status"
// avisa leitores de tela, e o texto em si (não só a cor vermelha) carrega o sinal.
export function DevBanner({ texto }: { texto: string }) {
  return (
    <div className="devbar" role="status">
      {texto}
    </div>
  )
}
