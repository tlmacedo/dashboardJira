"""Integração ponta a ponta: Syncer + JiraClient contra o Jira SIMULADO (tests/mock_jira.py), em SQLite temporário.

Cobre carga completa Cloud/DC, contagens, idempotência, incremental com marca d'água, reconciliação,
backoff 429/503, erros 401/403, status não mapeado, squads por projeto/componente, subtarefa Review,
fuso das datas e as particularidades do changelog (duplicatas, truncamento, autor ausente, formatos de data).
"""
from __future__ import annotations

from collections import Counter
from datetime import date, datetime, timedelta

import httpx
import pytest
import yaml

from jiradash.jira.client import JiraClient, JiraError
from jiradash.jira.sync import Syncer, parse_dt
from jiradash.models import (AssigneeChange, ChangeEvent, Issue, Person, StatusTransition, SyncState, Worklog)

FLAVORS = ["cloud", "dc"]


def counts(db):
    ev = Counter(f for (f,) in db.query(ChangeEvent.field).all())
    return {"issues": db.query(Issue).count(), "transitions": db.query(StatusTransition).count(),
            "assignee_changes": db.query(AssigneeChange).count(), "events": dict(ev), "events_total": sum(ev.values()),
            "worklogs": db.query(Worklog).count()}


def check_counts(db, exp):
    got = counts(db)
    for k in ("issues", "transitions", "assignee_changes", "events_total", "worklogs"):
        assert got[k] == exp[k], f"{k}: banco={got[k]} esperado={exp[k]}"
    assert got["events"] == exp["events"]


# --------------------------------------------------------------------------------------------- carga completa
@pytest.mark.parametrize("flavor", FLAVORS)
def test_carga_completa_conta_tudo(jira_env, flavor):
    env = jira_env(flavor, n_issues=90)
    out = env.sync(full=True)
    exp = env.mock.expected()
    assert out["issues"] == exp["issues"]
    check_counts(env.db, exp)
    # abertas == não 'done' conforme o mapeamento de status do YAML
    assert env.db.query(Issue).filter(Issue.status_category != "done").count() == exp["abertas"]
    # a marca d'água foi gravada
    assert env.db.query(SyncState).filter_by(key="watermark").one().value


@pytest.mark.parametrize("flavor", FLAVORS)
def test_idempotente_rodar_duas_vezes(jira_env, flavor):
    env = jira_env(flavor, n_issues=60)
    env.sync(full=True)
    antes = counts(env.db)
    env.sync(full=True)  # 2ª carga completa: sem duplicar nada
    env.sync(full=False)  # e uma incremental por cima
    assert counts(env.db) == antes
    ids = [c for (c,) in env.db.query(StatusTransition.changelog_id).all()]
    assert len(ids) == len(set(ids))


@pytest.mark.parametrize("flavor", FLAVORS)
def test_duplicatas_do_changelog_sao_descartadas(jira_env, flavor):
    env = jira_env(flavor, n_issues=40, dup_histories=True, bulk_page_histories=7)
    env.sync(full=True)
    check_counts(env.db, env.mock.expected())


def test_bulkfetch_cloud_paginado_por_token(jira_env):
    env = jira_env("cloud", n_issues=50, bulk_page_histories=5)
    env.sync(full=True)
    assert len(env.mock.calls_to("changelog/bulkfetch")) > 3  # seguiu o nextPageToken
    check_counts(env.db, env.mock.expected())


@pytest.mark.parametrize("flavor", FLAVORS)
def test_busca_paginada_em_paginas_pequenas(jira_env, flavor):
    env = jira_env(flavor, n_issues=70, search_page_cap=7)
    out = env.sync(full=True)
    assert out["issues"] == env.mock.expected()["issues"]
    assert len(env.mock.calls_to("/search")) >= 10


# --------------------------------------------------------------------------------------------- squads
@pytest.mark.parametrize("flavor", FLAVORS)
def test_squad_por_projeto(jira_env, flavor):
    env = jira_env(flavor, n_issues=60)
    env.sync(full=True)
    from jiradash.models import Squad
    por_proj = {s.projects[0]: s.id for s in env.db.query(Squad).all()}
    assert set(por_proj) == {"PAY", "PLT", "CX"}
    for i in env.db.query(Issue).all():
        assert i.squad_id == por_proj[i.project], i.key


def test_squad_por_componente(jira_env, tmp_path, monkeypatch):
    """Dois squads no MESMO projeto, separados por componente; um terceiro 'curinga' (sem componente) listado ANTES."""
    from jiradash import config, teams
    cfg = yaml.safe_load((config.ROOT / "config" / "equipes.demo.yaml").read_text(encoding="utf-8"))
    sq = {s["key"]: s for s in cfg["squads"]}
    curinga = {**sq["pagamentos"], "key": "curinga", "name": "Curinga", "projects": ["PAY"], "components": [], "members": sq["pagamentos"]["members"][:1]}
    pix = {**sq["pagamentos"], "key": "pix", "name": "Pix", "projects": ["PAY"], "components": ["pix"], "members": sq["pagamentos"]["members"][1:2]}
    cart = {**sq["plataforma"], "key": "cartoes", "name": "Cartões", "projects": ["PAY"], "components": ["cartoes"], "members": sq["plataforma"]["members"][:1]}
    cfg["squads"] = [curinga, pix, cart, sq["plataforma"], sq["experiencia"]]
    d = tmp_path / "cfg"
    d.mkdir()
    (d / "equipes.demo.yaml").write_text(yaml.safe_dump(cfg, allow_unicode=True), encoding="utf-8")
    monkeypatch.setattr(config, "CONFIG_DIR", d)
    env = jira_env("cloud", n_issues=90, with_components=True)
    from jiradash.models import Squad
    env.db.query(Squad).delete()
    teams.bootstrap(env.db)
    env.sync(full=True)
    keys = {s.key: s.id for s in env.db.query(Squad).all()}
    for i in env.db.query(Issue).filter(Issue.project == "PAY").all():
        want = keys["pix"] if i.components == ["pix"] else keys["cartoes"] if i.components == ["cartoes"] else keys["curinga"]
        assert i.squad_id == want, (i.key, i.components)


# --------------------------------------------------------------------------------------------- Review/Checklist
@pytest.mark.parametrize("flavor", FLAVORS)
def test_subtarefa_review_tem_parent_key(jira_env, flavor):
    env = jira_env(flavor, n_issues=90)
    env.sync(full=True)
    revs = env.db.query(Issue).filter(Issue.type == "Review").all()
    assert len(revs) == env.mock.expected()["reviews"] > 0
    for r in revs:
        assert r.parent_key
        pai = env.db.query(Issue).filter_by(key=r.parent_key).one()
        assert pai.type == "Checklist"


@pytest.mark.parametrize("flavor", FLAVORS)
def test_epic_key_preenchido(jira_env, flavor):
    """Cloud: fields.parent do tipo Epic; DC: campo personalizado 'Epic Link'."""
    env = jira_env(flavor, n_issues=90)
    env.sync(full=True)
    esperados = {i.key for i in env.mock.live() if i.epic}
    assert esperados
    got = {i.key for i in env.db.query(Issue).filter(Issue.epic_key.isnot(None)).all()}
    assert got == esperados


# --------------------------------------------------------------------------------------------- changelog
def test_duedate_dc_normalizado_para_iso(jira_env):
    env = jira_env("dc", n_issues=90)
    env.sync(full=True)
    evs = env.db.query(ChangeEvent).filter_by(field="duedate").all()
    assert evs
    for e in evs:
        assert date.fromisoformat(e.to_value), e.to_value
        assert e.from_value is None or date.fromisoformat(e.from_value), e.from_value


def test_duedate_cloud_vem_de_fromstring(jira_env):
    env = jira_env("cloud", n_issues=90)
    env.sync(full=True)
    for e in env.db.query(ChangeEvent).filter_by(field="duedate").all():
        assert date.fromisoformat(e.to_value)


@pytest.mark.parametrize("flavor", FLAVORS)
def test_labels_e_estimativa_do_changelog(jira_env, flavor):
    env = jira_env(flavor, n_issues=90)
    env.sync(full=True)
    esperado = {}
    for i in env.mock.live():
        for h in i.histories:
            for x in h.items:
                if x["field"] == "labels":
                    esperado[f"{i.id}-{h.id}"] = (" ".join(x["from"]), " ".join(x["to"]))
    assert esperado
    got = {}
    for e in env.db.query(ChangeEvent).filter_by(field="labels").all():
        got[e.changelog_id.rsplit("-", 1)[0]] = ((e.from_value or ""), (e.to_value or ""))
    assert got == esperado
    for e in env.db.query(ChangeEvent).filter_by(field="timeoriginalestimate").all():
        assert int(e.from_value) > 0 and int(e.to_value) > int(e.from_value)


@pytest.mark.parametrize("flavor", FLAVORS)
def test_estado_final_da_issue_bate_com_o_jira(jira_env, flavor):
    env = jira_env(flavor, n_issues=60)
    env.sync(full=True)
    for m in env.mock.live():
        i = env.db.query(Issue).filter_by(id=m.id).one()
        assert (i.key, i.project, i.type, i.status, i.priority) == (m.key, m.project, m.type, m.status, m.priority)
        assert i.resolution == m.resolution
        assert i.due == m.due and sorted(i.labels) == sorted(m.labels)
        assert i.original_estimate_s == m.estimate and i.time_spent_s == m.spent
        assert i.created == m.created.replace(microsecond=0) and i.updated == m.updated.replace(microsecond=0) or abs(i.created - m.created) < timedelta(seconds=1)
        if m.assignee:
            assert env.db.get(Person, i.assignee_id).jira_ref == m.assignee
        else:
            assert i.assignee_id is None


@pytest.mark.parametrize("flavor", FLAVORS)
def test_autor_ausente_vira_none(jira_env, flavor):
    env = jira_env(flavor, n_issues=60, anonymous_author_ratio=0.5)
    env.sync(full=True)
    assert env.db.query(StatusTransition).filter(StatusTransition.author_id.is_(None)).count() > 10
    check_counts(env.db, env.mock.expected())


def test_changelog_truncado_no_dc_e_completado(jira_env):
    env = jira_env("dc", n_issues=60, dc_changelog_cap=2)
    env.sync(full=True)
    check_counts(env.db, env.mock.expected())


def test_changelog_truncado_no_dc_sem_endpoint_dedicado(jira_env):
    env = jira_env("dc", n_issues=40, dc_changelog_cap=2, dc_changelog_endpoint=False)
    env.sync(full=True)
    check_counts(env.db, env.mock.expected())


def test_dc_responsavel_so_com_nome_de_exibicao(jira_env):
    """Item de assignee sem from/to (só fromString/toString = nome de exibição) ainda casa com a pessoa."""
    env = jira_env("dc", n_issues=90, dc_assignee_name_only=True)
    env.sync(full=True)
    exp = env.mock.expected()["assignee_changes"]
    assert env.db.query(AssigneeChange).count() == exp
    comuns = env.db.query(AssigneeChange).filter(AssigneeChange.to_person_id.isnot(None)).count()
    assert comuns >= exp * 0.9  # ex-funcionário não existe no cadastro; o resto precisa casar


@pytest.mark.parametrize("flavor", FLAVORS)
def test_ex_funcionario_no_historico_vira_pessoa(jira_env, flavor):
    """Quem saiu da empresa aparece só no histórico (de/para): a mudança não pode perder a ponta."""
    env = jira_env(flavor, n_issues=90)
    env.sync(full=True)
    ex = env.db.query(Person).filter_by(jira_ref="ex.silva").first()
    assert ex is not None
    n_mock = sum(1 for i in env.mock.live() for h in i.histories for x in h.items if x["field"] == "assignee" and x["from"] == "ex.silva")
    n_db = env.db.query(AssigneeChange).filter_by(from_person_id=ex.id).count()
    assert n_mock > 0 and n_db == n_mock


# --------------------------------------------------------------------------------------------- worklogs
@pytest.mark.parametrize("flavor", FLAVORS)
def test_worklogs_paginados_e_somam(jira_env, flavor):
    env = jira_env(flavor, n_issues=60, worklog_page_cap=2)
    env.sync(full=True)
    exp = env.mock.expected()
    assert env.db.query(Worklog).count() == exp["worklogs"]
    for m in env.mock.live():
        i = env.db.query(Issue).filter_by(id=m.id).one()
        assert sum(w.seconds for w in i.worklogs) == m.spent


def test_worklog_editado_e_removido_reflete_no_incremental(jira_env):
    env = jira_env("cloud", n_issues=40)
    env.sync(full=True)
    alvo = next(i for i in env.mock.live() if len(i.worklogs) >= 2)
    w_edit, w_del = alvo.worklogs[0], alvo.worklogs[1]
    w_edit.seconds += 7200
    del alvo.worklogs[1]
    env.mock._bump(alvo)
    env.sync(full=False)
    i = env.db.query(Issue).filter_by(id=alvo.id).one()
    got = {w.id: w.seconds for w in i.worklogs}
    assert got == {w.id: w.seconds for w in alvo.worklogs}
    assert i.time_spent_s == alvo.spent


# --------------------------------------------------------------------------------------------- incremental
@pytest.mark.parametrize("flavor", FLAVORS)
def test_incremental_traz_so_issues_alteradas(jira_env, flavor):
    env = jira_env(flavor, n_issues=60)
    env.sync(full=True)
    antes = counts(env.db)
    abertas = [i for i in env.mock.live() if i.status == "Em Andamento"][:3]
    for i in abertas:
        env.mock.transition(i.key, "Em Revisão")
    env.mock.reassign(abertas[0].key, ROSTER_OF(abertas[0]))
    out = env.sync(full=False)
    assert out["issues"] == 3, out
    assert "updated >=" in out["since"]
    depois = counts(env.db)
    assert depois["transitions"] == antes["transitions"] + 3
    assert depois["assignee_changes"] == antes["assignee_changes"] + 1
    for i in abertas:
        assert env.db.query(Issue).filter_by(key=i.key).one().status == "Em Revisão"
    # a próxima incremental relê só a janela de sobreposição (marca d'água − 5 min): as 3 de novo, sem duplicar nada
    assert env.sync(full=False)["issues"] == 3
    assert counts(env.db) == depois
    # fora da janela de 5 min não vem mais nada
    for i in env.mock.live():
        i.updated -= timedelta(minutes=30)
    assert env.sync(full=False)["issues"] == 0


def ROSTER_OF(m):
    from mock_jira import ROSTER
    return next(r for r in ROSTER[m.project] if r != m.assignee)


def test_incremental_respeita_fuso_do_usuario_jira(jira_env):
    """JQL `updated >= "yyyy-MM-dd HH:mm"` é interpretado no fuso do usuário do Jira (aqui UTC-3), não em UTC."""
    env = jira_env("cloud", n_issues=30, user_tz="America/Sao_Paulo")
    env.sync(full=True)
    alvo = next(i for i in env.mock.live() if i.status == "Em Andamento")
    env.mock.transition(alvo.key, "Em Teste")
    out = env.sync(full=False)
    assert out["issues"] == 1, f"a issue alterada agora não veio na incremental: {out['since']}"


def test_incremental_com_fuso_positivo(jira_env):
    env = jira_env("dc", n_issues=30, user_tz="Asia/Tokyo")
    env.sync(full=True)
    alvo = next(i for i in env.mock.live() if i.status == "Em Andamento")
    env.mock.transition(alvo.key, "Em Teste")
    assert env.sync(full=False)["issues"] >= 1


def test_incremental_sem_marca_dagua_faz_carga_completa(jira_env):
    env = jira_env("cloud", n_issues=20)
    out = env.sync(full=False)
    assert out["issues"] == env.mock.expected()["issues"]
    assert "updated >=" not in out["since"]


# --------------------------------------------------------------------------------------------- reconciliação
@pytest.mark.parametrize("flavor", FLAVORS)
def test_reconcile_marca_issues_removidas(jira_env, flavor):
    env = jira_env(flavor, n_issues=60)
    env.sync(full=True)
    abertas = [i for i in env.mock.live() if env.mock.category(i) != "done"]
    apagadas = abertas[:2]
    for i in apagadas:
        env.mock.delete_issue(i.key)
    gone = env.syncer().reconcile_open()
    assert gone == 2
    for i in apagadas:
        assert env.db.query(Issue).filter_by(key=i.key).one().deleted_at is not None
    assert env.db.query(Issue).filter(Issue.deleted_at.is_(None)).count() == env.mock.expected()["issues"]


def test_reconcile_nao_remove_issue_movida_de_projeto(jira_env):
    env = jira_env("cloud", n_issues=40)
    env.sync(full=True)
    alvo = next(i for i in env.mock.live() if env.mock.category(i) != "done" and i.project == "PAY")
    velha = alvo.key
    env.mock.move_issue(alvo.key, "PLT-999")
    assert env.syncer().reconcile_open() == 0, "issue movida (chave nova) foi tratada como removida"
    assert env.db.query(Issue).filter_by(key=velha).one().deleted_at is None


def test_reconcile_com_servidor_permissivo(jira_env):
    """Se o Jira ignora chaves inexistentes (sem 400), o resultado é o mesmo."""
    env = jira_env("dc", n_issues=40, strict_key_in=False)
    env.sync(full=True)
    ab = next(i for i in env.mock.live() if env.mock.category(i) != "done")
    env.mock.delete_issue(ab.key)
    assert env.syncer().reconcile_open() == 1


def test_issue_removida_reaparece_apos_restaurar(jira_env):
    env = jira_env("cloud", n_issues=30)
    env.sync(full=True)
    ab = next(i for i in env.mock.live() if env.mock.category(i) != "done")
    env.mock.delete_issue(ab.key)
    env.syncer().reconcile_open()
    ab.deleted = False
    env.mock._bump(ab)
    env.sync(full=False)
    assert env.db.query(Issue).filter_by(key=ab.key).one().deleted_at is None


# --------------------------------------------------------------------------------------------- 429 / 503 / erros
def test_429_com_retry_after_e_respeitado(jira_env, sleeps):
    env = jira_env("cloud", n_issues=20)
    env.mock.fail_next("changelog/bulkfetch", 429, retry_after=7)
    env.sync(full=True)
    assert any(4.8 <= s <= 9.2 for s in sleeps), sleeps  # 7s ±30% de jitter
    check_counts(env.db, env.mock.expected())


def test_429_periodico_durante_carga_grande(jira_env, sleeps):
    env = jira_env("cloud", n_issues=60, rate_limit_every=5, retry_after=2)
    env.sync(full=True)
    assert len(sleeps) >= 10 and all(s >= 1.3 for s in sleeps)
    check_counts(env.db, env.mock.expected())


def test_503_intermitente_usa_backoff_exponencial(jira_env, sleeps):
    env = jira_env("dc", n_issues=20)
    env.mock.fail_next("/search", 503, times=3)
    env.sync(full=True)
    esperas = sleeps[:3]
    assert len(esperas) == 3
    assert esperas[0] < esperas[1] < esperas[2] or esperas[2] > esperas[0]  # 1s, 2s, 4s (com jitter)
    check_counts(env.db, env.mock.expected())


def test_503_persistente_esgota_tentativas(jira_env, sleeps):
    env = jira_env("cloud", n_issues=10)
    env.mock.fail_next("/search/jql", 503, times=99)
    with pytest.raises(JiraError) as e:
        env.sync(full=True)
    assert "503" in str(e.value)
    assert len(sleeps) == env.client.max_retries


def test_falha_no_meio_nao_deixa_carga_parcial(jira_env, sleeps):
    env = jira_env("cloud", n_issues=30)
    env.mock.fail_next("/worklog", 503, times=999)
    with pytest.raises(JiraError):
        env.sync(full=True)
    env.db.rollback()
    assert env.db.query(Issue).count() == 0
    assert env.db.query(SyncState).count() == 0
    env.mock.faults.clear()
    env.sync(full=True)  # recupera
    check_counts(env.db, env.mock.expected())


@pytest.mark.parametrize("flavor", FLAVORS)
def test_401_credencial_errada_mensagem_clara(jira_env, flavor):
    env = jira_env(flavor, n_issues=10)
    c = env.new_client(token="errado")
    with pytest.raises(JiraError) as e:
        Syncer(env.db, c)
    msg = str(e.value)
    assert "401" in msg and ("credenciais" in msg.lower() or "autentica" in msg.lower())
    assert "JIRA_TOKEN" in msg


@pytest.mark.parametrize("flavor", FLAVORS)
def test_403_sem_permissao_mensagem_clara(jira_env, flavor):
    env = jira_env(flavor, n_issues=10)
    env.mock.fail_next("/search", 403, times=99)
    with pytest.raises(JiraError) as e:
        env.sync(full=True)
    msg = str(e.value)
    assert "403" in msg and "permiss" in msg.lower()


def test_jql_invalido_vira_erro_com_mensagem_do_jira(jira_env):
    env = jira_env("cloud", n_issues=10)
    with pytest.raises(JiraError) as e:
        list(env.client.search("assignee was currentUser()", ["summary"]))
    assert "400" in str(e.value) and "JQL" in str(e.value)


def test_resposta_html_de_sso_nao_vira_jsondecodeerror():
    """Base URL errada / SSO devolve 200 com HTML: precisa virar JiraError legível."""
    def h(req):
        return httpx.Response(200, text="<html>login</html>", headers={"content-type": "text/html"})
    c = JiraClient("dc", "https://jira.x", "t", transport=httpx.MockTransport(h))
    with pytest.raises(JiraError) as e:
        c.myself()
    assert "JSON" in str(e.value)


def test_erro_de_rede_vira_jiraerror_apos_tentativas(sleeps):
    def h(req):
        raise httpx.ConnectError("sem rota")
    c = JiraClient("cloud", "https://x", "t", "e", transport=httpx.MockTransport(h), max_retries=2)
    with pytest.raises(JiraError) as e:
        c.myself()
    assert "conex" in str(e.value).lower() or "rede" in str(e.value).lower()
    assert len(sleeps) == 2


# --------------------------------------------------------------------------------------------- status e fuso
def test_status_nao_mapeado_usa_categoria_do_jira_e_avisa(jira_env, caplog):
    env = jira_env("cloud", n_issues=30)
    a, b = [i for i in env.mock.live() if env.mock.category(i) != "done"][:2]
    env.mock.transition(a.key, "Aguardando QA", category="indeterminate")
    env.mock.transition(b.key, "Aguardando QA", category="done")
    with caplog.at_level("WARNING"):
        out = env.sync(full=True)
    assert env.db.query(Issue).filter_by(key=a.key).one().status_category == "active"
    assert env.db.query(Issue).filter_by(key=b.key).one().status_category == "done"
    assert out["status_sem_mapeamento"] == {"Aguardando QA": 2}
    assert "Aguardando QA" in caplog.text


@pytest.mark.parametrize("offset", [-180, 0, 330, 540])
def test_datas_com_qualquer_fuso_viram_o_mesmo_instante_utc(jira_env, offset):
    env = jira_env("cloud", n_issues=30, date_offset_minutes=offset)
    env.sync(full=True)
    for m in env.mock.live():
        i = env.db.query(Issue).filter_by(id=m.id).one()
        assert abs((i.created - m.created).total_seconds()) < 1, (m.key, offset)
        assert abs((i.updated - m.updated).total_seconds()) < 1
        tr = [(t.at, t.to_status) for t in i.transitions]
        esperado = [(h.at, x["to"]) for h in m.histories for x in h.items if x["field"] == "status"]
        assert len(tr) == len(esperado)
        for (a, s1), (b, s2) in zip(sorted(tr), sorted(esperado)):
            assert s1 == s2 and abs((a - b).total_seconds()) < 1


def test_parse_dt_formatos():
    assert parse_dt("2026-10-01T10:00:00.000+0000") == datetime(2026, 10, 1, 10)
    assert parse_dt("2026-10-01T10:00:00.000-0300") == datetime(2026, 10, 1, 13)
    assert parse_dt("2026-10-01T10:00:00Z") == datetime(2026, 10, 1, 10)
    assert parse_dt("2026-10-01T10:00:00.123+05:30") == datetime(2026, 10, 1, 4, 30, 0, 123000)
    assert parse_dt(None) is None


# --------------------------------------------------------------------------------------------- jobs / HTTP real
def test_jobs_run_sync_completo_com_reconciliacao(jira_env, monkeypatch):
    from jiradash import jobs
    env = jira_env("cloud", n_issues=30)
    ab = next(i for i in env.mock.live() if env.mock.category(i) != "done")
    out = jobs.run_sync(env.db, full=True, client=env.client)
    assert out["issues"] == env.mock.expected()["issues"] and out["removidas"] == 0
    env.mock.delete_issue(ab.key)
    out = jobs.run_sync(env.db, full=True, client=env.client)
    assert out["removidas"] == 1


def test_jobs_make_client_usa_settings(monkeypatch):
    from jiradash import jobs
    from jiradash.config import settings
    monkeypatch.setattr(settings, "jira_flavor", "dc")
    monkeypatch.setattr(settings, "jira_base_url", "https://jira.local/")
    monkeypatch.setattr(settings, "jira_token", "pat")
    c = jobs.make_client()
    assert c.flavor == "dc" and c.base == "https://jira.local" and c.http.headers["Authorization"] == "Bearer pat"


def test_jobs_sync_keys_de_issue_apagada_nao_explode(jira_env):
    from jiradash import jobs
    env = jira_env("cloud", n_issues=20)
    env.sync(full=True)
    ab = next(i for i in env.mock.live() if env.mock.category(i) != "done")
    env.mock.delete_issue(ab.key)
    jobs.sync_keys(env.db, [ab.key], client=env.client)  # webhook de issue excluída
    assert env.db.query(Issue).filter_by(key=ab.key).one().deleted_at is not None


@pytest.mark.parametrize("flavor", FLAVORS)
def test_http_real_em_thread_uvicorn(jira_env, flavor):
    from mock_jira import MockServer
    env = jira_env(flavor, n_issues=30)
    with MockServer(env.mock) as url:
        c = JiraClient(flavor, url, env.mock.token, env.mock.email)
        out = Syncer(env.db, c).run(env.scope, full=True)
    assert out["issues"] == env.mock.expected()["issues"]
    check_counts(env.db, env.mock.expected())


# --------------------------------------------------------------------------------------------- escrita
@pytest.mark.parametrize("flavor", FLAVORS)
def test_atribuicao_assignable_e_assign(jira_env, flavor):
    env = jira_env(flavor, n_issues=20)
    it = next(i for i in env.mock.live() if i.project == "PAY")
    assert env.client.assignable(it.key, "ana.souza") is True
    assert env.client.assignable(it.key, "igor.martins") is False  # outro projeto
    env.client.assign(it.key, "carla.mendes")
    assert it.assignee == "carla.mendes"
    with pytest.raises(JiraError):
        env.client.assign(it.key, "fantasma")
    # a mudança aparece na próxima incremental
    env.sync(full=True)
    assert env.db.get(Person, env.db.query(Issue).filter_by(key=it.key).one().assignee_id).jira_ref == "carla.mendes"
