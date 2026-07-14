import {
  Activity,
  Bot,
  Box,
  Database,
  Gauge,
  KeyRound,
  Layers3,
  LogOut,
  RefreshCw,
  Search,
  Shield,
  ShieldAlert,
  TerminalSquare,
  Users,
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { api, ApiError, bootstrapAdmin, login } from "./api";
import {
  AdminMe,
  AssistantTurnSummary,
  AuditEvent,
  JobSummary,
  ModelSummary,
  Overview,
  ProjectSummary,
  UserSummary,
} from "./types";

type View = "overview" | "users" | "projects" | "models" | "jobs" | "assistant" | "audit";

type DataState = {
  me: AdminMe | null;
  overview: Overview | null;
  users: UserSummary[];
  projects: ProjectSummary[];
  models: ModelSummary[];
  jobs: JobSummary[];
  assistantTurns: AssistantTurnSummary[];
  auditEvents: AuditEvent[];
};

const emptyData: DataState = {
  me: null,
  overview: null,
  users: [],
  projects: [],
  models: [],
  jobs: [],
  assistantTurns: [],
  auditEvents: [],
};

const navItems: Array<{ view: View; label: string; icon: ReactNode }> = [
  { view: "overview", label: "Overview", icon: <Gauge size={18} /> },
  { view: "users", label: "Users", icon: <Users size={18} /> },
  { view: "projects", label: "Projects", icon: <Layers3 size={18} /> },
  { view: "models", label: "Models", icon: <Database size={18} /> },
  { view: "jobs", label: "Jobs", icon: <TerminalSquare size={18} /> },
  { view: "assistant", label: "Assistant", icon: <Bot size={18} /> },
  { view: "audit", label: "Audit", icon: <Shield size={18} /> },
];

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem("varka_admin_token") || "");
  const [data, setData] = useState<DataState>(emptyData);
  const [view, setView] = useState<View>("overview");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function refresh() {
    if (!token) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [me, overview, users, projects, models, jobs, assistantTurns, auditEvents] =
        await Promise.all([
          api<AdminMe>("/api/admin/me", token),
          api<Overview>("/api/admin/overview", token),
          api<UserSummary[]>("/api/admin/users", token),
          api<ProjectSummary[]>("/api/admin/projects", token),
          api<ModelSummary[]>("/api/admin/models", token),
          api<JobSummary[]>("/api/admin/jobs", token),
          api<AssistantTurnSummary[]>("/api/admin/assistant/turns", token),
          api<AuditEvent[]>("/api/admin/audit-events", token),
        ]);
      setData({ me, overview, users, projects, models, jobs, assistantTurns, auditEvents });
    } catch (err) {
      handleError(err);
      if (err instanceof ApiError && [401, 403].includes(err.status)) {
        logout();
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [token]);

  function saveToken(value: string) {
    setToken(value);
    localStorage.setItem("varka_admin_token", value);
  }

  function logout() {
    localStorage.removeItem("varka_admin_token");
    setToken("");
    setData(emptyData);
  }

  function handleError(err: unknown) {
    setError(err instanceof Error ? err.message : "Unexpected failure");
  }

  if (!token) {
    return <LoginScreen onLogin={saveToken} />;
  }

  const canOperate = data.me?.roles.some((role) => role === "ADMIN" || role === "OPERATOR") ?? false;
  const canAdmin = data.me?.roles.includes("ADMIN") ?? false;

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <Shield size={22} />
          </div>
          <div>
            <strong>Varka Admin</strong>
            <span>Control plane</span>
          </div>
        </div>
        <nav>
          {navItems.map((item) => (
            <button
              className={view === item.view ? "nav-item active" : "nav-item"}
              key={item.view}
              onClick={() => setView(item.view)}
              title={item.label}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="identity">
            <strong>{data.me?.displayName || "Admin"}</strong>
            <span>{data.me?.email}</span>
            <small>{data.me?.roles.join(" / ")}</small>
          </div>
          <button className="icon-button" onClick={logout} title="Sign out">
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>{navItems.find((item) => item.view === view)?.label}</h1>
            <p>{subtitle(view)}</p>
          </div>
          <div className="topbar-actions">
            <label className="search">
              <Search size={17} />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search visible records"
              />
            </label>
            <button className="primary-action" onClick={refresh} disabled={loading}>
              <RefreshCw size={17} />
              <span>{loading ? "Refreshing" : "Refresh"}</span>
            </button>
          </div>
        </header>

        {error && (
          <div className="notice error">
            <ShieldAlert size={18} />
            <span>{error}</span>
          </div>
        )}

        {view === "overview" && <OverviewView data={data} />}
        {view === "users" && (
          <UsersView
            rows={filterRows(data.users, query)}
            token={token}
            canAdmin={canAdmin}
            canOperate={canOperate}
            refresh={refresh}
            onError={handleError}
          />
        )}
        {view === "projects" && <ProjectsView rows={filterRows(data.projects, query)} />}
        {view === "models" && <ModelsView rows={filterRows(data.models, query)} />}
        {view === "jobs" && (
          <JobsView
            rows={filterRows(data.jobs, query)}
            token={token}
            canOperate={canOperate}
            refresh={refresh}
            onError={handleError}
          />
        )}
        {view === "assistant" && (
          <AssistantView
            rows={filterRows(data.assistantTurns, query)}
            token={token}
            canOperate={canOperate}
            refresh={refresh}
            onError={handleError}
          />
        )}
        {view === "audit" && <AuditView rows={filterRows(data.auditEvents, query)} />}
      </section>
    </main>
  );
}

function LoginScreen({ onLogin }: { onLogin: (token: string) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [bootstrapToken, setBootstrapToken] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const result = await login(email, password);
      if (bootstrapToken.trim()) {
        await bootstrapAdmin(result.token, bootstrapToken.trim());
      }
      onLogin(result.token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not sign in");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="brand large">
          <div className="brand-mark">
            <Shield size={25} />
          </div>
          <div>
            <strong>Varka Admin</strong>
            <span>Protected operations workspace</span>
          </div>
        </div>
        <form onSubmit={submit}>
          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" />
          </label>
          <label>
            Password
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              minLength={8}
            />
          </label>
          <label>
            First admin setup token
            <input
              value={bootstrapToken}
              onChange={(event) => setBootstrapToken(event.target.value)}
              type="password"
              autoComplete="one-time-code"
            />
          </label>
          {error && <div className="form-error">{error}</div>}
          <button className="primary-action wide" disabled={loading}>
            <KeyRound size={17} />
            <span>{loading ? "Signing in" : "Enter admin"}</span>
          </button>
        </form>
      </section>
    </main>
  );
}

function OverviewView({ data }: { data: DataState }) {
  const overview = data.overview;
  const cards = [
    ["Users", overview?.users, <Users size={20} />, `${overview?.disabledUsers ?? 0} disabled`],
    ["Active sessions", overview?.activeSessions, <Activity size={20} />, "valid tokens"],
    ["Projects", overview?.projects, <Layers3 size={20} />, `${overview?.models ?? 0} models`],
    ["Artifacts", overview?.artifacts, <Box size={20} />, "generated outputs"],
    ["Active jobs", overview?.activeJobs, <TerminalSquare size={20} />, `${overview?.failedJobs ?? 0} failed`],
    [
      "Assistant turns",
      overview?.activeAssistantTurns,
      <Bot size={20} />,
      `${overview?.failedAssistantTurns ?? 0} failed`,
    ],
  ];

  return (
    <div className="overview-grid">
      {cards.map(([label, value, icon, hint]) => (
        <section className="metric-card" key={label as string}>
          <div className="metric-icon">{icon}</div>
          <span>{label}</span>
          <strong>{String(value ?? "-")}</strong>
          <small>{hint}</small>
        </section>
      ))}
      <section className="panel wide-panel">
        <h2>Operational Attention</h2>
        <div className="attention-grid">
          <Attention label="Failed MDE jobs" value={overview?.failedJobs ?? 0} />
          <Attention label="Failed assistant turns" value={overview?.failedAssistantTurns ?? 0} />
          <Attention label="Disabled accounts" value={overview?.disabledUsers ?? 0} />
          <Attention label="Active sessions" value={overview?.activeSessions ?? 0} />
        </div>
      </section>
    </div>
  );
}

function UsersView(props: {
  rows: UserSummary[];
  token: string;
  canAdmin: boolean;
  canOperate: boolean;
  refresh: () => Promise<void>;
  onError: (err: unknown) => void;
}) {
  async function action(path: string, body: object) {
    try {
      await api<void>(path, props.token, { method: "POST", body: JSON.stringify(body) });
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  return (
    <Table
      headers={["User", "Roles", "Sessions", "Projects", "Status", "Created", "Actions"]}
      rows={props.rows.map((user) => [
        <RecordTitle title={user.displayName} subtitle={user.email} key="user" />,
        <Pills values={user.adminRoles} empty="none" key="roles" />,
        user.activeSessions,
        user.projectCount,
        <Status value={user.disabled ? "DISABLED" : "ACTIVE"} key="status" />,
        formatDate(user.createdAt),
        <div className="row-actions" key="actions">
          {props.canAdmin && (
            <>
              <button onClick={() => action(`/api/admin/users/${user.id}/roles`, { role: "ADMIN", reason: "admin panel grant" })}>
                Admin
              </button>
              <button onClick={() => action(`/api/admin/users/${user.id}/roles`, { role: "OPERATOR", reason: "admin panel grant" })}>
                Operator
              </button>
            </>
          )}
          {props.canOperate && (
            <button onClick={() => action(`/api/admin/users/${user.id}/sessions/revoke`, { reason: "admin panel revocation" })}>
              Revoke sessions
            </button>
          )}
          {props.canAdmin && (
            <button
              className={user.disabled ? "" : "danger"}
              onClick={() =>
                action(`/api/admin/users/${user.id}/${user.disabled ? "enable" : "disable"}`, {
                  reason: "admin panel account control",
                })
              }
            >
              {user.disabled ? "Enable" : "Disable"}
            </button>
          )}
        </div>,
      ])}
    />
  );
}

function ProjectsView({ rows }: { rows: ProjectSummary[] }) {
  return (
    <Table
      headers={["Project", "Owner", "Members", "Models", "Artifacts", "Jobs", "Updated"]}
      rows={rows.map((project) => [
        <RecordTitle title={project.name} subtitle={project.description || project.id} key="project" />,
        project.ownerEmail,
        project.memberCount,
        project.modelCount,
        project.artifactCount,
        project.jobCount,
        formatDate(project.updatedAt),
      ])}
    />
  );
}

function ModelsView({ rows }: { rows: ModelSummary[] }) {
  return (
    <Table
      headers={["Model", "Project", "Level", "Revision", "Metamodel", "Migration", "Updated"]}
      rows={rows.map((model) => [
        <RecordTitle title={model.name} subtitle={model.id} key="model" />,
        model.projectName,
        <Pill value={model.level} key="level" />,
        model.revision,
        model.metamodelVersion || "-",
        model.migrationState || "-",
        formatDate(model.updatedAt),
      ])}
    />
  );
}

function JobsView(props: {
  rows: JobSummary[];
  token: string;
  canOperate: boolean;
  refresh: () => Promise<void>;
  onError: (err: unknown) => void;
}) {
  async function cancel(id: string) {
    try {
      await api<void>(`/api/admin/jobs/${id}/cancel`, props.token, {
        method: "POST",
        body: JSON.stringify({ reason: "admin panel cancellation" }),
      });
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  return (
    <Table
      headers={["Job", "Project", "User", "Operation", "Status", "Progress", "Created", "Actions"]}
      rows={props.rows.map((job) => [
        <RecordTitle title={job.id} subtitle={job.sourceLevel} key="job" />,
        job.projectName,
        job.userEmail,
        job.operation,
        <Status value={job.status} key="status" />,
        `${job.progressPercent}%`,
        formatDate(job.createdAt),
        props.canOperate && ["QUEUED", "RUNNING"].includes(job.status) ? (
          <button className="danger" onClick={() => cancel(job.id)} key="cancel">
            Cancel
          </button>
        ) : (
          "-"
        ),
      ])}
    />
  );
}

function AssistantView(props: {
  rows: AssistantTurnSummary[];
  token: string;
  canOperate: boolean;
  refresh: () => Promise<void>;
  onError: (err: unknown) => void;
}) {
  async function cancel(id: string) {
    try {
      await api<void>(`/api/admin/assistant/turns/${id}/cancel`, props.token, {
        method: "POST",
        body: JSON.stringify({ reason: "admin panel cancellation" }),
      });
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  return (
    <Table
      headers={["Turn", "Project", "User", "State", "Provider calls", "Tokens", "Accepted", "Actions"]}
      rows={props.rows.map((turn) => [
        <RecordTitle title={turn.id} subtitle={turn.modelId || turn.level} key="turn" />,
        turn.projectName,
        turn.userEmail,
        <Status value={turn.state} key="state" />,
        turn.providerCalls,
        turn.promptTokens + turn.completionTokens,
        formatDate(turn.acceptedAt),
        props.canOperate && ["QUEUED", "RUNNING"].includes(turn.state) ? (
          <button className="danger" onClick={() => cancel(turn.id)} key="cancel">
            Cancel
          </button>
        ) : (
          "-"
        ),
      ])}
    />
  );
}

function AuditView({ rows }: { rows: AuditEvent[] }) {
  return (
    <Table
      headers={["Event", "Actor", "Target", "Reason", "Request", "Created"]}
      rows={rows.map((event) => [
        <RecordTitle title={event.action} subtitle={event.id} key="event" />,
        event.actorEmail || "-",
        `${event.targetType} / ${event.targetId}`,
        event.reason || "-",
        event.requestId || "-",
        formatDate(event.createdAt),
      ])}
    />
  );
}

function Table({ headers, rows }: { headers: string[]; rows: Array<Array<ReactNode>> }) {
  return (
    <section className="panel table-panel">
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {headers.map((header) => (
                <th key={header}>{header}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={headers.length} className="empty">
                  No records match the current view.
                </td>
              </tr>
            ) : (
              rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {row.map((cell, cellIndex) => (
                    <td key={cellIndex}>{cell}</td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function RecordTitle({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="record-title">
      <strong>{title}</strong>
      <span>{subtitle}</span>
    </div>
  );
}

function Status({ value }: { value: string }) {
  return <span className={`status status-${value.toLowerCase().replaceAll("_", "-")}`}>{value}</span>;
}

function Pill({ value }: { value: string }) {
  return <span className="pill">{value}</span>;
}

function Pills({ values, empty }: { values: string[]; empty: string }) {
  if (values.length === 0) {
    return <span className="muted">{empty}</span>;
  }
  return (
    <div className="pills">
      {values.map((value) => (
        <Pill value={value} key={value} />
      ))}
    </div>
  );
}

function Attention({ label, value }: { label: string; value: number }) {
  return (
    <div className={value > 0 ? "attention has-value" : "attention"}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function filterRows<T>(rows: T[], query: string): T[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return rows;
  }
  return rows.filter((row) => JSON.stringify(row).toLowerCase().includes(normalized));
}

function formatDate(value: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function subtitle(view: View) {
  switch (view) {
    case "overview":
      return "System health, workload, and operational attention.";
    case "users":
      return "Account status, admin roles, sessions, and ownership footprint.";
    case "projects":
      return "Tenant workspaces, owners, and generated workload.";
    case "models":
      return "Persisted model revisions across CIM, PIM, and PSM.";
    case "jobs":
      return "Transformation and generation execution state.";
    case "assistant":
      return "Durable assistant turns, provider usage, and cancellation control.";
    case "audit":
      return "Administrative actions with actor, target, reason, and request IDs.";
  }
}
