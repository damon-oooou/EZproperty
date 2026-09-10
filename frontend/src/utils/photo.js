import { API_ORIGIN } from '../api/client';

// v0.6:后端返回三档 URL。dev 是 /uploads/... 相对路径,需要拼 API origin;
// prod 返回 R2 presigned 绝对 URL,原样使用。
export function photoSrc(url) {
  return url.startsWith('http') ? url : `${API_ORIGIN}${url}`;
}

export function formatDate(isoDateTime) {
  const d = new Date(isoDateTime);
  return d.toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' });
}

// v0.6:照片时间措辞规则 —— takenAt 非空显示 "Taken {日期}"(EXIF 拍摄时间),
// 为空回退 "Uploaded {日期}"。禁止拿上传时间冒充拍摄时间。
// v0.8:History 面板与 lightbox 共用这一处,不另写一套。
export function photoDateLabel(photo) {
  if (photo.takenAt) return `Taken ${formatDate(photo.takenAt)}`;
  if (photo.uploadedAt) return `Uploaded ${formatDate(photo.uploadedAt)}`;
  return '';
}

// ENTRY → "entry",供 "From entry, 19 Mar 2024" 这类句中使用
export function inspectionTypeWord(type) {
  return (type || '').toLowerCase();
}

// v0.8:来源 inspection 的一句话:"From entry, 19 Mar 2024"
export function originLabel(origin) {
  if (!origin) return '';
  return `From ${inspectionTypeWord(origin.type)}, ${formatDate(origin.inspectionDate)}`;
}

export const NOTE_MAX_LENGTH = 500;
