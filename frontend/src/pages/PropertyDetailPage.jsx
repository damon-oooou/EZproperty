import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getProperty, getRooms } from '../api/client';

function PropertyDetailPage() {
  const { propertyId } = useParams();
  const [property, setProperty] = useState(null);
  const [rooms, setRooms] = useState([]);

  useEffect(() => {
    loadData();
  }, [propertyId]);

  async function loadData() {
    const propertyData = await getProperty(propertyId);
    const roomsData = await getRooms(propertyId);
    setProperty(propertyData);
    setRooms(roomsData);
  }

  if (!property) return <div>Loading...</div>;

  return (
    <div style={{ padding: '20px' }}>
      <Link to="/">← Back to Properties</Link>
      <h1>{property.address}</h1>
      <p>{property.type}</p>

      <h2>Rooms</h2>
      <ul>
        {rooms.map((room) => (
          <li key={room.id}>
            <Link to={`/rooms/${room.id}`}>{room.name}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default PropertyDetailPage;