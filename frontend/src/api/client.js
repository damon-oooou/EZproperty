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

export async function getPhotos(roomId) {
  const res = await fetch(`${BASE_URL}/rooms/${roomId}/photos`);
  return res.json();
}

export async function uploadPhotos(roomId, files) {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }
  const res = await fetch(`${BASE_URL}/rooms/${roomId}/photos`, {
    method: 'POST',
    body: formData,
  });
  return res.json();
}

export async function deletePhotos(roomId, photoIds) {
  const res = await fetch(`${BASE_URL}/rooms/${roomId}/photos`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ photoIds }),
  });
  return res;
}