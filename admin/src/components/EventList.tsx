'use client';
// Activity grouped by day. With showUser each line names (and links to) the user it belongs to.
import Link from 'next/link';
import { fmtTime } from '@/lib/format';
import { eventKinds, eventLabel, eventTone, groupEventsByDay, relativeTime } from '@/lib/logic';
import { nameOf } from '@/lib/names';
import type { ActivityEvent, UserSummary } from '@/lib/types';
import { Badge, Empty } from './ui';

export const userHref = (uid: string): string => `/users/view/?uid=${encodeURIComponent(uid)}`;

export function EventList({ events, showUser = false }: { events: ActivityEvent[]; showUser?: boolean }) {
  if (!events.length) return <Empty>No activity yet.</Empty>;
  return (
    <div className="timeline">
      {groupEventsByDay(events).map((group) => (
        <section key={group.key}>
          <h3 className="day">{group.label}</h3>
          <ul>
            {group.events.map((event) => (
              <li key={`${event.uid}/${event.id}`} className="event">
                <time dateTime={new Date(event.at).toISOString()} title={new Date(event.at).toISOString()}>{fmtTime(event.at)}</time>
                <Badge tone={eventTone(event.type)}>{eventLabel(event.type)}</Badge>
                <span className="event-text">
                  {showUser ? <><Link href={userHref(event.uid)} className="event-user">{nameOf(event.uid)}</Link>{' '}</> : null}
                  {event.summary}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** "Everything" and one chip per kind of activity in the list; the chosen kind is passed back through onPick. */
export function KindChips({ events, picked, onPick }: { events: ActivityEvent[]; picked: string; onPick: (kind: string) => void }) {
  return (
    <div className="chips">
      {['all', ...eventKinds(events)].map((kind) => (
        <button key={kind} type="button" className={`chip${kind === picked ? ' active' : ''}`} onClick={() => onPick(kind)}>
          {kind === 'all' ? 'Everything' : kind}
        </button>
      ))}
    </div>
  );
}

/** Name and email of a user, as one cell. */
export function UserCell({ user }: { user: Pick<UserSummary, 'uid' | 'name' | 'email'> }) {
  return (
    <Link className="user-cell" href={userHref(user.uid)}>
      <strong>{user.name || '(no name)'}</strong>
      <span className="muted">{user.email || user.uid}</span>
    </Link>
  );
}

export const lastSeen = (user: Pick<UserSummary, 'lastActiveAt'>): string => relativeTime(user.lastActiveAt);
