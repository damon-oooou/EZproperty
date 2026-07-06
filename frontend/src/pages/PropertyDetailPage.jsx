import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getProperty, getInspections, createInspection } from '../api/client';
import Layout from '../components/Layout';
import InspectionTypeBadge from '../components/InspectionTypeBadge';

function PropertyDetailPage() {
  const { propertyId } = useParams();
  const [property, setProperty] = useState(null);
  const [inspections, setInspections] = useState([]);

  const [type, setType] = useState('ENTRY');
  const [inspectionDate, setInspectionDate] = useState(
    new Date().toISOString().split('T')[0]
  );
  const [inherit, setInherit] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    loadData();
  }, [propertyId]);

  async function loadData() {
    const [propertyData, inspectionsData] = await Promise.all([
      getProperty(propertyId),
      getInspections(propertyId),
    ]);
    setProperty(propertyData);
    setInspections(inspectionsData);
  }

  async function handleCreate() {
    if (creating) return;
    setCreating(true);
    try {
      await createInspection(propertyId, type, inspectionDate, inherit);
      setInherit(false);
      await loadData();
    } finally {
      setCreating(false);
    }
  }

  if (!property) {
    return (
      <Layout>
        <div className="py-16 text-center text-slate-400">Loading...</div>
      </Layout>
    );
  }

  const hasPrevious = inspections.length > 0;

  return (
    <Layout breadcrumbs={[{ label: property.address }]}>
      {/* 页头 */}
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-slate-900">{property.address}</h1>
        <p className="text-sm text-slate-500 mt-1 capitalize">
          {property.type.toLowerCase()}
        </p>
      </div>

      {/* 创建表单卡片 */}
      <div className="bg-white border border-slate-200 rounded-2xl p-5 mb-8 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-900 mb-4">New inspection</h2>
        <div className="flex flex-wrap items-center gap-3">
          <select
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="h-10 px-2 rounded-lg border border-slate-300 bg-white
                       focus:outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-600/20"
          >
            <option value="ENTRY">Entry</option>
            <option value="ROUTINE">Routine</option>
            <option value="EXIT">Exit</option>
          </select>

          <input
            type="date"
            value={inspectionDate}
            onChange={(e) => setInspectionDate(e.target.value)}
            className="h-10 px-3 rounded-lg border border-slate-300
                       focus:outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-600/20"
          />

          <label
            className={`flex items-center gap-2 text-sm select-none
                        ${hasPrevious ? 'text-slate-600 cursor-pointer' : 'text-slate-400 cursor-not-allowed'}`}
          >
            <input
              type="checkbox"
              checked={inherit}
              disabled={!hasPrevious}
              onChange={(e) => setInherit(e.target.checked)}
              className="w-4 h-4 accent-teal-700"
            />
            Inherit photos from previous inspection
          </label>

          <button
            onClick={handleCreate}
            disabled={creating}
            className="h-10 px-4 rounded-lg bg-teal-700 text-white font-medium
                       hover:bg-teal-800 disabled:opacity-50 ml-auto"
          >
            {creating ? 'Creating...' : 'Create inspection'}
          </button>
        </div>
        {!hasPrevious && (
          <p className="text-xs text-slate-400 mt-3">
            No previous inspection to inherit from — this will be the first one.
          </p>
        )}
      </div>

      {/* Inspection 列表 */}
      <h2 className="text-sm font-semibold text-slate-900 mb-3">Inspections</h2>
      {inspections.length === 0 ? (
        <div className="border border-dashed border-slate-300 rounded-2xl p-8 text-center text-slate-400">
          No inspections yet. Create the first one above.
        </div>
      ) : (
        <ul className="space-y-2">
          {inspections.map((insp) => (
            <li key={insp.id}>
              <Link
                to={`/properties/${propertyId}/inspections/${insp.id}`}
                className="flex items-center gap-4 bg-white border border-slate-200 rounded-xl
                           px-4 py-3 shadow-sm hover:border-teal-600 transition-colors"
              >
                <InspectionTypeBadge type={insp.type} />
                <span className="text-slate-900 font-medium">{insp.inspectionDate}</span>
                <span className="ml-auto text-slate-300">→</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Layout>
  );
}

export default PropertyDetailPage;