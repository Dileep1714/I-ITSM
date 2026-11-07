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
  | 'CANCEL';

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
  score: number;
  rationale: string;
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
