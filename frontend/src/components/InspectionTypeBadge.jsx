const STYLES = {
  ENTRY: 'bg-teal-50 text-teal-700',
  ROUTINE: 'bg-sky-50 text-sky-700',
  EXIT: 'bg-amber-50 text-amber-700',
};

function InspectionTypeBadge({ type }) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold
                  uppercase tracking-wide ${STYLES[type] ?? 'bg-slate-100 text-slate-600'}`}
    >
      {type}
    </span>
  );
}

export default InspectionTypeBadge;