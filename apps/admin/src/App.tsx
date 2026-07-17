import {
  Activity,
  Bot,
  Box,
  Database,
  Gauge,
  Globe2,
  KeyRound,
  Layers3,
  LogOut,
  Palette,
  Plus,
  RefreshCw,
  Search,
  Shield,
  ShieldAlert,
  SlidersHorizontal,
  TerminalSquare,
  Trash2,
  Users,
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { api, ApiError, bootstrapAdmin, login } from "./api";
import { CvsEditorView } from "./CvsEditor";
import {
  AdminMe,
  AssistantTurnSummary,
  AuditEvent,
  JobSummary,
  LandingPageVisit,
  ModelSummary,
  Overview,
  ProjectSummary,
  ThemeProfile,
  UserLoginEvent,
  UserSummary,
} from "./types";

type View =
  | "overview"
  | "users"
  | "projects"
  | "models"
  | "jobs"
  | "assistant"
  | "visitors"
  | "visual-syntax"
  | "theme-control"
  | "audit";

type DataState = {
  me: AdminMe | null;
  overview: Overview | null;
  users: UserSummary[];
  projects: ProjectSummary[];
  models: ModelSummary[];
  jobs: JobSummary[];
  assistantTurns: AssistantTurnSummary[];
  userLoginEvents: UserLoginEvent[];
  landingPageVisits: LandingPageVisit[];
  themeProfiles: ThemeProfile[];
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
  userLoginEvents: [],
  landingPageVisits: [],
  themeProfiles: [],
  auditEvents: [],
};

const navItems: Array<{ view: View; label: string; icon: ReactNode }> = [
  { view: "overview", label: "Overview", icon: <Gauge size={18} /> },
  { view: "users", label: "Users", icon: <Users size={18} /> },
  { view: "projects", label: "Projects", icon: <Layers3 size={18} /> },
  { view: "models", label: "Models", icon: <Database size={18} /> },
  { view: "jobs", label: "Jobs", icon: <TerminalSquare size={18} /> },
  { view: "assistant", label: "Assistant", icon: <Bot size={18} /> },
  { view: "visitors", label: "Visitors", icon: <Globe2 size={18} /> },
  { view: "visual-syntax", label: "Visual Syntax", icon: <Palette size={18} /> },
  { view: "theme-control", label: "Theme Control", icon: <SlidersHorizontal size={18} /> },
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
      const [
        me,
        overview,
        users,
        projects,
        models,
        jobs,
        assistantTurns,
        userLoginEvents,
        landingPageVisits,
        themeProfiles,
        auditEvents,
      ] =
        await Promise.all([
          api<AdminMe>("/api/admin/me", token),
          api<Overview>("/api/admin/overview", token),
          api<UserSummary[]>("/api/admin/users", token),
          api<ProjectSummary[]>("/api/admin/projects", token),
          api<ModelSummary[]>("/api/admin/models", token),
          api<JobSummary[]>("/api/admin/jobs", token),
          api<AssistantTurnSummary[]>("/api/admin/assistant/turns", token),
          api<UserLoginEvent[]>("/api/admin/user-login-events", token),
          api<LandingPageVisit[]>("/api/admin/landing-page-visits", token),
          api<ThemeProfile[]>("/api/admin/theme-profiles", token),
          api<AuditEvent[]>("/api/admin/audit-events", token),
        ]);
      setData({
        me,
        overview,
        users,
        projects,
        models,
        jobs,
        assistantTurns,
        userLoginEvents,
        landingPageVisits,
        themeProfiles,
        auditEvents,
      });
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
        {view === "visitors" && (
          <VisitorsView
            loginRows={filterRows(data.userLoginEvents, query)}
            landingRows={filterRows(data.landingPageVisits, query)}
          />
        )}
        {view === "visual-syntax" && (
          <CvsEditorView token={token} canAdmin={canAdmin} query={query} onError={handleError} />
        )}
        {view === "theme-control" && (
          <ThemeControlView
            profiles={filterRows(data.themeProfiles, query)}
            allProfiles={data.themeProfiles}
            token={token}
            canAdmin={canAdmin}
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
    ["Users", overview?.users, <Users size={20} />, `${overview?.guestUsers ?? 0} guests`],
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
          <Attention label="Guest accounts" value={overview?.guestUsers ?? 0} />
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
  async function action(path: string, body: object, method = "POST") {
    try {
      await api<void>(path, props.token, { method, body: JSON.stringify(body) });
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  return (
    <Table
      headers={["User", "Type / quota", "Roles", "Sessions", "Projects", "Status", "Created", "Actions"]}
      rows={props.rows.map((user) => [
        <RecordTitle title={user.displayName} subtitle={user.email} key="user" />,
        user.guest ? `${user.guestPromptsUsed} / ${user.guestPromptLimit} prompts` : "Registered",
        <Pills values={user.adminRoles} empty="none" key="roles" />,
        user.activeSessions,
        user.projectCount,
        <Status value={user.disabled ? "DISABLED" : "ACTIVE"} key="status" />,
        formatDate(user.createdAt),
        <div className="row-actions" key="actions">
          {props.canAdmin && !user.guest && (
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
          {props.canAdmin && user.guest && (
            <button
              className="danger"
              onClick={() =>
                action(
                  `/api/admin/users/${user.id}`,
                  { reason: "admin panel guest deletion" },
                  "DELETE",
                )
              }
            >
              Delete guest
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

function VisitorsView({
  loginRows,
  landingRows,
}: {
  loginRows: UserLoginEvent[];
  landingRows: LandingPageVisit[];
}) {
  return (
    <div className="visitor-admin">
      <section>
        <h2>User Login Events</h2>
        <Table
          headers={["User", "IP", "Country", "OS", "Browser", "Device", "Request", "Occurred"]}
          rows={loginRows.map((event) => [
            <RecordTitle title={event.displayName || event.email} subtitle={event.email} key="user" />,
            event.ipAddress || "-",
            event.country || "-",
            event.os || "-",
            event.browser || "-",
            event.device || "-",
            event.requestId || "-",
            formatDate(event.occurredAt),
          ])}
        />
      </section>

      <section>
        <div className="section-heading-row">
          <h2>Landing Page Visits</h2>
          <span>Retained for 3 days</span>
        </div>
        <Table
          headers={["Visit", "IP", "Country", "OS", "Browser", "Device", "Referrer", "Occurred"]}
          rows={landingRows.map((visit) => [
            <RecordTitle title={visit.path || "/"} subtitle={visit.id} key="visit" />,
            visit.ipAddress || "-",
            visit.country || "-",
            visit.os || "-",
            visit.browser || "-",
            visit.device || "-",
            visit.referrer || "-",
            formatDate(visit.occurredAt),
          ])}
        />
      </section>
    </div>
  );
}

const themeTokenGroups: Array<{ title: string; tokens: Array<[string, string]> }> = [
  {
    title: "Surfaces",
    tokens: [
      ["--bg", "App background"],
      ["--surface", "Base surface"],
      ["--surface-2", "Panel surface"],
      ["--surface-3", "Raised surface"],
      ["--canvas-bg", "Canvas"],
      ["--node-bg", "Node fill"],
      ["--border", "Border"],
      ["--border-subtle", "Subtle border"],
    ],
  },
  {
    title: "Text",
    tokens: [
      ["--text", "Text"],
      ["--text-strong", "Strong text"],
      ["--muted", "Muted text"],
      ["--muted-2", "Secondary muted"],
      ["--text-soft", "Soft text"],
      ["--node-title", "Node title"],
    ],
  },
  {
    title: "Accents",
    tokens: [
      ["--accent", "Accent"],
      ["--accent-dark", "Accent dark"],
      ["--accent-light", "Accent light"],
      ["--accent-glow", "Accent glow"],
      ["--accent-select", "Selection"],
      ["--accent-2", "Secondary accent"],
      ["--success", "Success"],
      ["--warning", "Warning"],
      ["--danger", "Danger"],
    ],
  },
  {
    title: "Chat And Grid",
    tokens: [
      ["--chat-user-bg", "User message"],
      ["--chat-user-text", "User text"],
      ["--chat-assistant-bg", "Assistant message"],
      ["--chat-assistant-border", "Assistant border"],
      ["--grid-line-major", "Major grid"],
      ["--grid-line-minor", "Minor grid"],
      ["--canvas-dot", "Canvas dot"],
    ],
  },
];

function ThemeControlView(props: {
  profiles: ThemeProfile[];
  allProfiles: ThemeProfile[];
  token: string;
  canAdmin: boolean;
  refresh: () => Promise<void>;
  onError: (err: unknown) => void;
}) {
  const firstProfile = props.profiles[0] || props.allProfiles[0] || null;
  const [selectedId, setSelectedId] = useState(firstProfile?.id || "");
  const selected =
    props.allProfiles.find((profile) => profile.id === selectedId) ||
    props.profiles[0] ||
    props.allProfiles[0] ||
    null;
  const [draft, setDraft] = useState<ThemeProfile | null>(selected);
  const [rawTokens, setRawTokens] = useState(() => JSON.stringify(selected?.tokens || {}, null, 2));

  useEffect(() => {
    if (selectedId.startsWith("new:")) {
      return;
    }
    const next =
      props.allProfiles.find((profile) => profile.id === selectedId) ||
      props.allProfiles[0] ||
      null;
    if (!next) {
      setDraft(null);
      setRawTokens("{}");
      return;
    }
    setSelectedId(next.id);
    setDraft(next);
    setRawTokens(JSON.stringify(next.tokens, null, 2));
  }, [props.allProfiles, selectedId]);

  function setToken(name: string, value: string) {
    setDraft((current) => {
      if (!current) {
        return current;
      }
      const tokens = { ...current.tokens, [name]: value };
      setRawTokens(JSON.stringify(tokens, null, 2));
      return { ...current, tokens };
    });
  }

  function applyRawTokens(value: string) {
    setRawTokens(value);
    try {
      const parsed = JSON.parse(value) as Record<string, string>;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return;
      }
      setDraft((current) => (current ? { ...current, tokens: parsed } : current));
    } catch {
      // Keep the user's in-progress JSON until it becomes valid.
    }
  }

  function createProfile() {
    const source = selected || props.allProfiles[0];
    if (!source) {
      return;
    }
    const next = {
      ...source,
      id: `new:${crypto.randomUUID()}`,
      name: `${source.name} Copy`,
      description: source.description,
      builtIn: false,
      active: false,
      activeLight: false,
      activeDark: false,
      tokens: { ...source.tokens },
    };
    setSelectedId(next.id);
    setDraft(next);
    setRawTokens(JSON.stringify(next.tokens, null, 2));
  }

  async function saveDraft() {
    if (!draft) {
      return;
    }
    try {
      const body = JSON.stringify({
        name: draft.name,
        description: draft.description,
        tokens: draft.tokens,
      });
      if (draft.id.startsWith("new:")) {
        const saved = await api<ThemeProfile>("/api/admin/theme-profiles", props.token, {
          method: "POST",
          body,
        });
        setSelectedId(saved.id);
      } else {
        await api<ThemeProfile>(`/api/admin/theme-profiles/${draft.id}`, props.token, {
          method: "POST",
          body,
        });
      }
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  async function activateProfile(id: string, scheme: "light" | "dark") {
    try {
      await api<ThemeProfile>(`/api/admin/theme-profiles/${id}/activate?scheme=${scheme}`, props.token, {
        method: "POST",
      });
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  async function deleteProfile(id: string) {
    try {
      await api<void>(`/api/admin/theme-profiles/${id}/delete`, props.token, { method: "POST" });
      setSelectedId("");
      await props.refresh();
    } catch (err) {
      props.onError(err);
    }
  }

  if (!draft) {
    return (
      <section className="panel empty-cvs">
        <h2>No theme profiles</h2>
        <p>Create a profile after the backend migration has run.</p>
      </section>
    );
  }

  const editable = props.canAdmin && !draft.builtIn;
  const canSave = props.canAdmin && !draft.builtIn;
  const canActivateLight = props.canAdmin && !draft.activeLight && !draft.id.startsWith("new:");
  const canActivateDark = props.canAdmin && !draft.activeDark && !draft.id.startsWith("new:");

  return (
    <div className="theme-admin">
      <section className="panel theme-profile-list">
        <div className="theme-profile-list-head">
          <h2>Profiles</h2>
          {props.canAdmin && (
            <button className="ghost-action" onClick={createProfile}>
              <Plus size={16} />
              <span>New</span>
            </button>
          )}
        </div>
        <div className="theme-profile-cards">
          {props.profiles.map((profile) => (
            <button
              className={profile.id === draft.id ? "theme-profile-card active" : "theme-profile-card"}
              key={profile.id}
              onClick={() => setSelectedId(profile.id)}
              type="button"
            >
              <span className="theme-swatch-row">
                <i style={{ background: profile.tokens["--bg"] }} />
                <i style={{ background: profile.tokens["--surface"] }} />
                <i style={{ background: profile.tokens["--accent"] }} />
                <i style={{ background: profile.tokens["--danger"] }} />
              </span>
              <strong>{profile.name}</strong>
              <small>{themeProfileState(profile)}</small>
            </button>
          ))}
        </div>
      </section>

      <section className="panel theme-editor">
        <div className="theme-editor-head">
          <div>
            <h2>{draft.name}</h2>
            <p>
              {draft.active
                ? `Active for ${[draft.activeLight ? "light" : "", draft.activeDark ? "dark" : ""]
                    .filter(Boolean)
                    .join(" and ")}.`
                : "Edit or activate this profile."}
            </p>
          </div>
          <div className="theme-editor-actions">
            {canActivateLight && (
              <button className="primary-action" onClick={() => activateProfile(draft.id, "light")}>
                <span>Set Light</span>
              </button>
            )}
            {canActivateDark && (
              <button className="primary-action" onClick={() => activateProfile(draft.id, "dark")}>
                <span>Set Dark</span>
              </button>
            )}
            {props.canAdmin && draft.builtIn && (
              <button className="ghost-action" onClick={createProfile}>
                <Plus size={16} />
                <span>Duplicate</span>
              </button>
            )}
            {canSave && (
              <button className="primary-action" onClick={saveDraft}>
                <span>Save</span>
              </button>
            )}
            {props.canAdmin && !draft.builtIn && !draft.active && !draft.id.startsWith("new:") && (
              <button className="danger-action" onClick={() => deleteProfile(draft.id)}>
                <Trash2 size={16} />
                <span>Delete</span>
              </button>
            )}
          </div>
        </div>

        <div className="theme-meta-grid">
          <label className="admin-field">
            <span>Name</span>
            <input
              disabled={!editable}
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </label>
          <label className="admin-field">
            <span>Description</span>
            <input
              disabled={!editable}
              value={draft.description}
              onChange={(event) => setDraft({ ...draft, description: event.target.value })}
            />
          </label>
        </div>

        <div className="theme-token-groups">
          {themeTokenGroups.map((group) => (
            <section className="theme-token-group" key={group.title}>
              <h3>{group.title}</h3>
              <div className="theme-token-grid">
                {group.tokens.map(([tokenName, label]) => (
                  <label className="theme-token-field" key={tokenName}>
                    <span>{label}</span>
                    <div>
                      <i style={{ background: draft.tokens[tokenName] }} />
                      <input
                        disabled={!editable}
                        value={draft.tokens[tokenName] || ""}
                        onChange={(event) => setToken(tokenName, event.target.value)}
                      />
                    </div>
                  </label>
                ))}
              </div>
            </section>
          ))}
        </div>

        <label className="admin-field">
          <span>All Tokens JSON</span>
          <textarea
            className="raw-json theme-json"
            disabled={!editable}
            value={rawTokens}
            onChange={(event) => applyRawTokens(event.target.value)}
          />
        </label>
      </section>
    </div>
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

function themeProfileState(profile: ThemeProfile) {
  if (profile.activeLight && profile.activeDark) {
    return "Light / Dark";
  }
  if (profile.activeLight) {
    return "Light active";
  }
  if (profile.activeDark) {
    return "Dark active";
  }
  return profile.builtIn ? "Built-in" : "Custom";
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
    case "visitors":
      return "Successful user logins and short-retention landing page visits.";
    case "visual-syntax":
      return "Concrete visual syntax documents for CIM, PIM, and PSM.";
    case "theme-control":
      return "Frontend color profiles, active theme selection, and modular CSS tokens.";
    case "audit":
      return "Administrative actions with actor, target, reason, and request IDs.";
  }
}
