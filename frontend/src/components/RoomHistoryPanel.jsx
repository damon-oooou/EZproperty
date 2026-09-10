import { useState } from 'react';
import { photoSrc, photoDateLabel, originLabel, formatDate, inspectionTypeWord } from '../utils/photo';

const MAX_ORIGIN_ROWS = 3;

function SectionTitle({ children, count }) {
  return (
    <div className="text-sm font-semibold text-slate-900">
      {children} <span className="font-normal text-slate-400">· {count}</span>
    </div>
  );
}

function Thumb({ photo, onClick, className = '' }) {
  return (
    <img
      src={photoSrc(photo.thumbnailUrl)}
      alt={photo.fileName}
      onClick={onClick}
      className={`object-cover rounded-lg border border-slate-200 ${onClick ? 'cursor-pointer hover:border-teal-600' : ''} ${className}`}
    />
  );
}

/**
 * v0.8:Room 页 History 面板。三组:Updated this inspection / Newly captured / Carried forward。
 * 任何一组为空则整组不渲染;三组全空显示空状态。
 * Carried forward 默认折叠成按来源分行的摘要,展开后才平铺缩略图。
 *
 * onOpenPhoto(photoId):点击当前 inspection 里的照片时在网格 lightbox 中打开(旧照片不在当前
 * inspection 里,不可打开)。
 */
function RoomHistoryPanel({ history, loading, error, onOpenPhoto }) {
  const [carriedExpanded, setCarriedExpanded] = useState(false);

  if (loading && !history) {
    return <div className="text-sm text-slate-400">Loading history...</div>;
  }
  if (error) {
    return <div className="text-sm text-red-600">{error}</div>;
  }
  if (!history) return null;

  const { updated, newlyCaptured, carriedForward } = history;
  const isEmpty =
    updated.length === 0 && newlyCaptured.length === 0 && carriedForward.count === 0;

  if (isEmpty) {
    return (
      <div className="text-sm text-slate-400">No changes recorded for this room yet.</div>
    );
  }

  const origins = carriedForward.origins;
  const visibleOrigins = origins.slice(0, MAX_ORIGIN_ROWS);
  const hiddenOriginPhotos = origins
    .slice(MAX_ORIGIN_ROWS)
    .reduce((sum, o) => sum + o.photoCount, 0);

  return (
    <div className="space-y-6">
      {updated.length > 0 && (
        <section>
          <SectionTitle count={updated.length}>Updated this inspection</SectionTitle>
          <div className="mt-2 space-y-3">
            {updated.map(({ oldPhoto, newPhoto, note }) => (
              <div key={newPhoto.id} className="rounded-xl border border-slate-200 bg-white p-3">
                <div className="flex items-center gap-2">
                  <Thumb photo={oldPhoto} className="w-16 h-16 opacity-70" />
                  <span className="text-slate-400">→</span>
                  <Thumb photo={newPhoto} className="w-16 h-16" onClick={() => onOpenPhoto(newPhoto.id)} />
                </div>
                {note && (
                  <div className="mt-2 text-sm text-slate-800 whitespace-pre-wrap break-words">
                    {note}
                  </div>
                )}
                <div className="mt-1 text-xs text-slate-400">
                  Replaced photo · {photoDateLabel(oldPhoto)}
                  {oldPhoto.origin ? ` · ${originLabel(oldPhoto.origin)}` : ''}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {newlyCaptured.length > 0 && (
        <section>
          <SectionTitle count={newlyCaptured.length}>Newly captured</SectionTitle>
          <div className="mt-2 flex flex-wrap gap-2">
            {newlyCaptured.map((p) => (
              <Thumb key={p.id} photo={p} className="w-14 h-14" onClick={() => onOpenPhoto(p.id)} />
            ))}
          </div>
        </section>
      )}

      {carriedForward.count > 0 && (
        <section>
          <SectionTitle count={carriedForward.count}>Carried forward</SectionTitle>

          {/* 折叠态:按来源 inspection 分行(最多 3 行,其余合并) */}
          <div className="mt-2 space-y-1 text-sm text-slate-600">
            {visibleOrigins.map((o) => (
              <div key={o.inspectionId}>
                From {inspectionTypeWord(o.type)}, {formatDate(o.inspectionDate)}
                <span className="text-slate-400"> · {o.photoCount} photo{o.photoCount === 1 ? '' : 's'}</span>
              </div>
            ))}
            {hiddenOriginPhotos > 0 && (
              <div className="text-slate-400">+{hiddenOriginPhotos} earlier</div>
            )}
          </div>

          {carriedExpanded ? (
            <div className="mt-3 grid grid-cols-3 gap-2">
              {carriedForward.photos.map((p) => (
                <div key={p.id}>
                  <Thumb photo={p} className="w-full aspect-square" onClick={() => onOpenPhoto(p.id)} />
                  <div className="mt-1 text-[11px] leading-tight text-slate-400">
                    {originLabel(p.origin)}
                    <br />
                    {photoDateLabel(p)}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <button
              onClick={() => setCarriedExpanded(true)}
              className="mt-2 text-sm font-medium text-teal-700 hover:text-teal-900"
            >
              Show all {carriedForward.count}
            </button>
          )}
          {carriedExpanded && (
            <button
              onClick={() => setCarriedExpanded(false)}
              className="mt-2 text-sm font-medium text-teal-700 hover:text-teal-900"
            >
              Collapse
            </button>
          )}
        </section>
      )}
    </div>
  );
}

export default RoomHistoryPanel;
