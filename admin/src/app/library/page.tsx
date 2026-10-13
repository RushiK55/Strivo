import type { Metadata } from 'next';
import { LibraryView } from '@/views/LibraryView';

export const metadata: Metadata = { title: 'Library' };

export default function Page() {
  return <LibraryView />;
}
