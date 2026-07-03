import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getProperty, getInspections, createInspection } from '../api/client';

function PropertyDetailPage() {
  const { propertyId } = useParams();
  const [property, setProperty] = useState(null);
  const [inspections, setInspections] = useState([]);

  // New Inspection 表单状态
  const [type, setType] = useState('ENTRY');
  const [inspectionDate, setInspectionDate] = useState(
    new Date().toISOString().split('T')[0]   // 今天,格式 yyyy-MM-dd
  );
  const [inherit, setInherit] = useState(false);

  useEffect(() => {
    loadData();
  }, [propertyId]);

  async function loadData() {
    const propertyData = await getProperty(propertyId);
    const inspectionsData = await getInspections(propertyId);
    setProperty(propertyData);
    setInspections(inspectionsData);
  }

  async function handleCreate() {
    await createInspection(propertyId, type, inspectionDate, inherit);
    setInherit(false);
    loadData();
  }

  if (!property) return <div>Loading...</div>;

  return (
    <div style={{ padding: '20px' }}>
      <Link to="/">← Back to Properties</Link>
      <h1>{property.address}</h1>
      <p>{property.type}</p>

      <h2>New Inspection</h2>
      <div style={{ marginBottom: '20px', display: 'flex', gap: '10px', alignItems: 'center' }}>
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="ENTRY">Entry</option>
          <option value="ROUTINE">Routine</option>
          <option value="EXIT">Exit</option>
        </select>
        <input
          type="date"
          value={inspectionDate}
          onChange={(e) => setInspectionDate(e.target.value)}
        />
        <label>
          <input
            type="checkbox"
            checked={inherit}
            onChange={(e) => setInherit(e.target.checked)}
          />
          Inherit photos from previous inspection
        </label>
        <button onClick={handleCreate}>Create</button>
      </div>

      <h2>Inspections</h2>
      <ul>
        {inspections.map((insp) => (
          <li key={insp.id}>
            <Link to={`/properties/${propertyId}/inspections/${insp.id}`}>
              {insp.type} — {insp.inspectionDate}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default PropertyDetailPage;