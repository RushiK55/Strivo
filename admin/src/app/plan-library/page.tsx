import type { Metadata } from 'next';
import { PlanLibraryView } from '@/views/PlanLibraryView';

export const metadata: Metadata = { title: 'Plan library' };

export default function Page() {
  return <PlanLibraryView />;
}
