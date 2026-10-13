'use client';
// The frame around every page: the sidebar with the five sections, who is signed in, and sign out.
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { useAdmin, useAuth } from './AuthProvider';

const NAV = [
  { href: '/', label: 'Overview' },
  { href: '/users/', label: 'Users' },
  { href: '/activity/', label: 'Activity' },
  { href: '/library/', label: 'Library' },
  { href: '/admins/', label: 'Admins' },
];

/** Which section a path belongs to: "/users/view/" is part of "/users/". */
const section = (path: string): string => (path === '/' || path === '' ? '/' : `/${path.split('/')[1] ?? ''}/`);

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { signOut } = useAuth();
  const admin = useAdmin();
  const active = section(pathname);
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">Strivo <span>Admin</span></div>
        <nav className="nav" aria-label="Main">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} className={item.href === active ? 'active' : ''} aria-current={item.href === active ? 'page' : undefined}>
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="who">
          <span className="muted">{admin.email || admin.uid}</span>
          <button className="btn btn-small" type="button" onClick={() => void signOut()}>Sign out</button>
        </div>
      </aside>
      <main id="content" tabIndex={-1}>{children}</main>
    </div>
  );
}
