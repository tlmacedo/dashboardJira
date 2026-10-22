"""Modelo de dados: o changelog é o fato central (ver relatório, seção de arquitetura)."""
from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import JSON, Boolean, Date, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


class Person(Base):
    __tablename__ = "person"
    id: Mapped[int] = mapped_column(primary_key=True)
    jira_ref: Mapped[str] = mapped_column(String(128), unique=True)  # accountId (Cloud) ou username (DC)
    name: Mapped[str] = mapped_column(String(200))
    email: Mapped[str | None] = mapped_column(String(200))
    role: Mapped[str] = mapped_column(String(60), default="membro")  # membro | gerente
    skills: Mapped[list] = mapped_column(JSON, default=list)
    weekly_hours: Mapped[float] = mapped_column(Float, default=40.0)
    focus_factor: Mapped[float] = mapped_column(Float, default=0.7)  # fração de horas em tarefas
    active: Mapped[bool] = mapped_column(Boolean, default=True)


class Squad(Base):
    __tablename__ = "squad"
    id: Mapped[int] = mapped_column(primary_key=True)
    key: Mapped[str] = mapped_column(String(40), unique=True)
    name: Mapped[str] = mapped_column(String(200))
    manager_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"))
    projects: Mapped[list] = mapped_column(JSON, default=list)
    components: Mapped[list] = mapped_column(JSON, default=list)
    wip_limit_per_person: Mapped[int] = mapped_column(Integer, default=3)
    members: Mapped[list["SquadMember"]] = relationship(back_populates="squad")


class SquadMember(Base):
    __tablename__ = "squad_member"
    id: Mapped[int] = mapped_column(primary_key=True)
    squad_id: Mapped[int] = mapped_column(ForeignKey("squad.id"))
    person_id: Mapped[int] = mapped_column(ForeignKey("person.id"))
    allocation: Mapped[float] = mapped_column(Float, default=1.0)  # 0..1 do tempo neste squad
    start: Mapped[date | None] = mapped_column(Date)
    end: Mapped[date | None] = mapped_column(Date)
    squad: Mapped[Squad] = relationship(back_populates="members")
    person: Mapped[Person] = relationship()


class Absence(Base):
    __tablename__ = "absence"
    id: Mapped[int] = mapped_column(primary_key=True)
    person_id: Mapped[int] = mapped_column(ForeignKey("person.id"))
    start: Mapped[date] = mapped_column(Date)
    end: Mapped[date] = mapped_column(Date)
    kind: Mapped[str] = mapped_column(String(40), default="ferias")  # sem motivo detalhado (privacidade)
    partial: Mapped[float] = mapped_column(Float, default=1.0)  # 1 = dia inteiro; 0,5 = meio período


class Holiday(Base):
    __tablename__ = "holiday"
    id: Mapped[int] = mapped_column(primary_key=True)
    day: Mapped[date] = mapped_column(Date, unique=True)
    name: Mapped[str] = mapped_column(String(120))


class Issue(Base):
    __tablename__ = "issue"
    id: Mapped[int] = mapped_column(primary_key=True)  # id numérico do Jira (a key pode mudar)
    key: Mapped[str] = mapped_column(String(40), index=True)
    project: Mapped[str] = mapped_column(String(40), index=True)
    summary: Mapped[str] = mapped_column(String(500))
    type: Mapped[str] = mapped_column(String(40))
    priority: Mapped[str] = mapped_column(String(40))
    status: Mapped[str] = mapped_column(String(80), index=True)
    status_category: Mapped[str] = mapped_column(String(20), index=True)  # todo | active | wait | blocked | done
    resolution: Mapped[str | None] = mapped_column(String(80))
    assignee_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"), index=True)
    reporter_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"))
    squad_id: Mapped[int | None] = mapped_column(ForeignKey("squad.id"), index=True)
    created: Mapped[datetime] = mapped_column(DateTime)
    updated: Mapped[datetime] = mapped_column(DateTime)
    resolved: Mapped[datetime | None] = mapped_column(DateTime, index=True)
    due: Mapped[date | None] = mapped_column(Date)
    original_estimate_s: Mapped[int | None] = mapped_column(Integer)
    remaining_s: Mapped[int | None] = mapped_column(Integer)
    time_spent_s: Mapped[int] = mapped_column(Integer, default=0)
    story_points: Mapped[float | None] = mapped_column(Float)
    labels: Mapped[list] = mapped_column(JSON, default=list)
    components: Mapped[list] = mapped_column(JSON, default=list)
    epic_key: Mapped[str | None] = mapped_column(String(40))
    parent_key: Mapped[str | None] = mapped_column(String(40))
    flagged: Mapped[bool] = mapped_column(Boolean, default=False)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime)
    transitions: Mapped[list["StatusTransition"]] = relationship(
        back_populates="issue", order_by="StatusTransition.at", cascade="all, delete-orphan"
    )
    assignee_changes: Mapped[list["AssigneeChange"]] = relationship(
        back_populates="issue", order_by="AssigneeChange.at", cascade="all, delete-orphan"
    )
    worklogs: Mapped[list["Worklog"]] = relationship(back_populates="issue", cascade="all, delete-orphan")


class StatusTransition(Base):
    __tablename__ = "status_transition"
    id: Mapped[int] = mapped_column(primary_key=True)
    changelog_id: Mapped[str] = mapped_column(String(40), unique=True)  # deduplicação (há relato de duplicatas)
    issue_id: Mapped[int] = mapped_column(ForeignKey("issue.id"), index=True)
    at: Mapped[datetime] = mapped_column(DateTime)
    from_status: Mapped[str | None] = mapped_column(String(80))
    to_status: Mapped[str] = mapped_column(String(80))
    author_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"))
    issue: Mapped[Issue] = relationship(back_populates="transitions")


class AssigneeChange(Base):
    __tablename__ = "assignee_change"
    id: Mapped[int] = mapped_column(primary_key=True)
    changelog_id: Mapped[str] = mapped_column(String(40), unique=True)
    issue_id: Mapped[int] = mapped_column(ForeignKey("issue.id"), index=True)
    at: Mapped[datetime] = mapped_column(DateTime)
    from_person_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"))
    to_person_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"))
    issue: Mapped[Issue] = relationship(back_populates="assignee_changes")


class Worklog(Base):
    __tablename__ = "worklog"
    id: Mapped[int] = mapped_column(primary_key=True)  # id do worklog no Jira
    issue_id: Mapped[int] = mapped_column(ForeignKey("issue.id"), index=True)
    author_id: Mapped[int | None] = mapped_column(ForeignKey("person.id"), index=True)
    started: Mapped[datetime] = mapped_column(DateTime)
    seconds: Mapped[int] = mapped_column(Integer)
    issue: Mapped[Issue] = relationship(back_populates="worklogs")


class DailySnapshot(Base):
    """Contagem por status/dia: alimenta o diagrama de fluxo cumulativo sem recalcular o passado."""
    __tablename__ = "daily_snapshot"
    id: Mapped[int] = mapped_column(primary_key=True)
    day: Mapped[date] = mapped_column(Date, index=True)
    squad_id: Mapped[int] = mapped_column(Integer, index=True)
    status: Mapped[str] = mapped_column(String(80))
    count: Mapped[int] = mapped_column(Integer)


class AssignmentSuggestion(Base):
    __tablename__ = "assignment_suggestion"
    id: Mapped[int] = mapped_column(primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime)
    kind: Mapped[str] = mapped_column(String(20), default="novo")  # novo | rebalanceamento
    issue_key: Mapped[str] = mapped_column(String(40), index=True)
    squad_id: Mapped[int] = mapped_column(Integer)
    from_person_id: Mapped[int | None] = mapped_column(Integer)
    suggested_person_id: Mapped[int] = mapped_column(Integer)
    candidates: Mapped[list] = mapped_column(JSON, default=list)  # ranking com os fatores do score
    reason: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(20), default="pendente")  # pendente|aprovada|rejeitada|aplicada
    decided_by: Mapped[str | None] = mapped_column(String(120))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime)
    final_person_id: Mapped[int | None] = mapped_column(Integer)
    override_reason: Mapped[str | None] = mapped_column(Text)
    engine_version: Mapped[str] = mapped_column(String(20), default="1")


class AuditLog(Base):
    __tablename__ = "audit_log"
    id: Mapped[int] = mapped_column(primary_key=True)
    at: Mapped[datetime] = mapped_column(DateTime)
    actor: Mapped[str] = mapped_column(String(120))
    action: Mapped[str] = mapped_column(String(60))
    detail: Mapped[dict] = mapped_column(JSON, default=dict)


class SyncState(Base):
    __tablename__ = "sync_state"
    id: Mapped[int] = mapped_column(primary_key=True)
    key: Mapped[str] = mapped_column(String(60), unique=True)
    value: Mapped[str] = mapped_column(Text)
