import type {
  ChangeRequestDto,
  CreateChangeRequestPayload,
  FileContentDto,
  FileTreeNodeDto,
  HtmlPrototypeBindingDto,
  HtmlPrototypePackageDto,
  HtmlPrototypeUploadResult,
  ProjectDto,
  ProjectTreeDto,
  PrototypeUploadResult,
  SaveHtmlPrototypeBindingPayload,
  SpecGraphDto,
  WorkspaceDto
} from '../types/models';

// Allows Docker/dev overrides while keeping the host-run default ergonomic.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
const API_ORIGIN = API_BASE.replace(/\/api\/?$/, '');

export class ApiClientError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
  }
}

export function apiAssetUrl(path: string) {
  if (!path) return '';
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  return `${API_ORIGIN}${path.startsWith('/') ? path : `/${path}`}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = options.body instanceof FormData
    ? options.headers
    : {
        'Content-Type': 'application/json',
        ...(options.headers ?? {})
      };
  const response = await fetch(`${API_BASE}${path}`, {
    headers,
    ...options
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }));
    throw new ApiClientError(body.message ?? response.statusText, response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  listProjects: () => request<ProjectDto[]>('/projects'),
  createProject: (name: string) => request<ProjectDto>('/projects', {
    method: 'POST',
    body: JSON.stringify({ name })
  }),
  deleteProject: (projectId: number) => request<void>(`/projects/${projectId}`, {
    method: 'DELETE'
  }),
  addWorkspace: (projectId: number, path: string) => request<WorkspaceDto>(`/projects/${projectId}/workspaces`, {
    method: 'POST',
    body: JSON.stringify({ path })
  }),
  removeWorkspace: (projectId: number, workspaceId: number) => request<void>(`/projects/${projectId}/workspaces/${workspaceId}`, {
    method: 'DELETE'
  }),
  renameWorkspace: (projectId: number, workspaceId: number, displayName: string) => request<WorkspaceDto>(`/projects/${projectId}/workspaces/${workspaceId}`, {
    method: 'PATCH',
    body: JSON.stringify({ displayName })
  }),
  importWorkspace: (projectId: number, files: File[], relativePaths: string[], displayName: string) => {
    const formData = new FormData();
    files.forEach((file, index) => {
      formData.append('files', file);
      formData.append('relativePaths', relativePaths[index] || file.name);
    });
    if (displayName) formData.append('displayName', displayName);
    return request<WorkspaceDto>(`/projects/${projectId}/workspace-imports`, {
      method: 'POST',
      body: formData
    });
  },
  getTree: (projectId: number, scan = false) => request<ProjectTreeDto>(`/projects/${projectId}/tree?scan=${scan}`),
  getSpecGraph: (instanceId: number) => request<SpecGraphDto>(`/instances/${instanceId}/spec-graph`),
  getFileTree: (instanceId: number) => request<FileTreeNodeDto>(`/instances/${instanceId}/files`),
  getFileContent: (instanceId: number, path: string) => request<FileContentDto>(`/instances/${instanceId}/files/content?path=${encodeURIComponent(path)}`),
  uploadPrototypeFrames: (instanceId: number, files: File[], overwrite = false) => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    return request<PrototypeUploadResult>(`/instances/${instanceId}/prototype-frames?overwrite=${overwrite}`, {
      method: 'POST',
      body: formData
    });
  },
  getHtmlPrototype: (instanceId: number) => request<HtmlPrototypePackageDto>(`/instances/${instanceId}/prototype-html`),
  uploadHtmlPrototype: (instanceId: number, files: File[], relativePaths: string[], overwrite = false, entryPath = '') => {
    const formData = new FormData();
    files.forEach((file, index) => {
      formData.append('files', file);
      formData.append('relativePaths', relativePaths[index] || file.name);
    });
    if (entryPath) formData.append('entryPath', entryPath);
    return request<HtmlPrototypeUploadResult>(`/instances/${instanceId}/prototype-html?overwrite=${overwrite}`, {
      method: 'POST',
      body: formData
    });
  },
  getHtmlPrototypeBindings: (instanceId: number) => request<HtmlPrototypeBindingDto[]>(`/instances/${instanceId}/prototype-html/bindings`),
  saveHtmlPrototypeBinding: (instanceId: number, payload: SaveHtmlPrototypeBindingPayload) => request<HtmlPrototypeBindingDto>(`/instances/${instanceId}/prototype-html/bindings`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  deleteHtmlPrototypeBinding: (instanceId: number, bindingId: string) => request<void>(`/instances/${instanceId}/prototype-html/bindings/${encodeURIComponent(bindingId)}`, {
    method: 'DELETE'
  }),
  createChangeRequest: (instanceId: number, payload: CreateChangeRequestPayload) => request<ChangeRequestDto>(`/instances/${instanceId}/change-requests`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
};
