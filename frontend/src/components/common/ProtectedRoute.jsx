import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { homePathFor } from '@/shared/auth/session';

/**
 * Renders its children only for a signed-in user. With `roles` (e.g. ['ROLE_ADMIN']) the user
 * must also have one of those roles; anyone else is sent to their own home page.
 * This only shapes the UI: the backend enforces the same roles on every API call.
 */
export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, loading, user } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <p>Checking authentication...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (roles && !roles.includes(user?.role)) {
    return <Navigate to={homePathFor(user)} replace />;
  }

  return children;
}
