import { Routes, Route } from 'react-router-dom';
import PropertiesPage from './pages/PropertiesPage';
import PropertyDetailPage from './pages/PropertyDetailPage';
import InspectionPage from './pages/InspectionPage';
import RoomPage from './pages/RoomPage';

function App() {
  return (
    <Routes>
      <Route path="/" element={<PropertiesPage />} />
      <Route path="/properties/:propertyId" element={<PropertyDetailPage />} />
      <Route
        path="/properties/:propertyId/inspections/:inspectionId"
        element={<InspectionPage />}
      />
      <Route
        path="/properties/:propertyId/inspections/:inspectionId/rooms/:roomId"
        element={<RoomPage />}
      />
    </Routes>
  );
}

export default App;