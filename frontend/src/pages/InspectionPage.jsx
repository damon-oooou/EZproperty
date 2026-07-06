import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getInspectionRooms, getInspections } from '../api/client';

function InspectionPage() {
  const { propertyId, inspectionId } = useParams();
  const [rooms, setRooms] = useState([]);
  const [inspection, setInspection] = useState(null);

  useEffect(() => {
    loadData();
  }, [propertyId, inspectionId]);

  async function loadData() {
    // v0.3:房间列表改从 inspection 语境获取,每间自带本次 inspection 的照片数
    const roomsData = await getInspectionRooms(inspectionId);
    const inspectionsData = await getInspections(propertyId);
    setRooms(roomsData);
    setInspection(
      inspectionsData.find((i) => i.id === Number(inspectionId)) ?? null
    );
  }

  return (
    <div style={{ padding: '20px' }}>
      <Link to={`/properties/${propertyId}`}>← Back to Inspections</Link>
      <h1>
        {inspection ? `${inspection.type} — ${inspection.inspectionDate}` : 'Inspection'}
      </h1>

      <h2>Rooms</h2>
      <ul>
        {rooms.map((room) => (
          <li key={room.id}>
            <Link
              to={`/properties/${propertyId}/inspections/${inspectionId}/rooms/${room.id}`}
            >
              {room.name} ({room.photoCount})
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default InspectionPage;