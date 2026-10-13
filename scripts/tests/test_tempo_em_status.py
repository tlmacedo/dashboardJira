import copy
import io
import json
import random
import sys
import unittest
import urllib.error
from contextlib import redirect_stderr, redirect_stdout
from datetime import timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import tempo_em_status as t

ISSUE = {
    "key": "ABC-1",
    "fields": {
        "created": "2026-01-05T09:00:00.000+0000",
        "resolutiondate": "2026-01-08T11:00:00.000+0000",
        "status": {"name": "Done"},
    },
    "changelog": {"histories": [
        {"author": {"name": "ana"}, "created": "2026-01-05T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Open", "toString": "In Progress"}]},
        {"author": {"name": "bia"}, "created": "2026-01-06T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "In Progress", "toString": "Done"}]},
        {"author": {"name": "ana"}, "created": "2026-01-07T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Done", "toString": "Reopened"}]},
        {"author": {"name": "ana"}, "created": "2026-01-07T11:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Reopened", "toString": "In Progress"}]},
        {"author": {"name": "bia"}, "created": "2026-01-08T11:00:00.000+0000",
         "items": [{"field": "priority", "fromString": "Low", "toString": "High"},
                   {"field": "status", "fromString": "In Progress", "toString": "Done"}]},
    ]},
}

AGORA = t.analisar_data("2026-02-01T00:00:00.000+0000")
H = lambda n: timedelta(hours=n)


def sem_transicoes(resolucao=None):
    fields = {"created": "2026-01-05T09:00:00.000+0000", "status": {"name": "Open"}}
    if resolucao:
        fields["resolutiondate"] = resolucao
    return {"key": "ABC-2", "fields": fields, "changelog": {"histories": []}}


class TestDatas(unittest.TestCase):
    def test_com_e_sem_milissegundos(self):
        a = t.analisar_data("2026-01-05T09:00:00.000+0000")
        b = t.analisar_data("2026-01-05T09:00:00+0000")
        self.assertEqual(a, b)
        self.assertIsNotNone(a.tzinfo)


class TestTransicoes(unittest.TestCase):
    def test_cinco_transicoes_ignora_priority(self):
        tr = t.transicoes_de_status(ISSUE)
        self.assertEqual(len(tr), 5)
        self.assertEqual((tr[0]["de"], tr[0]["para"], tr[0]["autor"]),
                         ("Open", "In Progress", "ana"))

    def test_ordem_cronologica_com_entrada_embaralhada(self):
        issue = copy.deepcopy(ISSUE)
        random.Random(1).shuffle(issue["changelog"]["histories"])
        issue["changelog"]["histories"].reverse()
        tr = t.transicoes_de_status(issue)
        quando = [x["quando"] for x in tr]
        self.assertEqual(quando, sorted(quando))
        self.assertEqual(tr[0]["de"], "Open")


class TestTempos(unittest.TestCase):
    def test_tempo_por_status(self):
        r = t.tempo_por_status(ISSUE, AGORA)
        self.assertEqual(r, {"Open": H(1), "In Progress": H(48),
                             "Done": H(24), "Reopened": H(1)})
        self.assertEqual(sum(r.values(), timedelta()), H(74))

    def test_passagens(self):
        self.assertEqual(t.passagens_por_status(ISSUE),
                         {"Open": 1, "In Progress": 2, "Done": 2, "Reopened": 1})

    def test_ciclo(self):
        self.assertEqual(t.tempo_de_ciclo(ISSUE, "In Progress", "Done"), H(24))
        self.assertEqual(t.tempo_de_ciclo(ISSUE, "Done", "In Progress"), H(25))
        self.assertIsNone(t.tempo_de_ciclo(ISSUE, "Done", "Inexistente"))

    def test_reaberturas(self):
        self.assertEqual(t.reaberturas(ISSUE, "Done", "Reopened"), 1)

    def test_sem_transicoes_nao_resolvida(self):
        issue = sem_transicoes()
        criada = t.analisar_data(issue["fields"]["created"])
        self.assertEqual(t.tempo_por_status(issue, AGORA), {"Open": AGORA - criada})
        self.assertEqual(t.passagens_por_status(issue), {"Open": 1})

    def test_sem_transicoes_resolvida(self):
        issue = sem_transicoes("2026-01-06T09:00:00.000+0000")
        self.assertEqual(t.tempo_por_status(issue, AGORA), {"Open": H(24)})

    def test_intervalos_chaves(self):
        iv = t.intervalos(ISSUE, AGORA)
        self.assertEqual(len(iv), 6)
        self.assertEqual(set(iv[0]), {"status", "inicio", "fim", "autor_entrada"})


class Resposta:
    def __init__(self, corpo):
        self._corpo = json.dumps(corpo).encode()

    def read(self):
        return self._corpo


def erro_429(espera="3"):
    return urllib.error.HTTPError("http://x", 429, "Too Many", {"retry-after": espera}, None)


class TestBuscar(unittest.TestCase):
    def test_duas_paginas(self):
        paginas = [
            {"startAt": 0, "total": 3, "issues": [{"key": "A-1"}, {"key": "A-2"}]},
            {"startAt": 2, "total": 3, "issues": [{"key": "A-3"}]},
        ]
        reqs = []

        def abrir(req, **kw):
            reqs.append(req)
            return Resposta(paginas[len(reqs) - 1])

        issues = list(t.buscar_issues("https://j.exemplo.com", "tok", "project = A",
                                      abrir=abrir, dormir=lambda s: None, tamanho_pagina=2))
        self.assertEqual([i["key"] for i in issues], ["A-1", "A-2", "A-3"])
        self.assertEqual(len(reqs), 2)
        url = reqs[0].full_url
        for parte in ("expand=changelog", "startAt=", "maxResults="):
            self.assertIn(parte, url)
        self.assertEqual(reqs[0].get_header("Authorization"), "Bearer tok")

    def test_429_com_retry_after(self):
        chamadas, sono = [], []

        def abrir(req, **kw):
            chamadas.append(1)
            if len(chamadas) == 1:
                raise erro_429("3")
            return Resposta({"startAt": 0, "total": 1, "issues": [{"key": "A-1"}]})

        issues = list(t.buscar_issues("https://j", "tok", "x", abrir=abrir, dormir=sono.append))
        self.assertEqual(len(issues), 1)
        self.assertEqual(sono, [3])
        self.assertEqual(len(chamadas), 2)

    def test_429_esgota_tentativas(self):
        def abrir(req, **kw):
            raise erro_429("1")

        with self.assertRaises(RuntimeError) as ctx:
            list(t.buscar_issues("https://j", "tok", "x", abrir=abrir,
                                 dormir=lambda s: None, max_tentativas=3))
        self.assertIn("tentativas", str(ctx.exception))


class TestCsv(unittest.TestCase):
    def test_linhas(self):
        linhas = t.linhas_csv([ISSUE], AGORA, "In Progress", "Done")
        self.assertEqual(len(linhas), 2)
        self.assertEqual(linhas[0], [
            "chave",
            "tempo_Done_horas", "tempo_In Progress_horas", "tempo_Open_horas",
            "tempo_Reopened_horas",
            "passagens_Done", "passagens_In Progress", "passagens_Open",
            "passagens_Reopened", "ciclo_horas", "reaberturas"])
        self.assertEqual(linhas[1], ["ABC-1", "24.0", "48.0", "1.0", "1.0",
                                     "2", "2", "1", "1", "24.0", "1"])

    def test_ciclo_ausente_fica_vazio(self):
        linhas = t.linhas_csv([sem_transicoes()], AGORA, "In Progress", "Done")
        self.assertEqual(linhas[1][-2:], ["", "0"])


class TestMain(unittest.TestCase):
    def test_help(self):
        saida = io.StringIO()
        with redirect_stdout(saida), self.assertRaises(SystemExit) as ctx:
            t.main(["--help"])
        self.assertEqual(ctx.exception.code, 0)
        texto = saida.getvalue()
        for parte in ("--status-inicio", "--status-fim", "ciclo_horas"):
            self.assertIn(parte, texto)

    def _rodar(self, abrir, env):
        import os
        from unittest import mock
        err = io.StringIO()
        with mock.patch.dict(os.environ, env, clear=False), redirect_stderr(err), \
                redirect_stdout(io.StringIO()):
            codigo = t.main(["--url", "https://j.exemplo.com", "--jql", "project = SEGREDO",
                             "--token-env", "TOK_TESTE"], abrir=abrir)
        return codigo, err.getvalue()

    def test_401_sai_com_1_sem_vazar_token(self):
        def abrir(req, **kw):
            raise urllib.error.HTTPError(req.full_url, 401, "Unauthorized", {}, None)

        codigo, err = self._rodar(abrir, {"TOK_TESTE": "token-secreto"})
        self.assertEqual(codigo, 1)
        self.assertIn("401", err)
        self.assertIn("PAT", err)
        for proibido in ("token-secreto", "SEGREDO", "j.exemplo.com"):
            self.assertNotIn(proibido, err)

    def test_500_sai_com_1(self):
        def abrir(req, **kw):
            raise urllib.error.HTTPError(req.full_url, 500, "Erro", {}, None)

        codigo, err = self._rodar(abrir, {"TOK_TESTE": "x"})
        self.assertEqual((codigo, "500" in err), (1, True))

    def test_erro_de_rede_sai_com_1(self):
        def abrir(req, **kw):
            raise urllib.error.URLError("sem rede")

        self.assertEqual(self._rodar(abrir, {"TOK_TESTE": "x"})[0], 1)

    def test_429_esgotado_sai_com_1_sem_vazar(self):
        import os
        from unittest import mock

        def abrir(req, **kw):
            raise erro_429("1")

        sonos = []
        err = io.StringIO()
        with mock.patch.dict(os.environ, {"TOK_TESTE": "token-secreto"}), \
                redirect_stderr(err), redirect_stdout(io.StringIO()):
            codigo = t.main(["--url", "https://j.exemplo.com", "--jql", "project = SEGREDO",
                             "--token-env", "TOK_TESTE"], abrir=abrir, dormir=sonos.append)
        self.assertEqual(codigo, 1)
        self.assertIn("429", err.getvalue())
        self.assertIn("tentativas", err.getvalue())
        self.assertEqual(len(sonos), 4)
        for proibido in ("token-secreto", "SEGREDO", "j.exemplo.com"):
            self.assertNotIn(proibido, err.getvalue())

    def test_variavel_ausente_sai_com_1(self):
        import os
        os.environ.pop("TOK_TESTE", None)
        codigo, err = self._rodar(lambda *a, **k: None, {})
        self.assertEqual(codigo, 1)
        self.assertIn("TOK_TESTE", err)


class TestStatusInicialDesconhecido(unittest.TestCase):
    def test_sem_from_string(self):
        issue = copy.deepcopy(ISSUE)
        del issue["changelog"]["histories"][0]["items"][0]["fromString"]
        self.assertIn("(desconhecido)", t.tempo_por_status(issue, AGORA))
        self.assertIn("(desconhecido)", t.passagens_por_status(issue))
        t.linhas_csv([issue, ISSUE], AGORA, "In Progress", "Done")


class TestTruncamentoChangelog(unittest.TestCase):
    def _pagina(self, issue):
        corpo = json.dumps({"startAt": 0, "total": 1, "issues": [issue]}).encode("utf-8")
        resposta = io.BytesIO(corpo)
        return lambda req, **kw: resposta

    def _rodar(self, issue):
        import os
        from unittest import mock
        err = io.StringIO()
        with mock.patch.dict(os.environ, {"TOK_TESTE": "token-secreto"}), \
                redirect_stderr(err), redirect_stdout(io.StringIO()):
            codigo = t.main(["--url", "https://j.exemplo.com", "--jql", "project = SEGREDO",
                             "--token-env", "TOK_TESTE"], abrir=self._pagina(issue))
        return codigo, err.getvalue()

    def test_avisa_quando_changelog_truncado(self):
        issue = copy.deepcopy(ISSUE)
        issue["changelog"]["total"] = 120  # só 5 históricos lidos
        codigo, err = self._rodar(issue)
        self.assertEqual(codigo, 0)
        self.assertIn("ABC-1", err)
        self.assertIn("changelog possivelmente truncado: 5 de 120 mudanças lidas", err)
        for proibido in ("token-secreto", "SEGREDO", "j.exemplo.com"):
            self.assertNotIn(proibido, err)

    def test_sem_aviso_quando_completo_ou_sem_total(self):
        completo = copy.deepcopy(ISSUE)
        completo["changelog"]["total"] = 5
        self.assertEqual(self._rodar(completo)[1], "")
        self.assertEqual(self._rodar(copy.deepcopy(ISSUE))[1], "")


if __name__ == "__main__":
    unittest.main()
