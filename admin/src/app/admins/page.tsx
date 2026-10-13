import type { Metadata } from 'next';
import { AdminsView } from '@/views/AdminsView';

export const metadata: Metadata = { title: 'Admins' };

export default function Page() {
  return <AdminsView />;
}
