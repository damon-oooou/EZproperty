const BASE_URL = 'http://localhost:8080/api';

// ===== v0.5: Auth(token 与用户信息存 localStorage,web/iOS 共用同一套 JWT API)=====

const TOKEN_KEY = 'ez_token';
const USER_KEY = 'ez_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY);
  try {
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function isLoggedIn() {
  return !!getToken();
}

function setAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * 所有受保护请求的统一出口:自动带 Authorization 头;
 * 401(token 过期/非法)时清掉本地凭证并跳登录页。
 */
async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  if (res.status === 401) {
    logout();
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new Error('Session expired');
  }
  return res;
}

async function requestJson(path, options) {
  const res = await request(path, options);
  return res.json();
}

async function authCall(path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new Error(data?.message || 'Something went wrong, please try again');
  }
  const data = await res.json();
  setAuth(data.token, data.user);
  return data.user;
}

export function login(email, password) {
  return authCall('/auth/login', { email, password });
}

export function register({ fullName, email, password, agencyName }) {
  return authCall('/auth/register', { fullName, email, password, agencyName });
}

// v0.5.1:Google Identity Services 回调拿到的 credential 送后端换本站 JWT
export function googleLogin(idToken) {
  return authCall('/auth/google', { idToken });
}

// ===== Properties =====

export async function getProperties() {
  return requestJson('/properties');
}

export async function getProperty(id) {
  return requestJson(`/properties/${id}`);
}

export async function createProperty(address, type) {
  return requestJson('/properties', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ address, type }),
  });
}

export async function getRooms(propertyId) {
  return requestJson(`/properties/${propertyId}/rooms`);
}

// v0.4:添加自定义房间(房间是 property 级资产,会出现在该 property 的所有 inspection 中)
export async function createRoom(propertyId, name) {
  return requestJson(`/properties/${propertyId}/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
}

// ===== Inspections =====

export async function getInspections(propertyId) {
  return requestJson(`/properties/${propertyId}/inspections`);
}

export async function createInspection(propertyId, type, inspectionDate, inheritFromPrevious) {
  return requestJson(`/properties/${propertyId}/inspections`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, inspectionDate, inheritFromPrevious }),
  });
}

// v0.3:inspection 语境的房间列表,每间带本次 inspection 的照片数
export async function getInspectionRooms(inspectionId) {
  return requestJson(`/inspections/${inspectionId}/rooms`);
}

export async function getInspectionPhotos(inspectionId, roomId) {
  return requestJson(`/inspections/${inspectionId}/rooms/${roomId}/photos`);
}

export async function uploadInspectionPhotos(inspectionId, roomId, files) {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }
  // 注意:FormData 不能手动设 Content-Type,浏览器会自动带 boundary
  const res = await request(`/inspections/${inspectionId}/rooms/${roomId}/photos`, {
    method: 'POST',
    body: formData,
  });
  // v0.5.2:格式/大小校验失败时后端返回 4xx + message,这里透传给页面提示
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new Error(data?.message || 'Upload failed, please try again');
  }
  return res.json();
}

export async function deleteInspectionPhotos(inspectionId, photoIds) {
  return request(`/inspections/${inspectionId}/photos`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(photoIds),
  });
}

// ===== Condition report =====

// 全房间列表 + 已填的 condition,未填的 satisfactory/comments 为 null(服务端合并)
export async function getConditions(inspectionId) {
  return requestJson(`/inspections/${inspectionId}/conditions`);
}

// updates: [{ roomId, satisfactory, comments }],批量 upsert,返回合并后的最新状态
export async function updateConditions(inspectionId, updates) {
  return requestJson(`/inspections/${inspectionId}/conditions`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  });
}

// 没填过时返回全 null 的空壳(不是 404),表单可以直接渲染
export async function getReportDetails(inspectionId) {
  return requestJson(`/inspections/${inspectionId}/report-details`);
}

export async function updateReportDetails(inspectionId, details) {
  return requestJson(`/inspections/${inspectionId}/report-details`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(details),
  });
}

/**
 * v0.5:PDF 端点也在认证保护之内,<a href> 带不了 Authorization 头,
 * 改为 fetch 拿 blob 后用临时链接触发下载。
 */
export async function downloadReportPdf(inspectionId) {
  const res = await request(`/inspections/${inspectionId}/report`);
  if (!res.ok) throw new Error('Failed to generate report');

  const disposition = res.headers.get('Content-Disposition') || '';
  const match = disposition.match(/filename="?([^";]+)"?/);
  const fileName = match ? match[1] : `inspection-report-${inspectionId}.pdf`;

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
