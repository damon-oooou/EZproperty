import { useEffect, useState } from 'react';
import {
  getConditions,
  updateConditions,
  getReportDetails,
  updateReportDetails,
  downloadReportPdf,
} from '../api/client';

/**
 * Report tab 的全部内容:报告头表单 + 房间状态表 + 一个 Save。
 * 三态语义:satisfactory = true / false / null(未检查),Yes/No 字段同理。
 */

// 二键三态:点亮一边,再点一次回到 null
function TriToggle({ value, onChange, yesLabel = 'Satisfactory', noLabel = 'Not satisfactory' }) {
  return (
    <div className="flex gap-1 shrink-0">
      <button
        type="button"
        onClick={() => onChange(value === true ? null : true)}
        className={`px-2.5 py-1 rounded-md text-xs font-medium border transition-colors
          ${value === true
            ? 'bg-teal-700 border-teal-700 text-white'
            : 'bg-white border-slate-300 text-slate-500 hover:border-teal-600'}`}
      >
        {yesLabel}
      </button>
      <button
        type="button"
        onClick={() => onChange(value === false ? null : false)}
        className={`px-2.5 py-1 rounded-md text-xs font-medium border transition-colors
          ${value === false
            ? 'bg-amber-600 border-amber-600 text-white'
            : 'bg-white border-slate-300 text-slate-500 hover:border-amber-600'}`}
      >
        {noLabel}
      </button>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="block text-xs font-medium text-slate-500 mb-1">{label}</span>
      {children}
    </label>
  );
}

const inputCls =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 ' +
  'placeholder:text-slate-400 focus:outline-none focus:border-teal-600';

function ReportEditor({ inspectionId }) {
  const [details, setDetails] = useState(null);
  const [conditions, setConditions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(false);
  const [downloading, setDownloading] = useState(false);

  // v0.5:PDF 端点需要 Authorization 头,<a href> 直链不再可用,改走 fetch + blob
  async function handleDownloadPdf() {
    setDownloading(true);
    try {
      await downloadReportPdf(inspectionId);
    } catch {
      // 401 已由 client 统一处理跳登录;其余错误静默,按钮恢复即可重试
    } finally {
      setDownloading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [d, c] = await Promise.all([
          getReportDetails(inspectionId),
          getConditions(inspectionId),
        ]);
        if (!cancelled) {
          setDetails(d);
          setConditions(c);
          setDirty(false);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [inspectionId]);

  function setField(name, value) {
    setDetails((prev) => ({ ...prev, [name]: value }));
    setDirty(true);
    setSaveError(false);
  }

  function setCondition(roomId, patch) {
    setConditions((prev) =>
      prev.map((c) => (c.roomId === roomId ? { ...c, ...patch } : c))
    );
    setDirty(true);
    setSaveError(false);
  }

  // v0.5.1:一键全部 Satisfactory。勾选框反映"当前是否全部 satisfactory"这个事实,
  // 勾上 = 全设 true(comments 不动),取消 = 全退回 null(未检查)。
  const allSatisfactory =
    conditions.length > 0 && conditions.every((c) => c.satisfactory === true);

  function handleToggleAll(checked) {
    setConditions((prev) =>
      prev.map((c) => ({ ...c, satisfactory: checked ? true : null }))
    );
    setDirty(true);
    setSaveError(false);
  }

  async function handleSave() {
    if (saving || !dirty) return;
    setSaving(true);
    setSaveError(false);
    try {
      // 请求体按后端 DTO 的形状显式构造,不把 inspectionId 混进去
      const detailsPayload = {
        landlordName: details.landlordName,
        tenantName: details.tenantName,
        leaseExpiry: details.leaseExpiry,
        smokeAlarmsPresent: details.smokeAlarmsPresent,
        smokeAlarmsLocation: details.smokeAlarmsLocation,
        tenantRepairsCarriedOut: details.tenantRepairsCarriedOut,
        urgentAction: details.urgentAction,
        generalComments: details.generalComments,
        tenantActionRequired: details.tenantActionRequired,
        agentName: details.agentName,
        agentTradingAs: details.agentTradingAs,
        disclaimer: details.disclaimer,
      };
      const conditionsPayload = conditions.map((c) => ({
        roomId: c.roomId,
        satisfactory: c.satisfactory,
        comments: c.comments,
      }));

      const [newDetails, newConditions] = await Promise.all([
        updateReportDetails(inspectionId, detailsPayload),
        updateConditions(inspectionId, conditionsPayload),
      ]);
      setDetails(newDetails);
      setConditions(newConditions);
      setDirty(false);
    } catch {
      setSaveError(true);
    } finally {
      setSaving(false);
    }
  }

  if (loading || !details) {
    return <div className="py-16 text-center text-slate-400">Loading...</div>;
  }

  return (
    <div className="max-w-3xl">
      {/* 保存栏 */}
      <div className="mb-6 flex items-center gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Inspection report</h2>
        <div className="ml-auto flex items-center gap-3">
          {saveError && (
            <span className="text-sm text-red-600">Save failed — try again</span>
          )}
          {dirty && !saveError && (
            <span className="text-sm text-amber-600">Unsaved changes</span>
          )}
          <button
            type="button"
            onClick={handleDownloadPdf}
            disabled={downloading}
            className="px-4 py-2 rounded-lg text-sm font-medium border border-slate-300
                       text-slate-700 hover:border-teal-600 hover:text-teal-700 transition-colors
                       disabled:opacity-60"
            title={dirty ? 'Unsaved changes won\u2019t appear in the PDF until you save' : undefined}
          >
            {downloading ? 'Preparing...' : 'Download PDF'}
          </button>
          <button
            onClick={handleSave}
            disabled={!dirty || saving}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors
              ${dirty
                ? 'bg-teal-700 text-white hover:bg-teal-800'
                : 'bg-slate-100 text-slate-400 cursor-default'}`}
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {/* 报告头:当事人与租约 */}
      <div className="rounded-xl bg-white border border-slate-200 p-5 mb-4">
        <h3 className="text-sm font-semibold text-slate-900 mb-4">Tenancy details</h3>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label="Landlord">
            <input className={inputCls} type="text"
              value={details.landlordName ?? ''}
              onChange={(e) => setField('landlordName', e.target.value)} />
          </Field>
          <Field label="Tenant">
            <input className={inputCls} type="text"
              value={details.tenantName ?? ''}
              onChange={(e) => setField('tenantName', e.target.value)} />
          </Field>
          <Field label="Lease expiry">
            <input className={inputCls} type="date"
              value={details.leaseExpiry ?? ''}
              onChange={(e) => setField('leaseExpiry', e.target.value || null)} />
          </Field>
        </div>
      </div>

      {/* 报告头:烟雾报警器与维修 */}
      <div className="rounded-xl bg-white border border-slate-200 p-5 mb-4">
        <h3 className="text-sm font-semibold text-slate-900 mb-4">Smoke alarms & repairs</h3>
        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-700">Smoke alarms present</span>
            <TriToggle yesLabel="Yes" noLabel="No"
              value={details.smokeAlarmsPresent}
              onChange={(v) => setField('smokeAlarmsPresent', v)} />
          </div>
          <Field label="Location of smoke alarms">
            <input className={inputCls} type="text"
              value={details.smokeAlarmsLocation ?? ''}
              onChange={(e) => setField('smokeAlarmsLocation', e.target.value)} />
          </Field>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-700">Has the tenant carried out repairs?</span>
            <TriToggle yesLabel="Yes" noLabel="No"
              value={details.tenantRepairsCarriedOut}
              onChange={(v) => setField('tenantRepairsCarriedOut', v)} />
          </div>
        </div>
      </div>

      {/* 房间状态表:镜像纸质表格 */}
      <div className="rounded-xl bg-white border border-slate-200 p-5 mb-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-slate-900">Room conditions</h3>
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={allSatisfactory}
              onChange={(e) => handleToggleAll(e.target.checked)}
              className="w-4 h-4 accent-teal-700"
            />
            <span className="text-sm text-slate-600">All satisfactory</span>
          </label>
        </div>
        <div className="space-y-2">
          {conditions.map((c) => (
            <div key={c.roomId}
              className="flex flex-wrap sm:flex-nowrap items-center gap-2 py-1.5
                         border-b border-slate-100 last:border-b-0">
              <span className="w-32 shrink-0 text-sm text-slate-900 truncate">{c.roomName}</span>
              <TriToggle
                value={c.satisfactory}
                onChange={(v) => setCondition(c.roomId, { satisfactory: v })} />
              <input
                className="flex-1 min-w-40 rounded-md border border-slate-200 px-2.5 py-1.5
                           text-sm text-slate-900 placeholder:text-slate-300
                           focus:outline-none focus:border-teal-600"
                type="text"
                placeholder="Comments"
                value={c.comments ?? ''}
                onChange={(e) => setCondition(c.roomId, { comments: e.target.value })} />
            </div>
          ))}
        </div>
      </div>

      {/* 三个行动框:每次检查的新发现,inherit 时有意不拷贝 */}
      <div className="rounded-xl bg-white border border-slate-200 p-5 mb-4">
        <h3 className="text-sm font-semibold text-slate-900 mb-4">Actions & comments</h3>
        <div className="space-y-4">
          <Field label="Urgent action / maintenance">
            <textarea className={inputCls} rows={3}
              value={details.urgentAction ?? ''}
              onChange={(e) => setField('urgentAction', e.target.value)} />
          </Field>
          <Field label="General comments / maintenance">
            <textarea className={inputCls} rows={3}
              value={details.generalComments ?? ''}
              onChange={(e) => setField('generalComments', e.target.value)} />
          </Field>
          <Field label="Tenant action required">
            <textarea className={inputCls} rows={3}
              value={details.tenantActionRequired ?? ''}
              onChange={(e) => setField('tenantActionRequired', e.target.value)} />
          </Field>
        </div>
      </div>

      {/* 中介信息与免责声明 */}
      <div className="rounded-xl bg-white border border-slate-200 p-5">
        <h3 className="text-sm font-semibold text-slate-900 mb-4">Agency</h3>
        <div className="grid sm:grid-cols-2 gap-4 mb-4">
          <Field label="Agent">
            <input className={inputCls} type="text"
              value={details.agentName ?? ''}
              onChange={(e) => setField('agentName', e.target.value)} />
          </Field>
          <Field label="Trading as">
            <input className={inputCls} type="text"
              value={details.agentTradingAs ?? ''}
              onChange={(e) => setField('agentTradingAs', e.target.value)} />
          </Field>
        </div>
        <Field label="Agent disclaimer (appears on the report)">
          <textarea className={inputCls} rows={4}
            value={details.disclaimer ?? ''}
            onChange={(e) => setField('disclaimer', e.target.value)} />
        </Field>
      </div>
    </div>
  );
}

export default ReportEditor;