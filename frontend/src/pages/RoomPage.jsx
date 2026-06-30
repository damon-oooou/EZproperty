import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getPhotos, uploadPhotos, deletePhotos } from '../api/client';

function RoomPage() {
  const { roomId } = useParams();
  const [photos, setPhotos] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);

  useEffect(() => {
    loadPhotos();
  }, [roomId]);

  async function loadPhotos() {
    const data = await getPhotos(roomId);
    setPhotos(data);
  }

  function toggleSelect(photoId) {
    setSelectedIds((prev) =>
      prev.includes(photoId)
        ? prev.filter((id) => id !== photoId)
        : [...prev, photoId]
    );
  }

  async function handleDelete() {
    if (selectedIds.length === 0) return;
    await deletePhotos(roomId, selectedIds);
    setSelectedIds([]);
    loadPhotos();
  }

  async function handleUpload(e) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    await uploadPhotos(roomId, files);
    loadPhotos();
  }

  return (
    <div style={{ padding: '20px' }}>
      <Link to="/">← Back to Properties</Link>

      <h1>Room Photos</h1>

      <div style={{ marginBottom: '20px' }}>
        <input type="file" multiple accept="image/*" onChange={handleUpload} />
        <button onClick={handleDelete} disabled={selectedIds.length === 0}>
          Delete Selected ({selectedIds.length})
        </button>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
        {photos.map((photo) => (
          <div key={photo.id} style={{ border: '1px solid #ccc', padding: '5px' }}>
            <img
              src={`http://localhost:8080/uploads/${roomId}/${photo.filePath.split('\\').pop()}`}
              alt={photo.fileName}
              style={{ width: '150px', height: '150px', objectFit: 'cover' }}
            />
            <div>
              <input
                type="checkbox"
                checked={selectedIds.includes(photo.id)}
                onChange={() => toggleSelect(photo.id)}
              />
              <span style={{ fontSize: '12px' }}>{photo.fileName}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default RoomPage;