// Remembers users' names, so activity lists can show "Aisha" instead of an account id.
import { getUser } from './data';
import type { UserSummary } from './types';

const userNames = new Map<string, string>();

export function rememberUsers(users: Pick<UserSummary, 'uid' | 'name' | 'email'>[]): void {
  for (const user of users) userNames.set(user.uid, user.name || user.email || user.uid);
}

export const nameOf = (uid: string): string => userNames.get(uid) ?? uid;

/** Looks up the names of users that activity mentions and that are not known yet. */
export async function loadNames(uids: string[]): Promise<void> {
  const missing = [...new Set(uids)].filter((uid) => !userNames.has(uid));
  await Promise.all(missing.map(async (uid) => {
    try {
      const user = await getUser(uid);
      userNames.set(uid, user ? (user.name || user.email || uid) : uid);
    } catch {
      userNames.set(uid, uid);
    }
  }));
}
