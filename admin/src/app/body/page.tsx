import type { Metadata } from 'next';
import { BodyView } from '@/views/BodyView';

export const metadata: Metadata = { title: 'Body' };

export default function Page() {
  return <BodyView />;
}
