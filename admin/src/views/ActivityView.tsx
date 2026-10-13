'use client';
// What every user has been doing, newest first.
import { useCallback, useState } from 'react';
import { EventList, KindChips } from '@/components/EventList';
import { ErrorBox, Loading, MoreButton } from '@/components/ui';
import { listEvents, type Cursor } from '@/lib/data';
import { usePaged } from '@/lib/hooks';
import { eventLabel } from '@/lib/logic';
import { loadNames } from '@/lib/names';

export function ActivityView() {
  const fetchPage = useCallback(async (after: Cursor | null) => {
    const page = await listEvents({ after });
    await loadNames(page.items.map((event) => event.uid));
    return page;
  }, []);
  const events = usePaged(fetchPage);
  const [kind, setKind] = useState('all');

  return (
    <div className="page">
      <h1>Activity</h1>
      <p className="muted">Everything users add or change, from all accounts.</p>
      {events.error ? <ErrorBox error={events.error} /> : null}
      {events.loading ? <Loading /> : (
        <>
          <KindChips events={events.items} picked={kind} onPick={setKind} />
          <EventList events={events.items.filter((event) => kind === 'all' || eventLabel(event.type) === kind)} showUser />
          <MoreButton hasMore={events.hasMore} busy={events.loadingMore} error={events.moreError} onClick={events.loadMore}>Load older activity</MoreButton>
        </>
      )}
    </div>
  );
}
