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





