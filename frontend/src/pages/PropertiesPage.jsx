import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { getProperties, createProperty } from '../api/client';

function PropertiesPage() {
  const [properties, setProperties] = useState([]);
  const [address, setAddress] = useState('');
  const [type, setType] = useState('HOUSE');

  useEffect(() => {
    loadProperties();
  }, []);

  async function loadProperties() {
    const data = await getProperties();
    setProperties(data);
  }

  async function handleCreate() {
    if (!address.trim()) return;
    await createProperty(address, type);
    setAddress('');
    loadProperties();
  }

  return (
    <div style={{ padding: '20px' }}>
      <h1>Properties</h1>

      <div style={{ marginBottom: '20px' }}>
        <input
          type="text"
          placeholder="Address"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="HOUSE">House</option>
          <option value="APARTMENT">Apartment</option>
        </select>
        <button onClick={handleCreate}>Create Property</button>
      </div>

      <ul>
        {properties.map((property) => (
          <li key={property.id}>
            <Link to={`/properties/${property.id}`}>
            {property.address} ({property.type})
            </Link>            
          </li>
        ))}
      </ul>
    </div>
  );
}

export default PropertiesPage;