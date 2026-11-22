import * as SecureStore from 'expo-secure-store';
import { API_URL } from '../lib/config';
import type {
  AttachmentView,
  Availability,
  AuditEntryView,
  CycleReport,
  OverviewReport,
  SlaViolationView,
  WorkloadReport,
  CategoryView,
  Correlation,
  DraftAnalysis,
  ErrorResponse,
  FieldError,
  Impact,
  IncidentDetail,
  IncidentSearch,
  IncidentSummary,
  NotificationView,
  PageResponse,
  PriorityView,
  Recommendation,
  ReportIncidentRequest,
  ResolutionCode,
  ServiceView,
  TokenResponse,
  Urgency,
  UserAdminView,
  UserView,
} from './types';

const TOKEN_KEY = 'iitsm.token';

/**
 * The JWT lives in the device's encrypted keystore (Android Keystore / iOS Keychain), not plain storage. It is also
 * kept in memory so every request can read it synchronously; load() runs once at start-up.
 */
let token: string | null = null;
export const tokenStore = {
  get: () => token,
  load: async () => {
    token = await SecureStore.getItemAsync(TOKEN_KEY);
    return token;
  },
  set: async (value: string) => {
    token = value;
    await SecureStore.setItemAsync(TOKEN_KEY, value);
  },
  clear: async () => {
    token = null;
    await SecureStore.deleteItemAsync(TOKEN_KEY);
  },
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

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

const failure = (code: string, message: string, status: number, path: string): ErrorResponse => ({
  code,
  message,
  status,
  path,
  timestamp: new Date().toISOString(),
  fieldErrors: [],
});

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  // On a phone an unreachable host can leave a request hanging for minutes: give up after 15 s with a clear error.
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch {
    throw new ApiError(
      failure('NETWORK_FAILURE', `Cannot reach the server at ${API_URL}. Check the Wi-Fi and that the server is running.`, 0, path),
    );
  } finally {
    clearTimeout(timeout);
  }

  const text = await response.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = response.ok ? undefined : failure('HTTP_' + response.status, text.slice(0, 200), response.status, path);
  }

  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError((data as ErrorResponse | undefined) ?? failure('HTTP_' + response.status, 'Request failed', response.status, path));
  }
  return data as T;
}

const get = <T>(path: string) => request<T>('GET', path);
const post = <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {});
const put = <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {});

function query(params: Record<string, string | number | boolean | undefined>) {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

/** The subset of the REST API used by the mobile app (employee and technician work; administration stays on the web). */
export const api = {
  auth: {
    login: (email: string, password: string) => post<TokenResponse>('/auth/login', { email, password }),
    me: () => get<UserView>('/auth/me'),
  },

  catalog: {
    services: () => get<ServiceView[]>('/catalog/services'),
    categories: () => get<CategoryView[]>('/catalog/categories'),
    previewPriority: (req: { impact: Impact; urgency: Urgency; serviceId: number; affectedUsers: number }) =>
      post<PriorityView>('/priority/preview', req),
  },

  incidents: {
    list: (page = 0, size = 20) => get<PageResponse<IncidentSummary>>(`/incidents${query({ page, size, sort: 'createdAt,desc' })}`),
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
    assign: (id: number, technicianId: number) => post<IncidentDetail>(`/incidents/${id}/assign`, { technicianId }),
    take: (id: number) => post<IncidentDetail>(`/incidents/${id}/take`),
    acknowledge: (id: number) => post<IncidentDetail>(`/incidents/${id}/acknowledge`),
    start: (id: number) => post<IncidentDetail>(`/incidents/${id}/start`),
    requestInfo: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/request-info`, { text }),
    provideInfo: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/provide-info`, { text }),
    resolve: (id: number, req: { summary: string; rootCause?: string; code: ResolutionCode }) =>
      post<IncidentDetail>(`/incidents/${id}/resolve`, req),
    accept: (id: number, rating: number, comment?: string) => post<IncidentDetail>(`/incidents/${id}/accept`, { rating, comment }),
    reject: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/reject`, { text }),
    close: (id: number) => post<IncidentDetail>(`/incidents/${id}/close`),
    cancel: (id: number, text: string) => post<IncidentDetail>(`/incidents/${id}/cancel`, { text }),
    addNote: (id: number, text: string, internal: boolean) => post<IncidentDetail>(`/incidents/${id}/notes`, { text, internal }),
  },

  intelligence: {
    analyze: (req: { title: string; description?: string; serviceId?: number | null }) =>
      post<DraftAnalysis>('/intelligence/analyze', req),
    correlations: () => get<Correlation[]>('/intelligence/correlations'),
  },

  attachments: {
    list: (incidentId: number) => get<AttachmentView[]>(`/incidents/${incidentId}/attachments`),
    /** Photo from the camera or gallery, sent as multipart/form-data. */
    upload: async (incidentId: number, file: { uri: string; name: string; type: string }) => {
      const form = new FormData();
      // React Native's FormData accepts a { uri, name, type } file descriptor.
      form.append('file', file as unknown as Blob);
      const response = await fetch(`${API_URL}/incidents/${incidentId}/attachments`, {
        method: 'POST',
        headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: form,
      }).catch(() => {
        throw new ApiError(failure('NETWORK_FAILURE', 'Upload failed: cannot reach the server.', 0, '/attachments'));
      });
      const body = await response.json().catch(() => undefined);
      if (!response.ok) throw new ApiError(body ?? failure('HTTP_' + response.status, 'Upload failed', response.status, '/attachments'));
      return body as AttachmentView;
    },
    /** Image source with the auth header, for <Image>. */
    source: (attachmentId: number) => ({
      uri: `${API_URL}/attachments/${attachmentId}/content`,
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    }),
  },

  reports: {
    overview: () => get<OverviewReport>('/reports/overview'),
    technicians: () => get<WorkloadReport>('/reports/technicians'),
  },

  admin: {
    slaViolations: (page = 0, size = 50) => get<PageResponse<SlaViolationView>>(`/admin/sla-violations${query({ page, size })}`),
    audit: (page = 0, size = 50) => get<PageResponse<AuditEntryView>>(`/admin/audit${query({ page, size })}`),
    runSlaMonitor: () => post<CycleReport>('/admin/sla-monitor/run'),
  },

  me: {
    profile: () => get<UserAdminView>('/me/profile'),
    setAvailability: (availability: Availability) => put<UserAdminView>('/me/availability', { availability }),
  },

  notifications: {
    list: (page = 0, size = 30) => get<PageResponse<NotificationView>>(`/notifications${query({ page, size })}`),
    unreadCount: () => get<{ unread: number }>('/notifications/unread-count'),
    markRead: (id: number) => post<NotificationView>(`/notifications/${id}/read`),
    markAllRead: () => post<{ unread: number }>('/notifications/read-all'),
  },
};
