# Squads Dashboard guide

Portuguese version: [`guia-do-painel.md`](guia-do-painel.md). Frequently asked questions: [`faq.md`](faq.md). Role-based training (in Portuguese): `docs/treinamento/`.

> **Today's state (2026-10-29).** Jira access is off (`JIRA_LIVE_ENABLED=false`): the dashboard talks to no Jira server and the numbers you see come from **fictional demonstration data** (`DASHBOARD_DEMO_DATA=true`, only with `DASHBOARD_DEV_MODE=true`). Everything this guide says about real numbers holds after the user turns the flag on and the first sync has run. Audit and Integrity (and cards VG-13 and VG-14 of the Attention center) sit behind the DPO gate, which is closed by default until the data protection officer accepts.

This guide follows the backend card catalog and the dashboard menu: every menu item has a section, in menu order, and every card named exists in the catalog with the same name and the same roles. Screen and term names are the ones shown by the English interface. To install the dashboard, see [`docs/implantacao.md`](../implantacao.md) (Portuguese).

## Before you start

**Roles and what each one sees.** The role comes from `papeis.yaml` and sets the menu and the data. The **User** sees only their own; the **Squad Leader** sees their own and their squad; **Coordinator** and **Manager** see everyone. Access log, Synchronization, Configuration and Integrity are for the Manager only; Data quality, Distribution decisions, Card map, Audit and Divergence are for coordination and manager. In development (`DASHBOARD_DEV_MODE=true`) there is no login, the server acts as manager and all data shows, a red banner warns you and the "Role (simulation)" selector only shows the menu of that role: **data scoping by role only applies in production, with login**. In production access is through single sign-on (SSO/OIDC).

**Filters.** The work and deadlines screens have period, squad, person, type and priority (each screen shows the ones that make sense). Filters live in the URL: copy the address to share the same view.

**Time unit.** The default is **Business days**; the option **Calendar days** is available. The time zone is America/Manaus.

**Task drawer.** Every number on a card with "Opens tasks: yes" opens the list of tasks behind it, with the same total. Inside the drawer there is a CSV export and, when Jira access is on, the "Open in Jira" link. The key of each row opens the task drawer; "Open in new tab" takes the same view to the detail page.

**Screen states.** Loading, empty (with "Clear filters"), error (with "Try again"), no permission (the message says which roles see the screen), expired session and invalid filter. Incomplete data shows as a warning with a path to Data quality.

**Symbols.** `✓` ok, `●` attention, `▲` problem, `■` neutral. They always come with text: color is never the only signal. Themes are Light, Dark and Sidia; languages are Portuguese (Brazil) and English.

**People.** People tables are in alphabetical order. The dashboard has no ranking and no score of people.

## How to read the numbers

- **On time and Adjusted on time.** Two metrics side by side. Only completed tasks (done category, a filled-in resolution that is not a cancellation), not an Epic, due in the period count. "On time" counts those done by the due date (resolution day in Manaus time). "Adjusted on time" also counts those with a label that justifies the delay (list in `labels_atraso.yaml`).
- **Evidence, not proof.** Audit and Integrity signals show what deserves a human check. They are never a conclusion about a person.
- **no capacity.** When a person's capacity in a week is zero (vacation, holiday, absence), occupancy does not become a percentage: it shows "no capacity".
- **Business days and Calendar days.** Time counts business days by default (weekends and calendar holidays left out); the filter allows calendar days.
- **Small sample.** With fewer than 5 tasks, percentiles (P50 and P85) show with a small-sample warning.

## DPO gate

The DPO gate stops Audit and Integrity from serving real data before the data protection officer (DPO) accepts. Today (code on `main`):

- **What goes through the gate:** Audit (cards AU-01 to AU-03, DV-01, and VG-14 of the Attention center) and Integrity (cards IN-01 to IN-08, and VG-13 of the Attention center). With the gate closed the screens show "This screen stays off until the data protection officer (DPO) approves its use", no data is queried and the server answers with the code `AGUARDANDO_ACEITE_DPO`. The field-by-field history in the Audit drawer opens only with the gate open.
- **When it opens:** only with the acceptance set in the configuration (`DASHBOARD_DPO_ACEITE=true` with the reference of the opinion, turned on only by the user) or in the demonstration: development mode, Jira off and a database with no truly synchronized data. It is **closed by default**; in production without acceptance it is closed.
- **When it closes again:** any synchronization state of a Jira instance in the database closes the demonstration door, even if the flag is turned off later; only the acceptance keeps it open.
- **Notice for coordination:** when some item stays out because of the gate, the Attention center shows "Some items stay out of this list until the data protection officer (DPO) approves the use of the sensitive screens you are allowed to see" (code `ITENS_AGUARDANDO_ACEITE_DPO`), without naming the module.

## My data and data protection

Every role has the **My data** screen (`meusDados`, route `/api/titular/meus-dados`): it shows only the person's own record: counts of tasks, changes and logged hours, planned absences (without the reason), roles, the categories processed with purpose and retention, and the person's accesses in the last 90 days. The button "Download my data (JSON)" generates the same content in the browser. The screen does not accept a request for someone else's data and does not show the content of Audit or Integrity signals about the person. A former member with no current role gets "no permission" and exercises the rights through the DPO channel. To correct data, fix it in Jira (the dashboard reflects it on the next sync); to ask for deletion, go through the DPO channel. See [`docs/lgpd/atendimento-ao-titular.md`](../lgpd/atendimento-ao-titular.md).

**Retention by category.** The default is 24 months for every category (a provisional decision, to be confirmed with the DPO), each with its own variable from 1 to 120 months: `RETENTION_HISTORICO_MONTHS` (old resolved tasks, changes, logged hours), `RETENTION_ACCESS_LOG_MONTHS` (access log), `RETENTION_DECISOES_MONTHS` (distribution decisions) and `RETENTION_EXECUCOES_MONTHS` (sync and backup runs). A short historical retention also shortens the window of the first synchronization.

## Home

### `central` — Attention center

**Question it answers.** What needs attention now, and where is it?

**Who sees it.** Coordinator, Manager.

This is the home of the coordination and the manager. It gathers the VG cards, each with its number and the task drawer. Card VG-13 (integrity evidence) is shown only to the manager, and VG-14 (Audit items) to coordination and manager, both only when the DPO gate is open (see "DPO gate"). If some item stays out because of the gate, coordination sees a notice, without a module name.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| VG-01 | Open tasks | How many tasks are open (not done) in the viewer's scope. It opens the same tasks as the Open tasks card. | Coordinator, Manager | yes |
| VG-02 | Completed in the last 30 days | How many tasks were completed in the last 30 days, compared with the previous 30 days. It opens the same tasks as the Completed card. | Coordinator, Manager | yes |
| VG-03 | Cycle of completed tasks (30 days) | Median (P50) and P85 of the cycle, from start of work to completion, of the tasks completed in the last 30 days. It opens the tasks behind the range. | Coordinator, Manager | yes |
| VG-04 | On time and adjusted on time | Of the tasks due in the last 90 days, how many finished on time and how many on adjusted time (with a justified delay). Both metrics sit side by side. | Coordinator, Manager | yes |
| VG-05 | Overdue | Open tasks whose due date has passed. It opens the same tasks as the Neglected card. | Coordinator, Manager | yes |
| VG-08 | High and medium risk | How many open tasks are at high risk and at medium risk by the explainable score. It opens the same tasks as the Risks card. | Coordinator, Manager | yes |
| VG-09 | Attention center | The indicators that call for the attention of the coordination and the manager: overdue, risk, capacity, predicted delays, no update, stuck, no assignee, blocked, goals, checklist and review, divergence, changes without authorization and estimate. Each indicator opens its tasks. | Coordinator, Manager | yes |
| VG-10 | Deliveries per week | The tasks completed in each of the last 12 weeks. Each column opens the tasks delivered in the week. | Coordinator, Manager | yes |
| VG-11 | When the backlog ends | The backlog size and the date it should end at the recent pace, at three confidence levels (P50, P85 and P95). It opens the backlog tasks. | Coordinator, Manager | yes |
| VG-12 | Occupancy per squad | The average occupancy and the Jain index of each squad over the next 4 weeks. It is an indicator of the group, with no grade for a person. | Coordinator, Manager | no |
| VG-13 | People with a high indication | The integrity evidence of people with a high indication. It is an indication to check, never proof. Only the manager sees it, and only with the DPO lock open. | Manager | yes |
| VG-14 | Audit items on the Central | The unauthorized changes and Task × Review divergences that the Central highlights. They are indications to check, never proof. They only show with the DPO lock open. | Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/central.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `squad` — My squad

**Question it answers.** How is my squad doing this week?

**Who sees it.** Squad Leader.

Home of the Squad Leader: the menu item is for the Squad Leader only, and the screen accepts `?squad=` in the URL to pick the squad. It shows each person's load in alphabetical order and without a score, what is due in 7 days with the real slack, absences (without the reason) and risk. Each number opens the tasks of the source card (Load, Due dates, Capacity and Risks) with the same squad filter. This screen has no cards of its own in the catalog.

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.


### `meu` — My dashboard

**Question it answers.** What do I have to do, and what is tight?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

Home of the user. In production it shows only what is yours; the squad leader can open the dashboard of a person in their squad. In the demonstration the development user has no tasks, so indicators MP-01, MP-03, MP-05 and MP-06 show zero.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| MP-01 | My open tasks | How many open tasks are with you and how many of them are due in the next 7 days. Each number opens your tasks. | User, Squad Leader, Coordinator, Manager | yes |
| MP-02 | My occupancy over 4 weeks | How much of your capacity over the next 4 weeks your queue takes, after absences and holidays. | User, Squad Leader, Coordinator, Manager | no |
| MP-03 | At risk | How many of your open tasks are at medium or high risk by the explainable score. It opens those tasks. | User, Squad Leader, Coordinator, Manager | yes |
| MP-04 | My upcoming absences | The absence periods registered for you from today on. The dashboard neither stores nor shows the reason. | User, Squad Leader, Coordinator, Manager | no |
| MP-05 | My queue | Your open tasks in the order the schedule would run them, with the finish date predicted by your capacity and a warning when they pass the due date. | User, Squad Leader, Coordinator, Manager | yes |
| MP-06 | On time and adjusted on time | Of your tasks due in the last 90 days, how many finished on time and how many on adjusted time (with a justified delay), side by side. Each number opens the tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `meusDados` — My data

**Question it answers.** What does the dashboard keep about me, what for, and for how long?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

Data subject screen (LGPD), for every role: it shows only the person's own record and their accesses of the last 90 days, and lets them download the same content as JSON. It does not accept a request for another person's data. A former member with no current role gets "no permission" and goes to the DPO channel. Details in the section "My data and data protection".

**Where the numbers come from.** This screen has no sheet or cards: it reads the person's own record through the route `/api/titular/meus-dados`.


## Work in progress

### `abertas` — Open

**Question it answers.** What is open right now, for how long, and what is due?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| AB-01 | Open | How many tasks are open right now and, of those, how many are in progress. | User, Squad Leader, Coordinator, Manager | yes |
| AB-02 | Historical age by type (P50 and P85) | How long the tasks of each type took, from the start of work to completion, over the last 16 weeks: the reference for deciding whether an open task is old. | User, Squad Leader, Coordinator, Manager | yes |
| AB-04 | Above the type P85 | In-progress tasks older than 85% of the completed tasks of the same type: candidates for attention. | User, Squad Leader, Coordinator, Manager | yes |
| AB-05 | Open by status | How many open tasks are in each status, in flow order. | User, Squad Leader, Coordinator, Manager | yes |
| AB-06 | Open by priority | How many open tasks there are for each Jira priority. | User, Squad Leader, Coordinator, Manager | yes |
| AB-07 | Open by age | How many open tasks are in each age band: up to 7, 8 to 14, 15 to 30 and more than 30 days. | User, Squad Leader, Coordinator, Manager | yes |
| AB-08 | Open by due date | How many open tasks are already overdue, due within 7 days, due later or have no due date. | User, Squad Leader, Coordinator, Manager | yes |
| AB-09 | Open tasks table | All the open tasks in your view, with status, priority, age, due date and remaining hours; sortable and exportable. | User, Squad Leader, Coordinator, Manager | yes |
| AB-10 | Open by field | How many open tasks there are for each value of Checklist Type, SKU Type, Model Name, Model Category, TG Request Type and Carrier Code; each group opens its tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/abertas.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `epicos` — Epics

**Question it answers.** How are the epics doing: how much has been delivered, what is overdue and when does it finish?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| EP-01 | Epics | Per epic: tasks, completed, progress, the oldest open one, overdue, points and forecast in weeks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/epicos.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `negligenciadas` — Neglected

**Question it answers.** What is stuck, unowned, overdue or without real movement?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| NG-01 | No update | In-progress tasks that Jira has not updated for more than 7 days. | User, Squad Leader, Coordinator, Manager | yes |
| NG-02 | No assignee | Open tasks that nobody has taken. | User, Squad Leader, Coordinator, Manager | yes |
| NG-03 | Overdue | Open tasks whose due date has passed. | User, Squad Leader, Coordinator, Manager | yes |
| NG-04 | Blocked | Open tasks that are blocked or flagged with an impediment. | User, Squad Leader, Coordinator, Manager | yes |
| NG-05 | Stuck in status | In-progress tasks that have been in the same status for too long. | User, Squad Leader, Coordinator, Manager | yes |
| NG-06 | To do for more than 30 days | Tasks that have been waiting in the queue for more than 30 days. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/negligenciadas.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `riscos` — Risks

**Question it answers.** Which open tasks are most at risk of being late or stuck, and why?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| RI-01 | High and medium risk | How many open tasks are at high risk and at medium risk. | User, Squad Leader, Coordinator, Manager | yes |
| RI-02 | Tasks at risk | The list of tasks at high or medium risk, with the main factor and the points of each factor. The low level stays in the detail view for checking. | User, Squad Leader, Coordinator, Manager | yes |
| RI-03 | Risk signals by person | How many tasks at risk and how many open tasks each person in your scope has, as a proportion. No ranking: alphabetical order. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/riscos.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `previsao` — Forecast

**Question it answers.** When does the backlog finish and how much fits in the next weeks, by the real delivery and arrival pace?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| PV-01 | Backlog | How many open tasks are still left to deliver in your scope. | User, Squad Leader, Coordinator, Manager | yes |
| PV-02 | Finishes in | In how many weeks the backlog finishes, in P50, P85 and P95, with the date of the target week. The card opens the backlog tasks (the numerator of the forecast). | User, Squad Leader, Coordinator, Manager | yes |
| PV-03 | Forecast confidence | How much history supports the forecast: low, medium or high, by weeks with data. The card opens the deliveries of the window. | User, Squad Leader, Coordinator, Manager | yes |
| PV-04 | Throughput and arrivals per week | How many tasks were delivered and how many were created in each closed week (Monday to Sunday, Manaus). | User, Squad Leader, Coordinator, Manager | yes |
| PV-05 | How many items in 2, 4 and 8 weeks | How many tasks should come out in 2, 4 and 8 weeks, in conservative P50, P85 and P95. It is a projection, with no tasks behind it. | User, Squad Leader, Coordinator, Manager | no |
| PV-06 | Forecast per task | For each in-progress task, how much is left according to the history of cycles of the same type that went past its age. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/previsao.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `fluxoDaSemana` — Weekly flow

**Question it answers.** Is the backlog growing or shrinking? How many tasks come in, start and leave each week.

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| FX-01 | Weekly flow | Per week, how many tasks came in, started and left, with cancelled ones apart. Each bar opens that week's tasks for that series. | User, Squad Leader, Coordinator, Manager | yes |
| FX-02 | Backlog balance | The totals for the last 12 weeks (came in, started, left and cancelled) and the growing-backlog signal. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/fluxo-da-semana.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `agingWip` — Aging by stage

**Question it answers.** Where have tasks been sitting in a stage longer than normal, before they turn into delays?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| AW-01 | Aging by stage | For each flow stage, the in-progress tasks as dots with their age in the stage, against the normal band (P50 and P85) of that same stage. Each stage and each "above normal" count opens its tasks. | User, Squad Leader, Coordinator, Manager | yes |
| AW-02 | Above normal | How many tasks are in progress and how many of them have been in their stage longer than that stage's historical P85. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/aging-wip.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `bloqueios` — Blocking between tasks

**Question it answers.** Which tasks are held up by another task that is still open, and does one due date fit the other?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| BQ-01 | Blocked by another task | Open tasks that depend on another task that is still open, with the blocker and its due date. | User, Squad Leader, Coordinator, Manager | yes |
| BQ-02 | Blockers | The open tasks that hold others up and are within your reach. Those of people you cannot see are counted apart, with no key or name. | User, Squad Leader, Coordinator, Manager | yes |
| BQ-03 | Due date conflict | Tasks blocked by a task that is due AFTER them: one due date does not fit the other. | User, Squad Leader, Coordinator, Manager | yes |
| BQ-04 | Blocked with no recorded cause | Tasks in a blocked status or with the impediment flag but with no open blocker in Jira: the reason for the block is not recorded. | User, Squad Leader, Coordinator, Manager | yes |
| BQ-05 | Check in Jira | Tasks whose blocking link points to a task that is not in the base: from here there is no way to know whether the blocker is still open. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/bloqueios.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## Results

### `fechadas` — Closed

**Question it answers.** What was delivered in the period, how fast, and on time?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

This screen also holds the Cycle control (cards CT-01 and CT-02), which has its own sheet in `app/config/cards/controle-ciclo.card.yaml`.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| FE-01 | Completed | How many tasks were completed in the period. | User, Squad Leader, Coordinator, Manager | yes |
| FE-02 | Points delivered | Sum of the points of the tasks completed in the period; opens the completed tasks that have points. | User, Squad Leader, Coordinator, Manager | yes |
| FE-03 | Cycle (P50 and P85) | How long the completed tasks took from the start of work to the resolution. | User, Squad Leader, Coordinator, Manager | yes |
| FE-04 | Lead time (P50 and P85) | How long the completed tasks took from creation to the resolution. | User, Squad Leader, Coordinator, Manager | yes |
| FE-05 | On time and adjusted on time | Of the completed tasks due in the period, how many were done by the due date and how many were done by the due date also counting those with a label that justifies the delay. | User, Squad Leader, Coordinator, Manager | yes |
| FE-06 | Cancelled and without resolution | Tasks closed with a cancellation resolution in the period and tasks in a done status without a resolution. | User, Squad Leader, Coordinator, Manager | yes |
| FE-07 | Deliveries per week | How many tasks were completed in each week (Monday to Sunday, Manaus time). | User, Squad Leader, Coordinator, Manager | yes |
| FE-08 | Cycle scatter | Each completed task as a point (resolution day and cycle days), with P50, P85 and P95. | User, Squad Leader, Coordinator, Manager | yes |
| FE-09 | Cycle by type | P50 and P85 of the cycle of each task type; types are never mixed. | User, Squad Leader, Coordinator, Manager | yes |
| FE-10 | Deliveries by project | How many completed tasks there are per project (key prefix). | User, Squad Leader, Coordinator, Manager | yes |
| FE-11 | Deliveries by person | How many tasks each person finished and how many they took part in, with points, cycle and estimated and logged hours. No ranking: alphabetical order. | User, Squad Leader, Coordinator, Manager | yes |
| FE-12 | Completed tasks table | All the completed tasks of the period with cycle, lead, due date, deadline status and hours; sortable and exportable. | User, Squad Leader, Coordinator, Manager | yes |
| FE-13 | Completed by field | How many tasks completed in the period there are for each value of Checklist Type, SKU Type, Model Name, Model Category, TG Request Type and Carrier Code; each group opens its tasks. | User, Squad Leader, Coordinator, Manager | yes |
| CT-01 | Cycle control | Each completed task as a dot (completion day x days of cycle), by type, with the moving average and the P50 and P85 bands. It shows whether the process is stable and which tasks fall off the curve. Each dot opens that day's tasks of that type. | User, Squad Leader, Coordinator, Manager | yes |
| CT-02 | Monthly cycle trend | P50 and P85 of the cycle month by month, per type, to see whether the process is getting faster. Each month opens that month's completed tasks of that type. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/fechadas.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `negativas` — Negative

**Question it answers.** What went off track in the period: reopened, sent back, cancelled or delivered late?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| NE-01 | Reopened | Tasks that went back from done to not done in the period. | User, Squad Leader, Coordinator, Manager | yes |
| NE-02 | Sent back | Tasks that went back to an earlier stage of the flow in the period. | User, Squad Leader, Coordinator, Manager | yes |
| NE-03 | Cancelled | Tasks closed in the period with a cancellation resolution (Won't Do, Duplicate...). | User, Squad Leader, Coordinator, Manager | yes |
| NE-04 | Completed late | Tasks completed in the period after the due date, and how many of them have no label that justifies the delay. | User, Squad Leader, Coordinator, Manager | yes |
| NE-05 | Open and overdue | Tasks still open whose due date has already passed. | User, Squad Leader, Coordinator, Manager | yes |
| NE-06 | Estimate overrun | Tasks completed in the period whose time spent went over the original estimate by more than 25%. | User, Squad Leader, Coordinator, Manager | yes |
| NE-07 | Many assignee changes | Tasks that changed assignee 3 times or more in the period. | User, Squad Leader, Coordinator, Manager | yes |
| NE-08 | Reopen, send-back and lateness rates | What fraction of the period's completed tasks was reopened, sent back, delivered late and delivered late without justification. Above 20% the screen shows ▲. | User, Squad Leader, Coordinator, Manager | yes |
| NE-09 | Where they stayed longest | Of the reopened, returned, canceled and late-completed tasks, how many stayed longest in each status; each group opens its tasks. | User, Squad Leader, Coordinator, Manager | yes |
| NE-10 | Negatives by assignee | Reopened, sent back and late deliveries of each person in your scope. No ranking: alphabetical order. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/negativas.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `cruzamentos` — Cross-tab

**Question it answers.** How do the open tasks spread across two axes of your choice, without asking for a new screen?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CZ-01 | Cross-tab | Cross-table of the open tasks by two axes of your choice (type, status, squad, age band...), with the count or the P50 age in each cell. Each cell and each total opens its tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/cruzamentos.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `tempo` — Time in status

**Question it answers.** How long tasks spend in each status and which open tasks are stuck beyond normal.

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| TS-01 | Tasks analyzed | How many tasks completed in the period go into this screen's calculations. | User, Squad Leader, Coordinator, Manager | yes |
| TS-02 | Flow efficiency | What share of the analyzed tasks' time was spent in active statuses. | User, Squad Leader, Coordinator, Manager | yes |
| TS-03 | Stuck beyond normal | Open tasks stuck in their current status beyond what is normal for that status. | User, Squad Leader, Coordinator, Manager | yes |
| TS-04 | Time per status, median / P85 | Median and P85 of the time completed tasks spent in each status. | User, Squad Leader, Coordinator, Manager | yes |
| TS-05 | Where the time goes | The relative weight of each status in the tasks' time, to find where they wait. | User, Squad Leader, Coordinator, Manager | yes |
| TS-06 | Median by person × status (days) | Median days of each person in each status, in alphabetical order and with no ranking. | User, Squad Leader, Coordinator, Manager | yes |
| TS-07 | Open tasks stuck beyond normal | List of the open tasks stuck beyond normal. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/tempo-em-status.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `esforco` — Effort per task

**Question it answers.** How much effort did each completed task cost, and how is it split among people by ownership?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| EF-01 | Effort per closed task | The effort of each task completed in the period, in three components side by side (type weight, tests and hours) and the composite index. The card opens the tasks. | User, Squad Leader, Coordinator, Manager | yes |
| EF-02 | Effort per person | The effort of completed tasks split among the people in your view by ownership (the share of time with the task), not by the worklog author. No ranking. The card opens the person's tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/esforco.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## Deadlines

### `metas` — Goals & deadlines

**Question it answers.** Were the deliveries done by the due date? And by the originally agreed date?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

The two metrics sit side by side here: "On time" and "Adjusted on time" (card MT-01). See "How to read the numbers".

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| MT-01 | On time and adjusted on time | Of the completed tasks due in the period, how many were done by the due date (on time) and how many also counting those with a label that justifies the delay (adjusted on time), side by side. | User, Squad Leader, Coordinator, Manager | yes |
| MT-02 | On time by the original date | How many tasks were done by the FIRST agreed due date, before deadlines were rescheduled. | User, Squad Leader, Coordinator, Manager | yes |
| MT-03 | Early | Tasks completed at least 1 day before the due date. | User, Squad Leader, Coordinator, Manager | yes |
| MT-04 | On D-1 or D-2 | Tasks completed 1 or 2 days before the due date. | User, Squad Leader, Coordinator, Manager | yes |
| MT-05 | On the day | Tasks completed exactly on the due date. | User, Squad Leader, Coordinator, Manager | yes |
| MT-06 | Late | Tasks completed after the due date. | User, Squad Leader, Coordinator, Manager | yes |
| MT-07 | No due date | Tasks completed in the period that have no due date and therefore stay out of the deadline rates. | User, Squad Leader, Coordinator, Manager | yes |
| MT-08 | Deliveries by period | How many tasks of the universe are due in each period (day, week or month), split into on the day, early and late. | User, Squad Leader, Coordinator, Manager | yes |
| MT-09 | % on time by period | The on-time rate in each period, compared with the target. | User, Squad Leader, Coordinator, Manager | yes |
| MT-10 | Resolution day distribution | How many tasks were done in each band relative to the due date: 3 or more days early, 2 days early, 1 day early, on the day, 1 to 3 days late and more than 3 days late. | User, Squad Leader, Coordinator, Manager | yes |
| MT-11 | Deadlines by type | Universe, on time and adjusted on time for each task type, with the state against the target. | User, Squad Leader, Coordinator, Manager | yes |
| MT-12 | Deadlines by priority | Universe, on time and adjusted on time for each priority, with the state against the target. | User, Squad Leader, Coordinator, Manager | yes |
| MT-13 | Deadlines by squad | Universe, on time and adjusted on time for each squad, with the state against the target. | User, Squad Leader, Coordinator, Manager | yes |
| MT-14 | Deadlines by person | Universe, on time and adjusted on time for each person in your view, with the state against the target. No ranking: alphabetical order. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/metas.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `vencimentos` — Due this week

**Question it answers.** What is due in the coming days and who is at risk of being late?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| VE-01 | Due in the coming days | How many open tasks are due in the next 7 or 14 days, with the forecast finish and the real slack of each. | User, Squad Leader, Coordinator, Manager | yes |
| VE-02 | At risk of being late | How many of those due in the coming days are expected to finish after the due date or have no finish forecast. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/vencimentos.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `checklistReview` — Checklist × Review

**Question it answers.** Was each checklist's review done on time, by someone else and with a plausible duration?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CR-01 | Checklist × Review pairs | The checklists that have a review linked to them through the blocking link, one pair per row. The card opens the checklists of the pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-02 | Checklists without a review | Checklists that have no review linked through the blocking link. The card opens those checklists. | User, Squad Leader, Coordinator, Manager | yes |
| CR-03 | Review cycle (median) | How long a review takes from its first move into progress until resolution, as a median. The card opens the pairs with a completed review. | User, Squad Leader, Coordinator, Manager | yes |
| CR-04 | Review within the checklist cycle | What share of the checklist cycle the review takes up, as a median. The card opens the pairs with a completed review. | User, Squad Leader, Coordinator, Manager | yes |
| CR-05 | Review created after the checklist | How many hours after the checklist the review is created, as a median. The card opens the pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-06 | Review due after the checklist | Pairs where the review's due date is later than the checklist's: the review deadline does not fit the parent's. The card opens those pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-07 | Same person on both | Pairs where the checklist and the review have the same assignee: there is no independent review. The card opens those pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-08 | Pairs with an alert | Pairs with at least one of the six alerts (review due later, parent closed first, same person, short review with reopened parent, overdue review, late review). The card opens the pairs and each alert opens its own. | User, Squad Leader, Coordinator, Manager | yes |
| CR-10 | Learnings | Sentences the panel only writes when the sample allows: review duration × parent reopening, labels with more reopening and pair patterns. | User, Squad Leader, Coordinator, Manager | no |
| CR-11 | Review duration × reopening | For each band of the review's active hours, how many pairs there are and in how many the checklist was reopened. Each band opens its pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-12 | Chain delays | Pairs where the review finished after the checklist's due date, pushing back the parent's closing. It is a count of pairs, with no names. The card opens those pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-13 | Labels and outcome | For each label, in how many pairs it appears, in how many the checklist was reopened and the lift against the average. Each label opens its pairs. | User, Squad Leader, Coordinator, Manager | yes |
| CR-14 | Label differences | Labels that exist only on the checklist or only on the review, with the number of pairs. Each row opens the pairs. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/checklist-review.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## People & planning

### `carga` — Load per person

**Question it answers.** How much work does each person have queued, and how much fits in the capacity of the next 4 weeks?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CP-01 | Load balance (Jain index) | How similar the occupancy of the people in your view is: 1.0 = all equal. It is an index of the group, never a score for a person. | User, Squad Leader, Coordinator, Manager | no |
| CP-02 | Above capacity and with slack | How many people are above capacity and how many have slack. The card opens the tasks in the queue of those people. | User, Squad Leader, Coordinator, Manager | yes |
| CP-03 | Load per person | For each person, in alphabetical order: open, in progress, queue in hours, 4-week capacity, occupancy, absence, overdue, above P85 and high risk. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/carga.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `calendario` — Team calendar

**Question it answers.** Who is absent, when are the holidays and what is due each day?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CL-01 | Team calendar | Holidays, absences and due dates of each day, with the team capacity of the day. Each day opens the tasks due on it. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/calendario.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `capacidade` — Capacity

**Question it answers.** Where does capacity not cover the work, and which delays come from absences?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CA-01 | Load and absence alerts | How many alerts of weeks with load above the limit or with absence and scheduled work. | Squad Leader, Coordinator, Manager | no |
| CA-02 | Forecast delays | How many tasks are expected to finish after the due date, given the person's capacity. | Squad Leader, Coordinator, Manager | yes |
| CA-03 | Delays caused by absence | How many of the forecast delays would not exist if the person had no absence. | Squad Leader, Coordinator, Manager | yes |
| CA-04 | Capacity balance (Jain) | How similar the occupancy of the people in your view is: 1.0 = all equal. It is an index of the group, never a score for a person. | Squad Leader, Coordinator, Manager | no |
| CA-05 | Capacity map | Each person's occupancy over 8 weeks; "abs." when absence removes all the capacity of the week. | Squad Leader, Coordinator, Manager | no |
| CA-06 | Alerts per person | Each high-load alert or absence-with-scheduled-work alert, with the hours to reschedule. | Squad Leader, Coordinator, Manager | no |
| CA-07 | Planned absences | Who is absent in the next 8 weeks. The panel neither stores nor shows the reason. | Squad Leader, Coordinator, Manager | no |
| CA-08 | Holidays in the horizon | The holidays in the next 8 weeks, which count as zero capacity. | Squad Leader, Coordinator, Manager | no |
| CA-09 | Forecast delay list | Each task expected to be late, with due date, forecast finish and the cause (absence or load). | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/capacidade.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `plano` — 4–8 week plan

**Question it answers.** How many hours does each person have planned for the coming weeks, against capacity?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| PL-01 | 4 to 8 week plan | Planned hours against each person's capacity in each week. Each cell opens the tasks that fill the week. | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/plano.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `cobertura` — Absence coverage

**Question it answers.** What work is left without an owner in the coming absences and who in the squad can cover it?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CB-01 | Absences with work to cover | Upcoming absences of people in your view and the work left uncovered: affected tasks, hours to replan, skills requested and who can cover. The card opens the affected tasks. | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/cobertura.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `simulador` — Absence simulator

**Question it answers.** What would slip if a person were away for a period? Read-only simulation: nothing is saved.

**Who sees it.** Squad Leader, Coordinator, Manager.

Read-only simulation: nothing is saved.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| SI-01 | Absence simulator | What would slip if a person were away for a period: the tasks that become late and the hours to replan, with occupancy before and after. Nothing is saved. The card opens the tasks that become late. | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/simulador.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `competencias` — Skills

**Question it answers.** Who has each skill and which ones depend on a single person or on nobody?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CM-01 | Skills matrix | Who has each skill in your view and which skills depend on a single person. | User, Squad Leader, Coordinator, Manager | no |
| CM-02 | Skills requested with nobody | Open tasks that ask for a skill (label skill:x) that nobody in your view has. The card opens the tasks. | User, Squad Leader, Coordinator, Manager | yes |
| CM-03 | Demand per skill | Open tasks and hours per requested skill. The card opens the skill's tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/competencias.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `revisoes` — Pending reviews

**Question it answers.** How many reviews does each reviewer have pending, how many are overdue and how many passed the review deadline?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| RV-01 | Pending reviews per reviewer | The open reviews of each reviewer in your view, how many are overdue and how many passed the review deadline. The card opens the tasks. | User, Squad Leader, Coordinator, Manager | yes |
| RV-02 | Reviews without a reviewer | Open reviews with no assignee (coordination and manager only). The card opens the tasks. | Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/revisoes.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `posse` — Task ownership

**Question it answers.** Who held each task and for how long, and how many times did the assignee change?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| PS-01 | Ownership per task | The tasks of the period (resolved in it, plus the open ones) with how long each person held them and how many times the assignee changed. The card opens the tasks. | User, Squad Leader, Coordinator, Manager | yes |
| PS-02 | Ownership per person | In how many tasks each person in your view held ownership and for how long. No ranking: people in alphabetical order. The card opens the person's tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/posse.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `cargaTipo` — Load by type/OS

**Question it answers.** How much open and closed work is there per task type and OS version?

**Who sees it.** User, Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CG-01 | Open by type and OS | Open tasks and queue hours by task type and OS version. The card opens the cell's tasks. | User, Squad Leader, Coordinator, Manager | yes |
| CG-02 | Closed by type and OS | Tasks completed in the period and their effort by task type and OS version. The card opens the cell's tasks. | User, Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/carga-tipo.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `tendencia` — Occupancy trend

**Question it answers.** Is each person's overload recurring or was it one-off in the last 12 weeks?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| TD-01 | Occupancy trend | Each person's occupancy over the last 12 weeks and whether overload is recurring or one-off. | Squad Leader, Coordinator, Manager | no |

**Where the numbers come from.** Sheet `app/config/cards/tendencia.card.yaml` (formula, example and acceptance).


### `gargalos` — Bottlenecks

**Question it answers.** Where does work pile up and who is above capacity?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| GA-01 | 60-day cumulative flow | How many tasks were in each status at the end of each of the last 60 days, in flow order. | Squad Leader, Coordinator, Manager | no |
| GA-02 | Stage status | How many tasks are in each stage today and where there is a bottleneck. The card opens the tasks of the stage. | Squad Leader, Coordinator, Manager | yes |
| GA-03 | People above capacity | How many people are above capacity in the next 4 weeks. The card opens the tasks in their queue. | Squad Leader, Coordinator, Manager | yes |
| GA-04 | People with slack | How many people have slack in the next 4 weeks. The card opens the tasks in their queue. | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/gargalos.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## Action

### `distribuicao` — Distribution

**Question it answers.** Who should take each task that is waiting for an owner?

**Who sees it.** Squad Leader, Coordinator, Manager.

Suggestions are read-only: the decision is human. Who may change assignee, due date and delay labels is set by the audit rule (see the FAQ).

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| DI-01 | Tasks waiting for an owner | The "to do" tasks with no assignee or assigned to the manager, waiting for someone to take them. The card opens those tasks. | Squad Leader, Coordinator, Manager | yes |
| DI-02 | Suggestions without a decision | The pending tasks for which the dashboard suggests a person and that nobody approved, swapped or rejected today. The card opens those tasks. | Squad Leader, Coordinator, Manager | yes |
| DI-03 | Balance before and after | The Jain index of the load of the eligible people before and after applying every suggestion (1.0 = everyone equal). It is an index of the group, never a score for a person. | Squad Leader, Coordinator, Manager | no |
| DI-04 | Why each person | The score factors of every candidate for a task, in alphabetical order and without a position: workload, WIP, missing skills and experience on the epic. | Squad Leader, Coordinator, Manager | no |

**Where the numbers come from.** Sheet `app/config/cards/distribuicao.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `rebalanceamento` — Rebalancing

**Question it answers.** What can be moved away from whoever is above capacity?

**Who sees it.** Squad Leader, Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| RB-01 | Suggested moves | The tasks not started yet that the dashboard suggests taking from whoever is above the load trigger, each with the suggested destination. The card opens those tasks. | Squad Leader, Coordinator, Manager | yes |
| RB-02 | Moves without a decision | The suggested moves that nobody approved, swapped or rejected today. The card opens those tasks. | Squad Leader, Coordinator, Manager | yes |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## Audit

### `auditoria` — Date, owner & label changes

**Question it answers.** Changes to due dates, assignees and delay labels made by someone who may not have been allowed to, to be reviewed. Coordination and manager only.

**Who sees it.** Coordinator, Manager.

It shows changes to due dates, assignees and delay labels for review. Evidence, not proof: the screen points to what deserves a conversation, never a conclusion about people. It sits behind the DPO gate: with the gate closed the screen is off and nothing is queried, and the field-by-field history opens only with the gate open (see "DPO gate"). Besides the date, owner and label signals, there are two more: estimate reduced after the start (`ESTIMATIVA_REDUZIDA`) and hours logged late (`WORKLOG_RETROATIVO`).

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| AU-01 | Signals to review | How many changes to due dates, assignees and delay labels deserve a check in the period. Evidence, not proof. | Coordinator, Manager | yes |
| AU-02 | Signals by type | The count of signals of each type (due date, assignee change, ball-passing, delay labels, cancellation), in catalog order. There is no count per person. | Coordinator, Manager | yes |
| AU-03 | On time and adjusted on time | Of the completed tasks due in the period, how many were done by the due date (on time) and how many also counting those with a label that justifies the delay (adjusted on time). | Coordinator, Manager | yes |

**Where the numbers come from.** Sheet `app/config/cards/auditoria.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `divergencia` — Task × Review mismatch

**Question it answers.** Tasks and Reviews linked by the Blocks link whose due dates differ. It only shows and links to Jira; it fixes nothing.

**Who sees it.** Coordinator, Manager.

It only shows and links to Jira; it fixes nothing.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| DV-01 | Task × Review mismatch | Task and Review pairs with different due dates. | Coordinator, Manager | yes |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `integridade` — Integrity

**Question it answers.** Are there signs that deadlines or flow were adjusted suspiciously? Each signal is a reason to talk and check in Jira.

**Who sees it.** Manager.

Only the manager sees it. It sits behind the DPO gate: without the DPO's acceptance the screen queries no data at all (it shows "This screen stays off until the data protection officer (DPO) approves its use"). Each signal is evidence, not proof. Every query is logged in the Access log. Per person, the screen calculates points, rate (points per completed task), the multiple of the rate against the team median and an evidence level (high or medium), in alphabetical order and without ranking; the signal catalog (IN-08) includes `ESTIMATIVA_AUMENTADA`. Without the DPO's acceptance it works only with demonstration data.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| IN-01 | Integrity evidence | Hints of possible manipulation of deadlines or flow found in the period, one per signal and task. A hint, not proof. The card opens the tasks. | Manager | yes |
| IN-02 | People with a high or medium hint | How many people are at each hint level and their evidence; the card opens the evidence of those people. Alphabetical, no ranking. A level is a reason to talk, not a grade. | Manager | yes |
| IN-03 | Affected tasks | How many tasks have at least one piece of evidence in the selection. The card opens those tasks. | Manager | yes |
| IN-04 | Team rate median | The median rate of points per completed task in the team; it is the basis of each person's multiple. No tasks behind it. | Manager | no |
| IN-05 | Evidence per signal | How much evidence each of the 12 signals had in the selection, in catalog order. Each signal opens its tasks. | Manager | yes |
| IN-06 | Impact on goals | By how many percentage points the current "on time" is above "on time by the original date" in the same period; a hint that extensions improve the goal. No tasks behind it. | Manager | no |
| IN-07 | Evidence table | Each piece of evidence with the task, the signal, the severity, the person of the signal and the day. The card opens the tasks. | Manager | yes |
| IN-08 | Signal catalog | What each signal looks for, its severity and configured limit, with no data about people. No tasks behind it. | Manager | no |

**Where the numbers come from.** Sheet `app/config/cards/integridade.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


## Governance

### `mapaCartoes` — Card map

**Question it answers.** Every card in the panel that your role sees: what it shows, how it is calculated and where the data comes from.

**Who sees it.** Coordinator, Manager.

It is generated from the backend card catalog (the same list this guide follows): for each card, what it shows, how it is calculated, the data source and who sees it. If this guide and the Map disagree, the Map wins.

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.


### `qualidade` — Data quality

**Question it answers.** What is missing or mismatched in the data coming from Jira?

**Who sees it.** Coordinator, Manager.

It gathers what is missing or inconsistent in the data. Cards that warn about incomplete data lead here.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| QD-01 | Open tasks with missing data | How many open tasks have no estimate, assignee, due date or epic, are in progress without logged hours, have an assignee missing from papeis.yaml or still carry the default priority. Each row opens the tasks. | Coordinator, Manager | yes |
| QD-02 | Mismatched completion | The tasks completed or resolved in the last 90 days where the done status and the resolution disagree. Each row opens the tasks. | Coordinator, Manager | yes |
| QD-03 | Jira vocabulary | Statuses missing from status_map.yaml and recent labels that are neither in labels_atraso.yaml nor in the ignored list. The manager fixes them in the YAML files; the dashboard only reads. | Coordinator, Manager | yes |
| QD-04 | Blocked without a cause | The tasks in a blocked status or with the impediment flag that have no open link explaining it. The card opens those tasks. | Coordinator, Manager | yes |
| QD-05 | Fields without a value | The Jira fields still to be confirmed (Story Points, Epic Link, Flagged, OS Version) where no synchronized issue has a value. It is a list of names, with no tasks. | Coordinator, Manager | no |

**Where the numbers come from.** Sheet `app/config/cards/qualidade.card.yaml` (formula, example and acceptance).

**Drill-down.** Where the "Opens tasks" column says yes, click the number to open the task drawer; it has the same total as the card.


### `decisoes` — Distribution decisions

**Question it answers.** What was decided about the distribution and rebalancing suggestions?

**Who sees it.** Coordinator, Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| DC-01 | Recorded decisions | The decisions to approve, swap or reject suggestions, newest first, with who decided and why. There is no count and no ordering by person. | Coordinator, Manager | no |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.


### `registroAcesso` — Access log

**Question it answers.** Who queried what on the dashboard, and when?

**Who sees it.** Manager.

Only the manager sees it. It lists who looked at what in the dashboard and when; this is the log that makes the sensitive screens auditable.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| RA-01 | Access log | Who queried which dashboard route and when, with the result (200, 400, 403) and only the names of the filters used. It shows no task key, segment or filter value. | Manager | no |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.


### `sincronizacao` — Sync

**Question it answers.** Jira synchronization state. Read-only.

**Who sees it.** Manager.

With the Jira flag off (today's state) the screen shows "Off by flag" and nothing talks to Jira. Periodic sync and the daily reconciliation run only after the user turns the flag on.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| SY-01 | Synchronizer mode | Whether the panel is syncing with Jira or is switched off by the flag. | Manager | no |
| SY-02 | Latest runs | The latest synchronizer runs for each instance, with counts and result. | Manager | no |
| SY-03 | Retention | The last local cleanup of old data. | Manager | no |

**Where the numbers come from.** Sheet `app/config/cards/sincronizacao.card.yaml` (formula, example and acceptance).


### `configuracao` — Configuration

**Question it answers.** Which rules and which mode is the dashboard running with?

**Who sees it.** Manager.

**Cards.**

| Id | Card | What it shows | Who sees it | Opens tasks |
|---|---|---|---|---|
| CF-01 | Configuration | The environment mode, the synchronization mode and, for each rules file, the version, the hash and whether the real file or the template applies. Read-only. | Manager | no |

**Where the numbers come from.** This screen has no sheet of its own; the numbers come from the cards listed above and from the Card map.

