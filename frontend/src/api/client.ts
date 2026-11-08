import type {
  Availability,
  CategoryAdminView,
  CategoryRequest,
  Correlation,
  CreateUserRequest,
  DraftAnalysis,
  OverviewReport,
  RelatedIncident,
  Role,
  ServiceAdminView,
  ServiceReport,
  ServiceRequest,
  SlaPolicyRequest,
  SlaPolicyView,
  UpdateTechnicianRequest,
  UpdateUserRequest,
  UserAdminView,
  WorkloadReport,
  AuditEntryView,
  CategoryView,
  CycleReport,
  ErrorResponse,
  FieldError,
  Impact,
  IncidentDetail,
  IncidentSearch,
  IncidentSummary,
  NotificationView,
  Outbox,
  PageResponse,
  PriorityView,
  Recommendation,
  RegisterRequest,
  ReportIncidentRequest,
  ResolutionCode,
  ServiceView,
  SlaViolationView,
  StrategySettings,
  TokenResponse,
  Urgency,
  UserView,
} from './types';

const TOKEN_KEY = 'iitsm.token';

export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/** An error returned by the API, carrying the backend's stable error code. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors: FieldError[];

  constructor(body: ErrorResponse) {
    super(body.message);
    this.code = body.code;
    this.status = body.status;
    this.fieldErrors = body.fieldErrors ?? [];
  }
}

/** Called when the server says the token is no longer valid, so the app can return to the login page. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({
      code: 'NETWORK_FAILURE',
      message: 'Cannot reach the server. Check that the backend is running.',
      status: 0,
      path,
      timestamp: new Date().toISOString(),
      fieldErrors: [],
    });
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(
      data ?? {
        code: 'HTTP_' + response.status,
        message: response.statusText || 'Request failed',
        status: response.status,
        path,
        timestamp: new Date().toISOString(),
        fieldErrors: [],
      },
    );
  }
  return data as T;
}

const get = <T>(path: string) => request<T>('GET', path);
const post = <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {});
const put = <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {});
const del = <T>(path: string) => request<T>('DELETE', path);

function query(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}

export const api = {
  auth: {
    login: (email: string, password: string) => post<TokenResponse>('/auth/login', { email, password }),
    register: (req: RegisterRequest) => post<TokenResponse>('/auth/register', req),
    me: () => get<UserView>('/auth/me'),
  },

  catalog: {
    services: () => get<ServiceView[]>('/catalog/services'),
    categories: () => get<CategoryView[]>('/catalog/categories'),
    previewPriority: (req: { impact: Impact; urgency: Urgency; serviceId: number; affectedUsers: number }) =>
      post<PriorityView>('/priority/preview', req),
  },

  incidents: {
    list: (page = 0, size = 20, sort = 'createdAt,desc') =>
      get<PageResponse<IncidentSummary>>(`/incidents${query({ page, size, sort })}`),
    search: (filters: IncidentSearch, page = 0, size = 20, sort = 'createdAt,desc') =>
      get<PageResponse<IncidentSummary>>(
        `/incidents${query({
          ...filters,
          status: filters.status?.join(','),
          priority: filters.priority?.join(','),
          sla: filters.sla?.join(','),
          page,
          size,
          sort,
        })}`,
      ),
    get: (id: number) => get<IncidentDetail>(`/incidents/${id}`),
    history: (id: number) => get<AuditEntryView[]>(`/incidents/${id}/history`),
    report: (req: ReportIncidentRequest) => post<IncidentDetail>('/incidents', req),
    classify: (id: number, categoryId: number) => post<IncidentDetail>(`/incidents/${id}/classify`, { categoryId }),
    recommendations: (id: number) => get<Recommendation[]>(`/incidents/${id}/recommendations`),
    assign: (id: number, technicianId: number) =>
      post<IncidentDetail>(`/incidents/${id}/assign`, { technicianId }),
    acknowledge: (id: number) => post<IncidentDetail>(`/incidents/${id}/acknowledge`),
    start: (id: number) => post<IncidentDetail>(`/incidents/${id}/start`),
    requestInfo: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/request-info`, { text }),
    provideInfo: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/provide-info`, { text }),
    resolve: (id: number, req: { summary: string; rootCause?: string; code: ResolutionCode }) =>
      post<IncidentDetail>(`/incidents/${id}/resolve`, req),
    accept: (id: number, rating: number, comment?: string) =>
      post<IncidentDetail>(`/incidents/${id}/accept`, { rating, comment }),
    reject: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/reject`, { text }),
    close: (id: number) => post<IncidentDetail>(`/incidents/${id}/close`),
    cancel: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/cancel`, { text }),
    addNote: (id: number, text: string, internal: boolean) =>
      post<IncidentDetail>(`/incidents/${id}/notes`, { text, internal }),
    similar: (id: number) => get<RelatedIncident[]>(`/incidents/${id}/similar`),
    markDuplicate: (id: number, originalId: number) =>
      post<IncidentDetail>(`/incidents/${id}/duplicate-of`, { originalId }),
  },

  intelligence: {
    analyze: (req: { title: string; description?: string; serviceId?: number | null }) =>
      post<DraftAnalysis>('/intelligence/analyze', req),
    correlations: () => get<Correlation[]>('/intelligence/correlations'),
  },

  reports: {
    overview: (from?: string, to?: string) => get<OverviewReport>(`/reports/overview${query({ from, to })}`),
    technicians: (from?: string, to?: string) => get<WorkloadReport>(`/reports/technicians${query({ from, to })}`),
    services: (from?: string, to?: string) => get<ServiceReport>(`/reports/services${query({ from, to })}`),
  },

  me: {
    profile: () => get<UserAdminView>('/me/profile'),
    setAvailability: (availability: Availability) => put<UserAdminView>('/me/availability', { availability }),
  },

  notifications: {
    list: (unreadOnly = false, page = 0, size = 20) =>
      get<PageResponse<NotificationView>>(`/notifications${query({ unreadOnly, page, size })}`),
    unreadCount: () => get<{ unread: number }>('/notifications/unread-count'),
    markRead: (id: number) => post<NotificationView>(`/notifications/${id}/read`),
    markAllRead: () => post<{ unread: number }>('/notifications/read-all'),
  },

  admin: {
    audit: (page = 0, size = 50) => get<PageResponse<AuditEntryView>>(`/admin/audit${query({ page, size })}`),
    slaViolations: (page = 0, size = 50) =>
      get<PageResponse<SlaViolationView>>(`/admin/sla-violations${query({ page, size })}`),
    outbox: (limit = 50) => get<Outbox>(`/admin/outbox${query({ limit })}`),
    runSlaMonitor: () => post<CycleReport>('/admin/sla-monitor/run'),
    strategies: () => get<StrategySettings>('/admin/strategies'),
    selectAssignment: (key: string) => put<StrategySettings>('/admin/strategies/assignment', { key }),
    selectPriority: (key: string) => put<StrategySettings>('/admin/strategies/priority', { key }),

    categories: () => get<CategoryAdminView[]>('/admin/categories'),
    createCategory: (req: CategoryRequest) => post<CategoryAdminView>('/admin/categories', req),
    updateCategory: (id: number, req: CategoryRequest) => put<CategoryAdminView>(`/admin/categories/${id}`, req),

    services: () => get<ServiceAdminView[]>('/admin/services'),
    createService: (req: ServiceRequest) => post<ServiceAdminView>('/admin/services', req),
    updateService: (id: number, req: ServiceRequest) => put<ServiceAdminView>(`/admin/services/${id}`, req),

    slaPolicies: () => get<SlaPolicyView[]>('/admin/sla-policies'),
    createSlaPolicy: (req: SlaPolicyRequest) => post<SlaPolicyView>('/admin/sla-policies', req),
    updateSlaPolicy: (id: number, req: SlaPolicyRequest) => put<SlaPolicyView>(`/admin/sla-policies/${id}`, req),
    deleteSlaPolicy: (id: number) => del<void>(`/admin/sla-policies/${id}`),

    users: (role?: Role) => get<UserAdminView[]>(`/admin/users${query({ role })}`),
    createUser: (req: CreateUserRequest) => post<UserAdminView>('/admin/users', req),
    updateUser: (id: number, req: UpdateUserRequest) => put<UserAdminView>(`/admin/users/${id}`, req),
    updateTechnician: (id: number, req: UpdateTechnicianRequest) =>
      put<UserAdminView>(`/admin/users/${id}/technician`, req),
    resetPassword: (id: number, password: string) => post<void>(`/admin/users/${id}/reset-password`, { password }),
  },
};
