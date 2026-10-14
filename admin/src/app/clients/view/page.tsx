import type { Metadata } from 'next';
import { ClientDetailView } from '@/views/ClientDetailView';

export const metadata: Metadata = { title: 'Client' };

export default function Page() {
  return <ClientDetailView />;
}
