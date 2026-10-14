// A round badge with the user's initials.
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { initials } from '@/lib/logic';

export function UserAvatar({ name, email, size = 'default' }: { name?: string; email?: string; size?: 'default' | 'sm' | 'lg' }) {
  return (
    <Avatar size={size}>
      <AvatarFallback className="bg-primary/15 font-semibold text-primary">{initials(name, email)}</AvatarFallback>
    </Avatar>
  );
}
