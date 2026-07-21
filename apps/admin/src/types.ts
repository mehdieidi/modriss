export type AdminMe = {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
};

export type Overview = {
  users: number;
  guestUsers: number;
  disabledUsers: number;
  activeSessions: number;
  projects: number;
  models: number;
  artifacts: number;
  activeJobs: number;
  failedJobs: number;
  activeAssistantTurns: number;
  failedAssistantTurns: number;
};

export type UserSummary = {
  id: string;
  email: string;
  displayName: string;
  guest: boolean;
  guestPromptLimit: number;
  guestPromptsUsed: number;
  activeSessions: number;
  projectCount: number;
  adminRoles: string[];
  disabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ProjectSummary = {
  id: string;
  name: string;
  description: string;
  ownerUserId: string;
  ownerEmail: string;
  memberCount: number;
  modelCount: number;
  artifactCount: number;
  jobCount: number;
  createdAt: string;
  updatedAt: string;
};

export type ModelSummary = {
  id: string;
  projectId: string;
  projectName: string;
  level: string;
  name: string;
  revision: number;
  metamodelVersion: string | null;
  migrationState: string | null;
  createdAt: string;
  updatedAt: string;
};

export type JobSummary = {
  id: string;
  projectId: string;
  projectName: string;
  userId: string;
  userEmail: string;
  operation: string;
  status: string;
  progressPercent: number;
  sourceLevel: string;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type AssistantTurnSummary = {
  id: string;
  threadId: string;
  projectId: string;
  projectName: string;
  userId: string;
  userEmail: string;
  level: string;
  modelId: string | null;
  state: string;
  providerCalls: number;
  promptTokens: number;
  completionTokens: number;
  acceptedAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

export type AssistantProviderCallPrompt = {
  id: number;
  provider: string | null;
  model: string | null;
  startedAt: string;
  systemPrompt: string | null;
  userPrompt: string | null;
};

export type AuditEvent = {
  id: string;
  actorId: string | null;
  actorEmail: string | null;
  action: string;
  targetType: string;
  targetId: string;
  reason: string;
  details: string;
  requestId: string | null;
  createdAt: string;
};

export type UserLoginEvent = {
  id: string;
  userId: string;
  email: string;
  displayName: string | null;
  ipAddress: string;
  country: string;
  os: string;
  browser: string;
  device: string;
  userAgent: string;
  requestId: string | null;
  occurredAt: string;
};

export type LandingPageVisit = {
  id: string;
  ipAddress: string;
  country: string;
  os: string;
  browser: string;
  device: string;
  userAgent: string;
  path: string;
  referrer: string;
  requestId: string | null;
  occurredAt: string;
};

export type ThemeProfile = {
  id: string;
  name: string;
  description: string;
  tokens: Record<string, string>;
  builtIn: boolean;
  active: boolean;
  activeLight: boolean;
  activeDark: boolean;
  createdAt: string;
  updatedAt: string;
};
