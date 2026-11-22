import { useAuth } from '@/auth/AuthContext';
import { AdminOverview, EmployeeHome, TechnicianHome } from '@/screens/HomeScreens';

/** Each role lands on its own home: an operations overview, a work queue, or "my incidents". */
export default function Home() {
  const { user } = useAuth();
  if (user?.role === 'ADMINISTRATOR') return <AdminOverview />;
  if (user?.role === 'TECHNICIAN') return <TechnicianHome />;
  return <EmployeeHome />;
}
