import { useEffect, useState, useCallback } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  getProperty,
  getInspectionRooms,
  getInspections,
  getInspectionPhotos,
  uploadInspectionPhotos,
  deleteInspectionPhotos,
  getRoomHistory,
} from '../api/client';
import Layout from '../components/Layout';
import PhotoLightbox from '../components/PhotoLightbox';
import UpdatePhotoDialog from '../components/UpdatePhotoDialog';
import RoomHistoryPanel from '../components/RoomHistoryPanel';
import { photoSrc } from '../utils/photo';

function RoomPage() {
  const { propertyId, inspectionId, roomId } = useParams();
  const [property, setProperty] = useState(null);
  const [room, setRoom] = useState(null);
  const [inspection, setInspection] = useState(null);
  const [photos, setPhotos] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [lightboxIndex, setLightboxIndex] = useState(-1);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [removing, setRemoving] = useState(false);

  // v0.8:Current / History tab 放 URL,刷新不丢(与 InspectionPage 同一做法)
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'history' ? 'history' : 'current';
  function switchTab(next) {
    setSearchParams(next === 'current' ? {} : { tab: next }, { replace: true });
  }

  // v0.8:History 面板数据(切到 History 时加载;照片有变动时重新拉)
  const [history, setHistory] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState(null);

  // v0.8:Add updated photo 对话框
  const [updateTarget, setUpdateTarget] = useState(null);

  useEffect(() => {
    loadContext();
  }, [propertyId, inspectionId, roomId]);

  useEffect(() => {
    loadPhotos();
    setHistory(null);
  }, [inspectionId, roomId]);

  useEffect(() => {
    if (tab === 'history' && !history) loadHistory();
  }, [tab, history, inspectionId, roomId]);

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

  async function loadHistory() {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      setHistory(await getRoomHistory(inspectionId, roomId));
    } catch (err) {
      setHistoryError(err.message || 'Failed to load history');
    } finally {
      setHistoryLoading(false);
    }
  }

  // 照片集合变动后:重拉网格;History 若已加载则失效,下次显示时重拉
  async function refreshAfterChange() {
    await loadPhotos();
    setHistory(null);
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
      await refreshAfterChange();
    } finally {
      setRemoving(false);
    }
  }

  // v0.5.2:前端先挡一道格式(只收 JPEG/PNG,HEIC 明确提示)和大小(单张 15MB),
  // 后端仍按 magic bytes / multipart 上限做最终校验。
  const MAX_FILE_MB = 15;
  const ACCEPTED_TYPES = ['image/jpeg', 'image/png'];

  function validateFiles(files) {
    for (const file of files) {
      if (!ACCEPTED_TYPES.includes(file.type)) {
        const isHeic =
          /heic|heif/i.test(file.type) || /\.(heic|heif)$/i.test(file.name);
        return isHeic
          ? `"${file.name}" is a HEIC file, which isn't supported. Please convert it to JPEG (on iPhone: Settings > Camera > Formats > Most Compatible).`
          : `"${file.name}" isn't a supported format. Only JPEG and PNG are accepted.`;
      }
      if (file.size > MAX_FILE_MB * 1024 * 1024) {
        return `"${file.name}" is ${(file.size / 1024 / 1024).toFixed(1)}MB — each photo must be ${MAX_FILE_MB}MB or smaller.`;
      }
    }
    return null;
  }

  async function handleUpload(e) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploadError(null);

    const error = validateFiles(files);
    if (error) {
      setUploadError(error);
      e.target.value = '';
      return;
    }

    setUploading(true);
    try {
      await uploadInspectionPhotos(inspectionId, roomId, files);
      await refreshAfterChange();
    } catch (err) {
      setUploadError(err.message || 'Upload failed, please try again');
    } finally {
      // 失败也要清空 input,否则重选同一文件不会触发 onChange
      e.target.value = '';
      setUploading(false);
    }
  }

  // v0.8:lightbox 里改了 note,就地更新网格数据,不关闭 lightbox
  function handlePhotoChange(updated) {
    setPhotos((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    setHistory(null); // note 也出现在 History 的 Updated 卡片里
  }

  // v0.8:仅在恰好选中一张时可用
  const singleSelected =
    selectedIds.length === 1 ? photos.find((p) => p.id === selectedIds[0]) : null;

  async function handleUpdated() {
    setUpdateTarget(null);
    setSelectedIds([]);
    await refreshAfterChange();
  }

  const openPhotoById = useCallback(
    (photoId) => {
      const i = photos.findIndex((p) => p.id === photoId);
      if (i >= 0) setLightboxIndex(i);
    },
    [photos]
  );

  const closeLightbox = useCallback(() => setLightboxIndex(-1), []);

  const tabCls = (active) =>
    `px-1 pb-2 text-sm font-medium border-b-2 transition-colors ${
      active
        ? 'border-teal-700 text-teal-800'
        : 'border-transparent text-slate-400 hover:text-slate-600'
    }`;

  const grid = photos.length === 0 ? (
    <div className="border border-dashed border-slate-300 rounded-2xl p-12 text-center text-slate-400">
      No photos for this room in this inspection.
      <br />
      <span className="text-sm">Upload photos to get started.</span>
    </div>
  ) : (
    <div
      className={`grid grid-cols-2 sm:grid-cols-3 gap-3 ${
        tab === 'history' ? 'md:grid-cols-3 lg:grid-cols-4' : 'md:grid-cols-4 lg:grid-cols-5'
      }`}
    >
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
              src={photoSrc(photo.thumbnailUrl)}
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
            {/* v0.8:有 note 的照片角标 */}
            {photo.note && (
              <span
                className="absolute bottom-2 right-2 rounded-md bg-slate-900/70 px-1.5 py-0.5
                           text-[10px] font-medium text-white"
                title={photo.note}
              >
                Note
              </span>
            )}
          </div>
        );
      })}
    </div>
  );

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
      <div className="mb-4 flex items-center gap-3 flex-wrap">
        <h1 className="text-2xl font-semibold text-slate-900">
          {room?.name ?? 'Room'}
        </h1>
        <span className="text-sm text-slate-400">
          {photos.length} photo{photos.length === 1 ? '' : 's'}
        </span>

        <div className="ml-auto flex items-center gap-3">
          {singleSelected && (
            <button
              onClick={() => setUpdateTarget(singleSelected)}
              disabled={removing}
              className="h-10 px-4 rounded-lg border border-teal-700 text-teal-800
                         font-medium hover:bg-teal-50 disabled:opacity-50"
            >
              Add updated photo
            </button>
          )}
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
              accept="image/jpeg,image/png"
              onChange={handleUpload}
              className="hidden"
            />
          </label>
        </div>
      </div>

      {/* v0.8:Current / History tab */}
      <div className="mb-6 flex gap-6 border-b border-slate-200">
        <button onClick={() => switchTab('current')} className={tabCls(tab === 'current')}>
          Current
        </button>
        <button onClick={() => switchTab('history')} className={tabCls(tab === 'history')}>
          History
        </button>
      </div>

      {/* v0.5.2:上传错误提示(格式 / 大小) */}
      {uploadError && (
        <div className="mb-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <span className="flex-1">{uploadError}</span>
          <button
            onClick={() => setUploadError(null)}
            className="shrink-0 font-medium hover:text-red-900"
            title="Dismiss"
          >
            ✕
          </button>
        </div>
      )}

      {/* 照片网格(v0.6:缩略图档)。History tab 时右侧出现面板;窄屏堆到网格下方 */}
      {tab === 'current' ? (
        grid
      ) : (
        <div className="flex flex-col md:flex-row gap-6">
          <div className="min-w-0 flex-1">{grid}</div>
          <aside className="w-full md:w-[272px] md:shrink-0 md:border-l md:border-slate-200 md:pl-6">
            <RoomHistoryPanel
              history={history}
              loading={historyLoading}
              error={historyError}
              onOpenPhoto={openPhotoById}
            />
          </aside>
        </div>
      )}

      {/* Lightbox(v0.6:中间档 + 查看原图入口 + 拍摄/上传日期;v0.8:note 编辑) */}
      {lightboxIndex >= 0 && photos[lightboxIndex] && (
        <PhotoLightbox
          photos={photos}
          index={lightboxIndex}
          onIndexChange={setLightboxIndex}
          onClose={closeLightbox}
          onPhotoChange={handlePhotoChange}
        />
      )}

      {/* v0.8:Add updated photo */}
      {updateTarget && (
        <UpdatePhotoDialog
          inspectionId={inspectionId}
          roomId={roomId}
          oldPhoto={updateTarget}
          onClose={() => setUpdateTarget(null)}
          onDone={handleUpdated}
        />
      )}
    </Layout>
  );
}

export default RoomPage;
