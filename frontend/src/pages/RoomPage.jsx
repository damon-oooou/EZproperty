import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  getProperty,
  getInspectionRooms,
  getInspections,
  getInspectionPhotos,
  uploadInspectionPhotos,
  deleteInspectionPhotos,
} from '../api/client';
import Layout from '../components/Layout';

const API_ORIGIN = 'http://localhost:8080';

function RoomPage() {
  const { propertyId, inspectionId, roomId } = useParams();
  const [property, setProperty] = useState(null);
  const [room, setRoom] = useState(null);
  const [inspection, setInspection] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [lightboxIndex, setLightboxIndex] = useState(-1);
  const [uploading, setUploading] = useState(false);
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    loadContext();
  }, [propertyId, inspectionId, roomId]);

  useEffect(() => {
    loadPhotos();
  }, [inspectionId, roomId]);

  async function loadContext() {
    const [propertyData, roomsData, inspectionsData] = await Promise.all([
      getProperty(propertyId),
      getInspectionRooms(inspectionId),
      getInspections(propertyId),
    ]);
    setProperty(propertyData);
    setRoom(roomsData.find((r) => r.id === Number(roomId)) ?? null);
    setInspection(
      inspectionsData.find((i) => i.id === Number(inspectionId)) ?? null
    );
  }

  async function loadPhotos() {
    const data = await getInspectionPhotos(inspectionId, roomId);
    setPhotos(data);
  }

  function toggleSelect(photoId) {
    setSelectedIds((prev) =>
      prev.includes(photoId)
        ? prev.filter((id) => id !== photoId)
        : [...prev, photoId]
    );
  }

  async function handleRemove() {
    if (selectedIds.length === 0 || removing) return;
    setRemoving(true);
    try {
      await deleteInspectionPhotos(inspectionId, selectedIds);
      setSelectedIds([]);
      await loadPhotos();
    } finally {
      setRemoving(false);
    }
  }

  async function handleUpload(e) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      await uploadInspectionPhotos(inspectionId, roomId, files);
      e.target.value = '';
      await loadPhotos();
    } finally {
      setUploading(false);
    }
  }

  // Lightbox 键盘:Esc 关闭,左右键翻页
  const handleLightboxKeys = useCallback(
    (e) => {
      if (e.key === 'Escape') setLightboxIndex(-1);
      else if (e.key === 'ArrowRight')
        setLightboxIndex((i) => Math.min(i + 1, photos.length - 1));
      else if (e.key === 'ArrowLeft')
        setLightboxIndex((i) => Math.max(i - 1, 0));
    },
    [photos.length]
  );

  useEffect(() => {
    if (lightboxIndex < 0) return;
    document.addEventListener('keydown', handleLightboxKeys);
    return () => document.removeEventListener('keydown', handleLightboxKeys);
  }, [lightboxIndex, handleLightboxKeys]);

  const lightboxPhoto = lightboxIndex >= 0 ? photos[lightboxIndex] : null;

  return (
    <Layout
      breadcrumbs={[
        { label: property?.address ?? '...', to: `/properties/${propertyId}` },
        {
          label: inspection
            ? `${inspection.type} — ${inspection.inspectionDate}`
            : 'Inspection',
          to: `/properties/${propertyId}/inspections/${inspectionId}`,
        },
        { label: room?.name ?? 'Room' },
      ]}
    >
      {/* 页头 + 操作区 */}
      <div className="mb-6 flex items-center gap-3 flex-wrap">
        <h1 className="text-2xl font-semibold text-slate-900">
          {room?.name ?? 'Room'}
        </h1>
        <span className="text-sm text-slate-400">
          {photos.length} photo{photos.length === 1 ? '' : 's'}
        </span>

        <div className="ml-auto flex items-center gap-3">
          {selectedIds.length > 0 && (
            <button
              onClick={handleRemove}
              disabled={removing}
              className="h-10 px-4 rounded-lg border border-slate-300 text-slate-700
                         font-medium hover:border-red-400 hover:text-red-600
                         disabled:opacity-50"
            >
              {removing
                ? 'Removing...'
                : `Remove from inspection (${selectedIds.length})`}
            </button>
          )}
          <label
            className={`h-10 px-4 rounded-lg bg-teal-700 text-white font-medium
                        flex items-center cursor-pointer hover:bg-teal-800
                        ${uploading ? 'opacity-50 pointer-events-none' : ''}`}
          >
            {uploading ? 'Uploading...' : 'Upload photos'}
            <input
              type="file"
              multiple
              accept="image/*"
              onChange={handleUpload}
              className="hidden"
            />
          </label>
        </div>
      </div>

      {/* 照片网格 / 空状态 */}
      {photos.length === 0 ? (
        <div className="border border-dashed border-slate-300 rounded-2xl p-12 text-center text-slate-400">
          No photos for this room in this inspection.
          <br />
          <span className="text-sm">Upload photos to get started.</span>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          {photos.map((photo, i) => {
            const selected = selectedIds.includes(photo.id);
            return (
              <div
                key={photo.id}
                className={`relative group rounded-xl overflow-hidden cursor-pointer
                            ${selected ? 'ring-2 ring-teal-600' : ''}`}
                onClick={() => setLightboxIndex(i)}
              >
                <img
                  src={`${API_ORIGIN}${photo.url}`}
                  alt={photo.fileName}
                  className="w-full aspect-square object-cover"
                />
                {/* 选择圆圈:阻止冒泡,不触发 lightbox */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleSelect(photo.id);
                  }}
                  className={`absolute top-2 left-2 w-6 h-6 rounded-full border-2 flex
                              items-center justify-center transition-colors
                              ${selected
                                ? 'bg-teal-600 border-teal-600'
                                : 'bg-slate-900/30 border-white/80 hover:bg-slate-900/50'
                              }`}
                  title={selected ? 'Deselect' : 'Select'}
                >
                  {selected && (
                    <svg className="w-3.5 h-3.5 text-white" fill="none"
                         viewBox="0 0 24 24" stroke="currentColor" strokeWidth="3">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </button>
              </div>
            );
          })}
        </div>
      )}

      {/* Lightbox */}
      {lightboxPhoto && (
        <div
          className="fixed inset-0 bg-slate-900/90 z-30 flex flex-col items-center justify-center px-6"
          onClick={() => setLightboxIndex(-1)}
        >
          <img
            src={`${API_ORIGIN}${lightboxPhoto.url}`}
            alt={lightboxPhoto.fileName}
            className="max-h-[82vh] max-w-[92vw] object-contain rounded-lg"
            onClick={(e) => e.stopPropagation()}
          />
          <div className="mt-4 text-slate-300 text-sm flex items-center gap-4">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((i) => Math.max(i - 1, 0));
              }}
              disabled={lightboxIndex === 0}
              className="px-3 py-1 rounded hover:bg-white/10 disabled:opacity-30"
            >
              ← Prev
            </button>
            <span>
              {lightboxIndex + 1} / {photos.length} · {lightboxPhoto.fileName}
            </span>
            <button
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex((i) => Math.min(i + 1, photos.length - 1));
              }}
              disabled={lightboxIndex === photos.length - 1}
              className="px-3 py-1 rounded hover:bg-white/10 disabled:opacity-30"
            >
              Next →
            </button>
          </div>
        </div>
      )}
    </Layout>
  );
}

export default RoomPage;