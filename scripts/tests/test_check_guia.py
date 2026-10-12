import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from check_guia import verificar_referencias, verificar_texto

FILTRO_OK = """### EQP | Resumo | Atrasadas
**Nome:** EQP | Resumo | Atrasadas
**Descrição:** Tarefas vencidas e abertas.
**Critério de data:** vencimento
```jql
resolution = Unresolved AND duedate < startOfDay()
```
"""


class TestVerificarTexto(unittest.TestCase):
    def test_texto_valido_nao_tem_erros(self):
        self.assertEqual(verificar_texto("a.md", FILTRO_OK), [])

    def test_marcador_proibido(self):
        self.assertTrue(verificar_texto("a.md", "texto TODO aqui"))

    def test_parenteses_desbalanceados(self):
        texto = "```jql\nassignee in (a, b\n```\n"
        self.assertTrue(any("parênteses" in e for e in verificar_texto("a.md", texto)))

    def test_aspas_desbalanceadas(self):
        texto = '```jql\nassignee = "a\n```\n'
        self.assertTrue(any("aspas" in e for e in verificar_texto("a.md", texto)))

    def test_account_id_proibido_em_jql(self):
        texto = "```jql\nassignee = accountId\n```\n"
        self.assertTrue(any("accountId" in e for e in verificar_texto("a.md", texto)))

    def test_data_br_proibida_em_jql(self):
        texto = "```jql\ncreated >= 01/10/2026\n```\n"
        self.assertTrue(any("dd/mm/aaaa" in e for e in verificar_texto("a.md", texto)))

    def test_nome_fora_do_padrao(self):
        texto = FILTRO_OK.replace("**Nome:** EQP | Resumo | Atrasadas", "**Nome:** Atrasadas")
        self.assertTrue(any("padrão" in e for e in verificar_texto("a.md", texto)))

    def test_filtro_sem_criterio_de_data(self):
        texto = FILTRO_OK.replace("**Critério de data:** vencimento\n", "")
        self.assertTrue(any("critério" in e for e in verificar_texto("a.md", texto)))

    def test_cerca_jql_nao_fechada(self):
        texto = "```jql\nassignee = a\n"
        self.assertTrue(any("não fechada" in e for e in verificar_texto("a.md", texto)))

    def test_crlf_jql_com_erro_e_detectado(self):
        texto = '```jql\r\nassignee = "a\r\n```\r\n'
        self.assertTrue(any("aspas" in e for e in verificar_texto("a.md", texto)))

    def test_nome_com_apenas_duas_partes(self):
        texto = FILTRO_OK.replace("**Nome:** EQP | Resumo | Atrasadas", "**Nome:** EQP | Resumo")
        self.assertTrue(any("padrão" in e for e in verificar_texto("a.md", texto)))

    def test_criterio_pareado_por_entrada(self):
        # Dois critérios no primeiro filtro e nenhum no segundo: os totais batem, mas o pareamento não.
        texto = (
            "**Nome:** EQP | A | B\n"
            "**Critério de data:** x\n"
            "**Critério de data:** y\n"
            "**Nome:** EQP | C | D\n"
            "**Descrição:** z\n"
        )
        self.assertTrue(any("critério" in e for e in verificar_texto("a.md", texto)))


class TestVerificarReferencias(unittest.TestCase):
    def test_referencia_existente(self):
        derivado = FILTRO_OK.replace("resolution", 'filter = "EQP | Resumo | Atrasadas" AND resolution')
        derivado = derivado.replace("Atrasadas\n**Descrição", "Outra\n**Descrição", 1)
        derivado = derivado.replace("**Nome:** EQP | Resumo | Atrasadas", "**Nome:** EQP | Resumo | Outra")
        self.assertEqual(verificar_referencias({"a.md": FILTRO_OK, "b.md": derivado}), [])

    def test_referencia_com_espacos_nas_bordas(self):
        texto = '```jql\nfilter = " EQP | Resumo | Atrasadas " AND resolution = Unresolved\n```\n'
        self.assertEqual(verificar_referencias({"a.md": FILTRO_OK, "b.md": texto}), [])

    def test_referencia_inexistente(self):
        texto = '```jql\nfilter = "EQP | Base | Fantasma"\n```\n'
        self.assertTrue(verificar_referencias({"a.md": texto}))


if __name__ == "__main__":
    unittest.main()
