import type { Metadata } from 'next';
import { AttendanceView } from '@/views/AttendanceView';

export const metadata: Metadata = { title: 'Attendance' };

export default function Page() {
  return <AttendanceView />;
}
