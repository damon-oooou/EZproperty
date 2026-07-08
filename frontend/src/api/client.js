const BASE_URL = 'http://localhost:8080/api';

export async function getProperties() {
  const res = await fetch(`${BASE_URL}/properties`);
  return res.json();
}

export async function getProperty(id) {
  const res = await fetch(`${BASE_URL}/properties/${id}`);
  return res.json();
}

export async function createProperty(address, type) {
  const res = await fetch(`${BASE_URL}/properties`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ address, type }),
  });
  return res.json();
}

export async function getRooms(propertyId) {
  const res = await fetch(`${BASE_URL}/properties/${propertyId}/rooms`);
  return res.json();
}

// v0.4:添加自定义房间(房间是 property 级资产,会出现在该 property 的所有 inspection 中)
export async function createRoom(propertyId, name) {
  const res = await fetch(`${BASE_URL}/properties/${propertyId}/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  return res.json();
}

// ===== Inspections =====

export async function getInspections(propertyId) {
  const res = await fetch(`${BASE_URL}/properties/${propertyId}/inspections`);
  return res.json();
}

export async function createInspection(propertyId, type, inspectionDate, inheritFromPrevious) {
  const res = await fetch(`${BASE_URL}/properties/${propertyId}/inspections`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, inspectionDate, inheritFromPrevious }),
  });
  return res.json();
}

// v0.3:inspection 语境的房间列表,每间带本次 inspection 的照片数
export async function getInspectionRooms(inspectionId) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/rooms`);
  return res.json();
}

export async function getInspectionPhotos(inspectionId, roomId) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/rooms/${roomId}/photos`);
  return res.json();
}

export async function uploadInspectionPhotos(inspectionId, roomId, files) {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/rooms/${roomId}/photos`, {
    method: 'POST',
    body: formData,
  });
  return res.json();
}

export async function deleteInspectionPhotos(inspectionId, photoIds) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/photos`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(photoIds),
  });
  return res;
}

// ===== v0.5: Condition report =====

// 全房间列表 + 已填的 condition,未填的 satisfactory/comments 为 null(服务端合并)
export async function getConditions(inspectionId) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/conditions`);
  return res.json();
}

// updates: [{ roomId, satisfactory, comments }],批量 upsert,返回合并后的最新状态
export async function updateConditions(inspectionId, updates) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/conditions`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  });
  return res.json();
}

// 没填过时返回全 null 的空壳(不是 404),表单可以直接渲染
export async function getReportDetails(inspectionId) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/report-details`);
  return res.json();
}

export async function updateReportDetails(inspectionId, details) {
  const res = await fetch(`${BASE_URL}/inspections/${inspectionId}/report-details`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(details),
  });
  return res.json();
}

// v0.5:PDF 报告下载地址(直接用 <a href> 触发浏览器下载,不走 fetch)
export function getReportPdfUrl(inspectionId) {
  return `${BASE_URL}/inspections/${inspectionId}/report`;
}