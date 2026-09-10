import { useEffect, useState } from 'react';
import { addUpdatedPhoto } from '../api/client';
import { photoSrc, photoDateLabel, NOTE_MAX_LENGTH } from '../utils/photo';

// 与 RoomPage 的上传校验一致(JPEG/PNG、单张 15MB)
const MAX_FILE_MB = 15;
const ACCEPTED_TYPES = ['image/jpeg', 'image/png'];

function validateFile(file) {
  if (!ACCEPTED_TYPES.includes(file.type)) {
    const isHeic = /heic|heif/i.test(file.type) || /\.(heic|heif)$/i.test(file.name);
    return isHeic
      ? `"${file.name}" is a HEIC file, which isn't supported. Please convert it to JPEG (on iPhone: Settings > Camera > Formats > Most Compatible).`
      : `"${file.name}" isn't a supported format. Only JPEG and PNG are accepted.`;
  }
  if (file.size > MAX_FILE_MB * 1024 * 1024) {
    return `"${file.name}" is ${(file.size / 1024 / 1024).toFixed(1)}MB — each photo must be ${MAX_FILE_MB}MB or smaller.`;
  }
  return null;
}

/**
 * v0.8:"Add updated photo" 对话框。
 * 左侧是被替换的旧照片,右侧选文件 + 必填 note。提交成功后 onDone(newPhoto)。
 */
function UpdatePhotoDialog({ inspectionId, roomId, oldPhoto, onClose, onDone }) {
  const [file, setFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape' && !submitting) onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose, submitting]);

  function handleFileChange(e) {
    const picked = e.target.files?.[0];
    e.target.value = '';
    if (!picked) return;
    const problem = validateFile(picked);
    if (problem) {
      setError(problem);
      setFile(null);
      return;
    }
    setError(null);
    setFile(picked);
  }

  const canSubmit = file && note.trim().length > 0 && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await addUpdatedPhoto(inspectionId, roomId, oldPhoto.id, file, note.trim());
      onDone(created);
    } catch (err) {
      setError(err.message || 'Upload failed, please try again');
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 bg-slate-900/60 z-40 flex items-center justify-center px-4"
      onClick={() => !submitting && onClose()}
    >
      <div
        className="w-full max-w-2xl rounded-2xl bg-white shadow-xl p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-labelledby="update-photo-title"
      >
        <h2 id="update-photo-title" className="text-lg font-semibold text-slate-900">
          Add updated photo
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          The previous photo stays in earlier inspections.
        </p>

        <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 gap-5">
          {/* 左:当前照片 */}
          <div>
            <div className="text-xs font-medium uppercase tracking-wide text-slate-400 mb-2">
              Current photo
            </div>
            <img
              src={photoSrc(oldPhoto.thumbnailUrl)}
              alt={oldPhoto.fileName}
              className="w-full aspect-square object-cover rounded-xl border border-slate-200"
            />
            <div className="mt-2 text-xs text-slate-500">{photoDateLabel(oldPhoto)}</div>
            {oldPhoto.note && (
              <div className="mt-1 text-sm text-slate-700 whitespace-pre-wrap break-words">
                {oldPhoto.note}
              </div>
            )}
          </div>

          {/* 右:新照片 + note */}
          <div>
            <div className="text-xs font-medium uppercase tracking-wide text-slate-400 mb-2">
              Updated photo
            </div>
            <label
              className={`block w-full aspect-square rounded-xl border cursor-pointer overflow-hidden
                          ${previewUrl
                            ? 'border-slate-200'
                            : 'border-dashed border-slate-300 hover:border-teal-600'}`}
            >
              {previewUrl ? (
                <img src={previewUrl} alt={file.name} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex flex-col items-center justify-center text-slate-400 text-sm">
                  <span className="text-2xl mb-1">+</span>
                  Choose a photo
                  <span className="text-xs mt-1">JPEG or PNG, up to {MAX_FILE_MB}MB</span>
                </div>
              )}
              <input
                type="file"
                accept="image/jpeg,image/png"
                onChange={handleFileChange}
                className="hidden"
                disabled={submitting}
              />
            </label>
            {file && (
              <button
                onClick={() => setFile(null)}
                disabled={submitting}
                className="mt-2 text-xs text-slate-500 hover:text-slate-800 underline-offset-2 hover:underline"
              >
                Choose a different photo
              </button>
            )}

            <label className="block mt-3 text-sm font-medium text-slate-700">
              What changed?
              <textarea
                value={note}
                maxLength={NOTE_MAX_LENGTH}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Benchtop chip repaired"
                rows={3}
                disabled={submitting}
                className="mt-1 w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm
                           text-slate-900 placeholder:text-slate-400 focus:outline-none
                           focus:border-teal-600 focus:ring-1 focus:ring-teal-600"
              />
            </label>
            <div className="mt-1 text-xs text-slate-400">
              Required · {NOTE_MAX_LENGTH - note.length} characters left
            </div>
          </div>
        </div>

        {error && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onClose}
            disabled={submitting}
            className="h-10 px-4 rounded-lg border border-slate-300 text-slate-700 font-medium
                       hover:border-slate-400 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={!canSubmit}
            className="h-10 px-4 rounded-lg bg-teal-700 text-white font-medium hover:bg-teal-800
                       disabled:opacity-50 disabled:hover:bg-teal-700"
          >
            {submitting ? 'Uploading...' : 'Add updated photo'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default UpdatePhotoDialog;
