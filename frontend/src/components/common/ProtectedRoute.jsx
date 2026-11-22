import Spinner from '@/shared/components/ui/Spinner';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, loading, user } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <Spinner label="Checking authentication..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const currentRole = user?.role?.replace(/^ROLE_/, '');
  if (roles && !roles.some((role) => role.replace(/^ROLE_/, '') === currentRole)) {
    return <p role="alert" style={{ padding: "var(--space-8)" }}>You do not have permission to view this page.</p>;
  }

  return children;
}
