// TypeScript mirrors of the backend DTOs (backend/src/main/java/com/iitsm/backend/api/dto).
// Timestamps arrive as ISO-8601 strings in UTC.

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'ADMINISTRATOR';

export type IncidentStatus =
  | 'NEW'
  | 'CLASSIFIED'
  | 'ASSIGNED'
  | 'ACKNOWLEDGED'
  | 'IN_PROGRESS'
  | 'ON_HOLD'
  | 'RESOLVED'
  | 'VERIFIED'
  | 'CLOSED'
  | 'CANCELLED';

export type Impact = 'LOW' | 'MEDIUM' | 'HIGH' | 'EXTENSIVE';
export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type PriorityLevel = 'P1' | 'P2' | 'P3' | 'P4';
export type SlaStatus = 'NOT_STARTED' | 'ON_TRACK' | 'AT_RISK' | 'PAUSED' | 'BREACHED' | 'MET';
export type BusinessCriticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'MISSION_CRITICAL';
export type ServiceStatus = 'OPERATIONAL' | 'DEGRADED' | 'PARTIAL_OUTAGE' | 'MAJOR_OUTAGE' | 'MAINTENANCE';

export type ResolutionCode =
  | 'PERMANENT_FIX'
  | 'WORKAROUND'
  | 'CONFIGURATION_CHANGE'
  | 'HARDWARE_REPLACED'
  | 'USER_EDUCATION'
  | 'THIRD_PARTY_FIX'
  | 'NOT_REPRODUCIBLE';

export type IncidentOperation =
  | 'CLASSIFY'
  | 'ASSIGN'
  | 'ACKNOWLEDGE'
  | 'START_WORK'
  | 'REQUEST_INFO'
  | 'PROVIDE_INFO'
  | 'RESOLVE'
  | 'ACCEPT_RESOLUTION'
  | 'REJECT_RESOLUTION'
  | 'AUTO_VERIFY'
  | 'CLOSE'
  | 'CANCEL'
  | 'MARK_DUPLICATE'
  | 'TAKE';

export type IncidentEventType =
  | 'CREATED'
  | 'CLASSIFIED'
  | 'PRIORITY_CHANGED'
  | 'DETAILS_UPDATED'
  | 'ASSIGNED'
  | 'REASSIGNED'
  | 'ACKNOWLEDGED'
  | 'WORK_STARTED'
  | 'INFO_REQUESTED'
  | 'INFO_PROVIDED'
  | 'NOTE_ADDED'
  | 'RESOLVED'
  | 'RESOLUTION_ACCEPTED'
  | 'RESOLUTION_REJECTED'
  | 'AUTO_VERIFIED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'SLA_WARNING'
  | 'SLA_BREACHED'
  | 'ESCALATED';

// ----- auth -----

export interface UserView {
  id: number;
  fullName: string;
  email: string;
  role: Role;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserView;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  department?: string;
  location?: string;
}

// ----- catalog -----

export interface ServiceView {
  id: number;
  name: string;
  description: string;
  criticality: BusinessCriticality;
  status: ServiceStatus;
  dependsOn: string[];
}

export interface CategoryView {
  id: number;
  name: string;
  description: string;
  active: boolean;
}

export interface PriorityView {
  level: PriorityLevel;
  label: string;
  /** null for employees: the calculation is internal to support staff. */
  score: number | null;
  rationale: string | null;
}

export interface StrategyView {
  key: string;
  displayName: string;
  description: string;
  active: boolean;
}

export interface StrategySettings {
  priorityCalculators: StrategyView[];
  assignmentStrategies: StrategyView[];
  autoAssign: boolean;
}

// ----- incidents -----

export interface ReportIncidentRequest {
  title: string;
  description: string;
  serviceId: number;
  categoryId?: number | null;
  impact: Impact;
  urgency: Urgency;
  affectedUsers?: number;
  /** "Submit anyway" after the server answered DUPLICATE_INCIDENT. */
  allowDuplicate?: boolean;
}

/** Filters for GET /api/incidents; all optional, combined with AND. */
export interface IncidentSearch {
  q?: string;
  status?: IncidentStatus[];
  priority?: PriorityLevel[];
  sla?: SlaStatus[];
  serviceId?: number;
  categoryId?: number;
  assigneeId?: number;
  unassigned?: boolean;
  /** ISO instants; createdTo is exclusive. */
  createdFrom?: string;
  createdTo?: string;
  /** MINE (default): reported by / assigned to me. ALL: everything I may see. */
  scope?: 'MINE' | 'ALL';
}

export interface IncidentSummary {
  id: number;
  number: string;
  title: string;
  status: IncidentStatus;
  priority: PriorityLevel | null;
  serviceName: string;
  categoryName: string | null;
  reporterName: string;
  assigneeName: string | null;
  slaStatus: SlaStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PersonRef {
  id: number;
  fullName: string;
  role: Role;
}

export interface NamedRef {
  id: number;
  name: string;
}

export interface SlaView {
  status: SlaStatus;
  responseDue: string | null;
  resolutionDue: string | null;
  respondedAt: string | null;
  resolvedAt: string | null;
  responseBreached: boolean;
  resolutionBreached: boolean;
  resolutionElapsedPercent: number;
}

export interface NoteView {
  id: number;
  author: PersonRef;
  text: string;
  internal: boolean;
  createdAt: string;
}

export interface InfoRequestView {
  id: number;
  askedBy: PersonRef;
  question: string;
  askedAt: string;
  answer: string | null;
  answeredAt: string | null;
}

export interface ResolutionView {
  summary: string;
  rootCause: string | null;
  code: ResolutionCode;
  resolvedBy: PersonRef;
  resolvedAt: string;
}

export interface FeedbackView {
  accepted: boolean;
  rating: number | null;
  comment: string | null;
  givenAt: string;
}

export interface IncidentDetail {
  id: number;
  number: string;
  title: string;
  description: string;
  status: IncidentStatus;
  impact: Impact;
  urgency: Urgency;
  affectedUsers: number;
  reporter: PersonRef;
  assignee: PersonRef | null;
  service: NamedRef;
  category: NamedRef | null;
  priority: PriorityView | null;
  sla: SlaView | null;
  notes: NoteView[];
  infoRequests: InfoRequestView[];
  resolution: ResolutionView | null;
  feedback: FeedbackView | null;
  duplicateOfNumber: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  allowedOperations: IncidentOperation[];
}

export interface AuditEntryView {
  id: number;
  incidentNumber: string;
  action: IncidentEventType;
  previousStatus: IncidentStatus | null;
  newStatus: IncidentStatus | null;
  description: string;
  actorName: string | null;
  occurredAt: string;
}

export interface Recommendation {
  technicianId: number;
  fullName: string;
  score: number;
  reasons: string[];
  activeIncidents: number;
  maxActiveIncidents: number;
}

// ----- monitoring -----

export interface NotificationView {
  id: number;
  incidentId: number | null;
  incidentNumber: string | null;
  type: IncidentEventType;
  title: string;
  message: string;
  createdAt: string;
  read: boolean;
}

export interface SlaViolationView {
  id: number;
  incidentId: number;
  incidentNumber: string;
  incidentTitle: string;
  kind: 'RESPONSE' | 'RESOLUTION';
  priority: PriorityLevel;
  serviceName: string;
  deadline: string;
  detectedAt: string;
}

export interface OutboundMessage {
  channel: string;
  to: string;
  subject: string;
  body: string;
  sentAt: string;
}

export interface Outbox {
  emailSimulated: boolean;
  messages: OutboundMessage[];
}

export interface CycleReport {
  ranAt: string;
  checked: number;
  warnings: number;
  breaches: number;
  escalations: number;
  assigned: number;
  autoVerified: number;
  failed: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  fieldErrors: FieldError[];
}

// ----- intelligent features -----

export type Relation = 'LIKELY_DUPLICATE' | 'SIMILAR';

export interface CategorySuggestion {
  categoryId: number;
  name: string;
  /** 0-1 */
  confidence: number;
  reasons: string[];
}

export interface RelatedIncident {
  id: number;
  number: string;
  title: string;
  status: IncidentStatus;
  priority: PriorityLevel | null;
  serviceName: string;
  createdAt: string;
  /** 0-1 */
  score: number;
  relation: Relation;
  reasons: string[];
  resolutionSummary: string | null;
  resolutionCode: ResolutionCode | null;
}

export interface DraftAnalysis {
  categorySuggestions: CategorySuggestion[];
  similarIncidents: RelatedIncident[];
}

export interface Correlation {
  rootServiceId: number;
  rootServiceName: string;
  affectedServices: string[];
  explanation: string;
  incidents: IncidentSummary[];
}

// ----- administration -----

export type SupportLevel = 'L1_TECHNICIAN' | 'L2_TEAM_LEAD' | 'L3_SYSTEM_ADMIN' | 'L4_IT_MANAGER';
export type Availability = 'AVAILABLE' | 'BUSY' | 'OFF_DUTY' | 'ON_LEAVE';

export interface CategoryAdminView {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  keywords: string[];
}

export interface CategoryRequest {
  name: string;
  description?: string | null;
  keywords?: string[];
  active?: boolean;
}

export interface ServiceAdminView {
  id: number;
  name: string;
  description: string | null;
  criticality: BusinessCriticality;
  status: ServiceStatus;
  dependsOn: NamedRef[];
}

export interface ServiceRequest {
  name: string;
  description?: string | null;
  criticality: BusinessCriticality;
  status?: ServiceStatus;
  dependsOnIds?: number[];
}

export interface SlaPolicyView {
  id: number;
  priority: PriorityLevel;
  serviceId: number | null;
  serviceName: string | null;
  responseMinutes: number;
  resolutionMinutes: number;
  warningThreshold: number;
}

export interface SlaPolicyRequest {
  priority: PriorityLevel;
  serviceId: number | null;
  responseMinutes: number;
  resolutionMinutes: number;
  warningThreshold: number;
}

export interface SkillView {
  categoryId: number;
  categoryName: string;
  proficiency: number;
}

export interface UserAdminView {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
  department: string | null;
  location: string | null;
  supportLevel: SupportLevel | null;
  experienceYears: number | null;
  maxActiveIncidents: number | null;
  availability: Availability | null;
  skills: SkillView[];
  activeIncidents: number | null;
}

export interface SkillRequest {
  categoryId: number;
  proficiency: number;
}

export interface CreateUserRequest {
  role: Role;
  fullName: string;
  email: string;
  password: string;
  department?: string;
  location?: string;
  supportLevel?: SupportLevel;
  experienceYears?: number;
  maxActiveIncidents?: number;
  skills?: SkillRequest[];
}

export interface UpdateUserRequest {
  fullName?: string;
  email?: string;
  active?: boolean;
  department?: string;
  location?: string;
}

export interface UpdateTechnicianRequest {
  supportLevel?: SupportLevel;
  experienceYears?: number;
  maxActiveIncidents?: number;
  availability?: Availability;
  skills?: SkillRequest[];
}

// ----- reports -----

export interface Period {
  from: string;
  to: string;
}

export interface Count<K> {
  key: K;
  count: number;
}

export interface OverviewReport {
  period: Period;
  totals: {
    created: number;
    openNow: number;
    resolved: number;
    closed: number;
    cancelled: number;
    slaBreached: number;
    slaCompliancePercent: number | null;
    avgResponseMinutes: number | null;
    avgResolutionMinutes: number | null;
    avgSatisfaction: number | null;
    feedbackCount: number;
  };
  byStatus: Count<IncidentStatus>[];
  byPriority: {
    priority: PriorityLevel;
    count: number;
    open: number;
    breached: number;
    avgResolutionMinutes: number | null;
  }[];
  byCategory: Count<string>[];
  daily: { date: string; created: number; resolved: number }[];
}

export interface TechnicianRow {
  technicianId: number;
  fullName: string;
  supportLevel: SupportLevel;
  availability: Availability;
  active: boolean;
  activeIncidents: number;
  maxActiveIncidents: number;
  utilizationPercent: number;
  assignedInPeriod: number;
  resolvedInPeriod: number;
  avgResolutionMinutes: number | null;
  slaBreaches: number;
  avgSatisfaction: number | null;
}

export interface WorkloadReport {
  period: Period;
  technicians: TechnicianRow[];
}

export interface ServiceRow {
  serviceId: number;
  name: string;
  criticality: BusinessCriticality;
  status: ServiceStatus;
  total: number;
  open: number;
  highPriority: number;
  slaBreaches: number;
  avgResolutionMinutes: number | null;
  topCategory: string | null;
  dependsOn: string[];
}

export interface ServiceReport {
  period: Period;
  services: ServiceRow[];
}
