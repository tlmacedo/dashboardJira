# Frequently asked questions

Portuguese version: [`perguntas-frequentes.md`](perguntas-frequentes.md). Full guide, screen by screen: [`panel-guide.md`](panel-guide.md) (in Portuguese: [`guia-do-painel.md`](guia-do-painel.md)). The terms below are the ones the English screens show.

> **Today's state (2026-10-29).** Jira access is off (flag `JIRA_LIVE_ENABLED=false`): the dashboard shows only fictional demonstration data. The answers about synchronization and real numbers hold after the user turns the flag on. Audit and Integrity sit behind the DPO gate (closed by default until the data protection officer accepts), and every role has the "My data" screen.

## 1. Why are "On time" and "Adjusted on time" different?

They are two metrics side by side, and both use the same tasks: completed ones (done category **and** a filled-in resolution that is not a cancellation), not an Epic, due in the period. "On time" counts those done by the due date, by the resolution day in Manaus time. "Adjusted on time" also counts those that missed the due date but carry a label that justifies the delay (the list is in `labels_atraso.yaml`). So the difference between the two is the justified delay. See cards MT-01 (Goals & deadlines), FE-05 (Closed) and MP-06 (My dashboard). A label that is under audit still counts in the "adjusted" figure, but shows in a separate counter in Audit.

## 2. What is "Evidence, not proof"?

It is the fixed notice on the Audit and Integrity screens. A signal shows a change or a pattern that deserves a human check (for example, a due date changed by someone who was not authorized). It does not say anyone did something wrong: there may be a legitimate reason. The path is to talk and check the task, never to punish because of a number. That is why queries to these modules are logged and Integrity is for the manager only.

## 3. What does "no capacity" mean?

When a person's net capacity in a week is zero (vacation, holiday, a registered absence), dividing demand by capacity makes no sense. The dashboard then shows "no capacity" and the planned hours instead of a percentage. It appears in the Load (CP-03), Capacity (CA-05) and 4–8 week plan (PL-01) cards. If someone shows "no capacity" and should not, check `ausencias.yaml` and the holiday calendar.

## 4. Why does the dashboard count business days?

Because working time does not run on weekends or holidays. The default is **Business days**, using the versioned holiday calendar and the America/Manaus time zone; the filters offer **Calendar days** when you want clock time. The unit you choose applies to the time, age and cycle cards on that screen.

## 5. Why can't I see someone else's task?

Because each role has its own view. The **User** sees only their own; the **Squad Leader** sees their own and their squad; **Coordinator** and **Manager** see everyone. Card totals are already calculated only over what you may see. On the Task ownership screen, a person outside your view shows as "someone else" (the Portuguese interface says "outra pessoa"), with no name. This applies in production, with login: in the demonstration (development mode) the server shows all data, and the "Role (simulation)" selector only changes the menu. If you need a wider view, the role is set in `papeis.yaml`: talk to the maintenance owner.

## 6. Why did a number change from yesterday to today?

Three common reasons: (1) the synchronization with Jira runs every 10 minutes (default) and the daily reconciliation at 03:00 fixes what slipped through; each card shows "updated ... ago" with the time of the last synchronization; (2) the tasks really changed in Jira (status, assignee, due date, resolution); (3) a rolling period moved by one day (for example, "last 90 days"). Today, with the Jira flag off, nothing synchronizes and the demonstration data only changes when the database is recreated.

## 7. Why is there no ranking of people?

It is a product decision. A ranking turns evidence into judgment, rewards the number instead of the work and ignores that load, task type and absences differ. People tables are in alphabetical order, with no position or score, and the indicators about people are capacity and load, not performance.

## 8. What should I do when a number looks wrong?

1. Open the number: the task drawer shows the tasks behind it, with the same total as the card.
2. Read the card's ⓘ ("What it shows", "How it is calculated", "Source") or the Card map; the rule may explain the surprise (for example, only completed tasks with a resolution count).
3. Check Data quality: missing or inconsistent data shows up there.
4. If it still looks wrong, write it down: the card, the filters (the URL) and what you expected, and send it to the coordinator or to the maintenance owner. The divergence log that goes with the validation against real Jira (Plan 14) **does not exist yet**; until then, the report goes this way and joins the list of pending items.

## 9. Who may change assignee, due date and labels?

Only the task Reporter (the PL), the coordinators and the manager, or the Squad Leader of the assignee may change the **assignee**, the **due date** and the **labels that prevent delay**. The lists of coordinators and Squad Leaders come from YAML with validity dates, and the check uses the roles valid on the date of each change. Anyone else who changes these fields raises an alert or an audit entry. The dashboard only reads: Jira is where changes happen, and Audit just shows what changed. Remember: "Evidence, not proof".

## 10. How do I see, correct or ask for deletion of my data?

Open **My data** in the menu (every role has the screen). It shows only what the dashboard stores about you: counts of tasks, changes and logged hours, planned absences (without the reason), roles, the categories processed with purpose and retention period, and your accesses of the last 90 days; "Download my data (JSON)" generates the same content. The screen does not show the content of Audit or Integrity signals about you: anyone who wants that survey asks the DPO. To **correct**, fix the task in Jira; the dashboard reflects it on the next sync. To **delete**, the request goes through the DPO channel (the next sync may bring back whatever still exists in Jira). A former member with no role any more gets "no permission" on the screen and also uses the DPO channel. See [`docs/lgpd/atendimento-ao-titular.md`](../lgpd/atendimento-ao-titular.md) (Portuguese). Retention is 24 months by default, per category (to be confirmed with the DPO).
