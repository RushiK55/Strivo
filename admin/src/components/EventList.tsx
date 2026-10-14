'use client';
// Activity grouped by day. With showUser each line names (and links to) the user it belongs to.
import { CircleDot, ClipboardList, Dumbbell, Footprints, ListChecks, Scale, UserRound, Utensils, type LucideIcon } from 'lucide-react';
import Link from 'next/link';
import { fmtTime } from '@/lib/format';
import { eventLabel, eventTone, groupEventsByDay, relativeTime, safeSummary } from '@/lib/logic';
import { nameOf } from '@/lib/names';
import type { ActivityEvent, Tone, UserSummary } from '@/lib/types';
import { cn } from '@/lib/utils';
import { Empty } from './ui';
import { UserAvatar } from './UserAvatar';

export const userHref = (uid: string): string => `/clients/view/?uid=${encodeURIComponent(uid)}`;

const KIND_ICON: Record<string, LucideIcon> = {
  Workout: Dumbbell, Plan: ClipboardList, Exercise: ListChecks, Food: Utensils, Weight: Scale, Profile: UserRound, Activity: Footprints,
};

const TONE_CHIP: Record<Tone, string> = {
  good: 'bg-success/15 text-success',
  info: 'bg-info/15 text-info',
  warn: 'bg-warning/15 text-warning',
  bad: 'bg-destructive/15 text-destructive',
  neutral: 'bg-muted text-muted-foreground',
};

export function EventList({ events, showUser = false }: { events: ActivityEvent[]; showUser?: boolean }) {
  if (!events.length) return <Empty>No activity yet.</Empty>;
  return (
    <div className="space-y-5">
      {groupEventsByDay(events).map((group) => (
        <section key={group.key}>
          <h3 className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">{group.label}</h3>
          <ul className="divide-y">
            {group.events.map((event) => {
              const label = eventLabel(event.type);
              const Icon = KIND_ICON[label] ?? CircleDot;
              return (
                <li key={`${event.uid}/${event.id}`} className="flex items-start gap-3 py-2.5">
                  <span className={cn('grid size-8 flex-none place-items-center rounded-full', TONE_CHIP[eventTone(event.type)])} title={label}>
                    <Icon className="size-4" aria-hidden="true" />
                    <span className="sr-only">{label}</span>
                  </span>
                  <span className="min-w-0 flex-1 break-words pt-1">
                    {showUser ? <><Link href={userHref(event.uid)} className="mr-0.5 font-semibold text-primary hover:underline">{nameOf(event.uid)}</Link>{' '}</> : null}
                    {safeSummary(event.type, event.summary)}
                  </span>
                  <time className="flex-none pt-1 text-xs text-muted-foreground tabular-nums" dateTime={new Date(event.at).toISOString()} title={new Date(event.at).toISOString()}>
                    {fmtTime(event.at)}
                  </time>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** Name and email of a user, as one cell with their avatar. */
export function UserCell({ user }: { user: Pick<UserSummary, 'uid' | 'name' | 'email'> }) {
  return (
    <Link className="group flex items-center gap-3" href={userHref(user.uid)}>
      <UserAvatar name={user.name} email={user.email} />
      <span className="flex min-w-0 flex-col">
        <strong className="truncate group-hover:text-primary">{user.name || '(no name)'}</strong>
        <span className="truncate text-muted-foreground">{user.email || user.uid}</span>
      </span>
    </Link>
  );
}

export const lastSeen = (user: Pick<UserSummary, 'lastActiveAt'>): string => relativeTime(user.lastActiveAt);
