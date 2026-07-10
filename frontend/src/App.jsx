import { Navigate, Routes, Route, useLocation } from 'react-router-dom';
import PropertiesPage from './pages/PropertiesPage';
import PropertyDetailPage from './pages/PropertyDetailPage';
import InspectionPage from './pages/InspectionPage';
import RoomPage from './pages/RoomPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import { isLoggedIn } from './api/client';

/**
 * v0.5:未登录访问受保护页面 → 跳登录页,并把来源路径带过去,
 * 登录成功后原路返回。token 过期的情况由 api/client 的 401 处理兜底。
 */
function RequireAuth({ children }) {
  const location = useLocation();
  if (!isLoggedIn()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route path="/" element={<RequireAuth><PropertiesPage /></RequireAuth>} />
      <Route
        path="/properties/:propertyId"
        element={<RequireAuth><PropertyDetailPage /></RequireAuth>}
      />
      <Route
        path="/properties/:propertyId/inspections/:inspectionId"
        element={<RequireAuth><InspectionPage /></RequireAuth>}
      />
      <Route
        path="/properties/:propertyId/inspections/:inspectionId/rooms/:roomId"
        element={<RequireAuth><RoomPage /></RequireAuth>}
      />
    </Routes>
  );
}

export default App;
