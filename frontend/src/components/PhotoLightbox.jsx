import { useEffect, useState } from 'react';
import { updatePhotoNote } from '../api/client';
import { photoSrc, photoDateLabel, originLabel, NOTE_MAX_LENGTH } from '../utils/photo';

/**
 * v0.8:Lightbox 抽成组件,并加入 note 编辑。
 * 保存后通过 onPhotoChange 把新的 PhotoResponse 交回父组件就地更新,不关闭 lightbox。
 */
function PhotoLightbox({ photos, index, onIndexChange, onClose, onPhotoChange }) {
  const photo = photos[index];
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  // 翻页时退出编辑态,不把上一张的草稿带到下一张
  useEffect(() => {
    setEditing(false);
    setError(null);
  }, [photo?.id]);

  // 键盘:Esc 关闭(编辑态时先退出编辑),左右键翻页(编辑态不翻页,textarea 要用方向键)
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') {
        if (editing) setEditing(false);
        else onClose();
      } else if (!editing && e.key === 'ArrowRight') {
        onIndexChange(Math.min(index + 1, photos.length - 1));
      } else if (!editing && e.key === 'ArrowLeft') {
        onIndexChange(Math.max(index - 1, 0));
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [editing, index, photos.length, onClose, onIndexChange]);

  if (!photo) return null;

  function startEdit() {
    setDraft(photo.note ?? '');
    setError(null);
    setEditing(true);
  }

  async function saveNote() {
    if (saving) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updatePhotoNote(photo.id, draft);
      // 返回的 PhotoResponse 没有 inspection 语境(carriedForward/origin 为 null),只取 note
      onPhotoChange({ ...photo, note: updated.note });
      setEditing(false);
    } catch (err) {
      setError(err.message || 'Failed to save note');
    } finally {
      setSaving(false);
    }
  }

  const stop = (e) => e.stopPropagation();
  const remaining = NOTE_MAX_LENGTH - draft.length;

  return (
    <div
      className="fixed inset-0 bg-slate-900/90 z-30 flex flex-col items-center justify-center px-6"
      onClick={onClose}
    >
      <img
        src={photoSrc(photo.mediumUrl)}
        alt={photo.fileName}
        className="max-h-[66vh] max-w-[92vw] object-contain rounded-lg"
        onClick={stop}
      />

      {/* 日期 + 沿用来源 */}
      <div className="mt-3 text-slate-300 text-sm flex items-center gap-2" onClick={stop}>
        <span>{photoDateLabel(photo)}</span>
        {photo.carriedForward && photo.origin && (
          <span className="text-slate-400">
            · Carried forward · {originLabel(photo.origin)}
          </span>
        )}
        {photo.replacesPhotoId && !photo.carriedForward && (
          <span className="text-teal-300">· Updated this inspection</span>
        )}
      </div>

      {/* v0.8:note 区域 */}
      <div className="mt-2 w-full max-w-xl" onClick={stop}>
        {editing ? (
          <div className="rounded-lg bg-slate-800 p-3">
            <textarea
              autoFocus
              value={draft}
              maxLength={NOTE_MAX_LENGTH}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Chip in benchtop laminate, approx 3cm"
              rows={3}
              className="w-full resize-none rounded-md bg-slate-900 px-3 py-2 text-sm text-slate-100
                         placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
            <div className="mt-2 flex items-center gap-3 text-xs text-slate-400">
              <span className={remaining < 50 ? 'text-amber-300' : ''}>
                {remaining} characters left
              </span>
              {error && <span className="text-red-300">{error}</span>}
              <div className="ml-auto flex gap-2">
                <button
                  onClick={() => setEditing(false)}
                  disabled={saving}
                  className="px-3 py-1 rounded text-slate-300 hover:bg-white/10 disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  onClick={saveNote}
                  disabled={saving}
                  className="px-3 py-1 rounded bg-teal-600 text-white font-medium hover:bg-teal-500
                             disabled:opacity-50"
                >
                  {saving ? 'Saving...' : 'Save note'}
                </button>
              </div>
            </div>
          </div>
        ) : photo.note ? (
          <div className="flex items-start gap-2 rounded-lg bg-slate-800/80 px-3 py-2 text-sm text-slate-100">
            <span className="flex-1 whitespace-pre-wrap break-words">{photo.note}</span>
            <button
              onClick={startEdit}
              className="shrink-0 text-slate-400 hover:text-white"
              title="Edit note"
              aria-label="Edit note"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round"
                      d="M15.232 5.232l3.536 3.536M4 20h4l10.5-10.5a2.5 2.5 0 00-3.536-3.536L4 16v4z" />
              </svg>
            </button>
          </div>
        ) : (
          <div className="text-center">
            <button
              onClick={startEdit}
              className="text-sm text-slate-400 underline-offset-2 hover:text-white hover:underline"
            >
              Add a note
            </button>
          </div>
        )}
      </div>

      {/* 翻页 + 原图 */}
      <div className="mt-3 text-slate-300 text-sm flex items-center gap-4" onClick={stop}>
        <button
          onClick={() => onIndexChange(Math.max(index - 1, 0))}
          disabled={index === 0}
          className="px-3 py-1 rounded hover:bg-white/10 disabled:opacity-30"
        >
          ← Prev
        </button>
        <span>
          {index + 1} / {photos.length} · {photo.fileName}
        </span>
        <a
          href={photoSrc(photo.originalUrl)}
          target="_blank"
          rel="noreferrer"
          className="px-3 py-1 rounded underline hover:bg-white/10"
          title="Open the full-resolution image in a new tab"
        >
          View original ↗
        </a>
        <button
          onClick={() => onIndexChange(Math.min(index + 1, photos.length - 1))}
          disabled={index === photos.length - 1}
          className="px-3 py-1 rounded hover:bg-white/10 disabled:opacity-30"
        >
          Next →
        </button>
      </div>
    </div>
  );
}

export default PhotoLightbox;
