'use client';
// The first page: how many users, how many are active, and what is happening right now.
import Link from 'next/link';
import { useCallback } from 'react';
import { EventList, UserCell, lastSeen } from '@/components/EventList';
import { ErrorBox, Loading } from '@/components/ui';
import { countActiveUsers, countEventsSince, countLibrary, countUsers, listEvents, listUsers } from '@/lib/data';
import { fmtNumber } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import { loadNames, rememberUsers } from '@/lib/names';

const DAY = 24 * 60 * 60 * 1000;

const shown = (result: PromiseSettledResult<number>): string => (result.status === 'fulfilled' ? fmtNumber(result.value) : '–');

function firstFailure(results: PromiseSettledResult<unknown>[]): unknown {
  for (const result of results) if (result.status === 'rejected') return result.reason;
  return undefined;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

export function OverviewView() {
  const load = useCallback(async () => {
    const now = Date.now();
    const [users, active, events, library, feed, newest] = await Promise.allSettled([
      countUsers(), countActiveUsers(now - 7 * DAY), countEventsSince(now - DAY), countLibrary(), listEvents({ pageSize: 15 }), listUsers(),
    ]);
    const directory = newest.status === 'fulfilled' ? newest.value.items : [];
    rememberUsers(directory);
    const recent = feed.status === 'fulfilled' ? feed.value.items : [];
    // Names for the people in the feed that are not in the first page of users.
    await loadNames(recent.map((event) => event.uid));
    return {
      counts: { users: shown(users), active: shown(active), events: shown(events), library: shown(library) },
      recent,
      directory,
      failure: firstFailure([users, active, events, library, feed, newest]),
    };
  }, []);
  const { data, error } = useLoad(load);

  return (
    <div className="page">
      <h1>Overview</h1>
      {error ? <ErrorBox error={error} /> : null}
      {!data ? <Loading /> : (
        <>
          {data.failure ? <ErrorBox error={data.failure} /> : null}
          <div className="stats">
            <Stat label="Users" value={data.counts.users} />
            <Stat label="Active in the last 7 days" value={data.counts.active} />
            <Stat label="Actions in the last 24 hours" value={data.counts.events} />
            <Stat label="Library exercises" value={data.counts.library} />
          </div>
          <div className="columns">
            <section className="panel">
              <h2>Recent activity</h2>
              <EventList events={data.recent} showUser />
              <p><Link href="/activity/">See all activity</Link></p>
            </section>
            <section className="panel">
              <h2>Recently active users</h2>
              {data.directory.length ? (
                <ul className="plain">
                  {data.directory.slice(0, 8).map((user) => (
                    <li key={user.uid} className="row"><UserCell user={user} /><span className="muted">{lastSeen(user)}</span></li>
                  ))}
                </ul>
              ) : <p className="empty">Nobody has signed in with the new app yet.</p>}
              <p><Link href="/users/">See all users</Link></p>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
