import type { Metadata } from 'next';
import { UserDetailView } from '@/views/UserDetailView';

export const metadata: Metadata = { title: 'User' };

export default function Page() {
  return <UserDetailView />;
}
