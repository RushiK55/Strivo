import type { Metadata } from 'next';
import { ActivityView } from '@/views/ActivityView';

export const metadata: Metadata = { title: 'Activity' };

export default function Page() {
  return <ActivityView />;
}
