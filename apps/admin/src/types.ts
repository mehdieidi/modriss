export type AdminMe = {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
};

export type Overview = {
  users: number;
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
