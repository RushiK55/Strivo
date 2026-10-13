'use client';
// One user: who they are, and everything they have saved. The user comes from the address (?uid=...), because a static
// site cannot have a page for every account id.
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useState } from 'react';
import { Badge, Empty, ErrorBox, Loading } from '@/components/ui';
import { countOf, getUser } from '@/lib/data';
import { fmtDate, fmtNumber } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import { relativeTime, STATUS_LABEL, STATUS_TONE, userStatus } from '@/lib/logic';
import { TABS, UserTab, type TabKey } from './UserTabs';

const Back = () => <p><Link href="/users/">← Users</Link></p>;

export function UserDetailView() {
  return (
    <Suspense fallback={<div className="page"><Loading /></div>}>
      <FromAddress />
    </Suspense>
  );
}

function FromAddress() {
  const uid = useSearchParams().get('uid');
  if (!uid) return <div className="page"><Back /><Empty>Pick a user from the Users page.</Empty></div>;
  return <UserPage key={uid} uid={uid} />;
}

function UserPage({ uid }: { uid: string }) {
  const load = useCallback(() => getUser(uid), [uid]);
  const { data: user, error } = useLoad(load);
  const [tab, setTab] = useState<TabKey>('activity');

  if (error) return <div className="page"><Back /><ErrorBox error={error} /></div>;
  if (user === undefined) return <div className="page"><Loading /></div>;
  if (user === null) {
    return <div className="page"><Back /><Empty>That user has no summary yet. It appears once they open the new version of the app.</Empty></div>;
  }

  const status = userStatus(user.lastActiveAt);
  return (
    <div className="page">
      <Back />
      <header className="user-head">
        <div>
          <h1>{user.name || '(no name)'}</h1>
          <p className="muted">{user.email || uid}</p>
        </div>
        <div className="user-meta">
          <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>
          <span>{`Last active ${relativeTime(user.lastActiveAt)}`}</span>
          <span className="muted">{`Joined ${fmtDate(user.createdAt)} · app ${user.appVersion || '–'} · ${user.platform || '–'}`}</span>
        </div>
      </header>
      <Counts uid={uid} />
      <nav className="tabs" role="tablist">
        {TABS.map(([key, label]) => (
          <button key={key} type="button" role="tab" className={`tab${key === tab ? ' active' : ''}`} aria-selected={key === tab} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </nav>
      <div className="tab-content"><UserTab key={tab} tab={tab} uid={uid} /></div>
    </div>
  );
}

/** The numbers under the header arrive when they can; the page does not wait for them. */
function Counts({ uid }: { uid: string }) {
  const load = useCallback(() => Promise.allSettled([countOf(uid, 'sessions'), countOf(uid, 'events'), countOf(uid, 'plans'), countOf(uid, 'weights')]), [uid]);
  const { data } = useLoad(load);
  const [workouts, actions, plans, weighIns] = (data ?? []).map((result) => (result.status === 'fulfilled' ? fmtNumber(result.value) : '–'));
  return <p className="muted">{data ? `${workouts} workouts · ${actions} recorded actions · ${plans} plans · ${weighIns} weigh-ins` : ' '}</p>;
}
