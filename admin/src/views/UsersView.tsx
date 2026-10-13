'use client';
// Everyone who uses the app, the most recently active first.
import { useCallback, useEffect, useState } from 'react';
import { UserCell, lastSeen } from '@/components/EventList';
import { Badge, ErrorBox, Loading, MoreButton } from '@/components/ui';
import { listUsers, type Cursor } from '@/lib/data';
import { fmtDate } from '@/lib/format';
import { usePaged } from '@/lib/hooks';
import { matchesUser, STATUS_LABEL, STATUS_TONE, summarizeUsers, userStatus } from '@/lib/logic';
import { rememberUsers } from '@/lib/names';

export function UsersView() {
  const fetchPage = useCallback((after: Cursor | null) => listUsers(after), []);
  const users = usePaged(fetchPage);
  const [search, setSearch] = useState('');

  useEffect(() => { rememberUsers(users.items); }, [users.items]);

  const summary = summarizeUsers(users.items);
  const rows = users.items.filter((user) => matchesUser(user, search));

  return (
    <div className="page">
      <h1>Users</h1>
      {users.error ? <ErrorBox error={users.error} /> : null}
      {users.loading ? <Loading /> : (
        <>
          <p className="muted">{`${users.items.length} loaded · ${summary.today} active today · ${summary.week} active this week · ${summary.inactive} inactive`}</p>
          <input
            type="search" className="search" placeholder="Search by name, email or last action" aria-label="Search users"
            value={search} onChange={(event) => setSearch(event.target.value)}
          />
          {rows.length ? (
            <table className="table">
              <thead>
                <tr>{['User', 'Status', 'Last active', 'Last action', 'Joined', 'App'].map((title) => <th key={title} scope="col">{title}</th>)}</tr>
              </thead>
              <tbody>
                {rows.map((user) => {
                  const status = userStatus(user.lastActiveAt);
                  return (
                    <tr key={user.uid}>
                      <td><UserCell user={user} /></td>
                      <td><Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge></td>
                      <td>{lastSeen(user)}</td>
                      <td className="wide">{user.lastEvent || <span className="muted">–</span>}</td>
                      <td>{fmtDate(user.createdAt)}</td>
                      <td className="muted">{user.appVersion || '–'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <p className="empty">{users.items.length ? 'No user matches that search.' : 'No users yet. A user appears here once they open the new version of the app.'}</p>
          )}
          <MoreButton hasMore={users.hasMore} busy={users.loadingMore} error={users.moreError} onClick={users.loadMore}>Load more</MoreButton>
        </>
      )}
    </div>
  );
}
