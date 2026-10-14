'use client';
// The frame around every page: the sidebar (shadcn Sidebar, which becomes a drawer on a phone), the top bar with the
// breadcrumb, and the account menu with sign out.
import { Activity, CalendarCheck, ChevronsUpDown, Dumbbell, LayoutDashboard, LogOut, PersonStanding, ShieldCheck, Users, type LucideIcon } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from '@/components/ui/breadcrumb';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Separator } from '@/components/ui/separator';
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton,
  SidebarMenuItem, SidebarProvider, SidebarTrigger,
} from '@/components/ui/sidebar';
import { useAdmin, useAuth } from './AuthProvider';
import { UserAvatar } from './UserAvatar';

const NAV: { href: string; label: string; icon: LucideIcon }[] = [
  { href: '/', label: 'Overview', icon: LayoutDashboard },
  { href: '/clients/', label: 'Clients', icon: Users },
  { href: '/attendance/', label: 'Attendance', icon: CalendarCheck },
  { href: '/activity/', label: 'Activity', icon: Activity },
  { href: '/body/', label: 'Body', icon: PersonStanding },
  { href: '/library/', label: 'Library', icon: Dumbbell },
  { href: '/team/', label: 'Team', icon: ShieldCheck },
];

/** Which section a path belongs to: "/clients/view/" is part of "/clients/". */
const section = (path: string): string => (path === '/' || path === '' ? '/' : `/${path.split('/')[1] ?? ''}/`);

/** The trail in the top bar: the section, and the user's page when one is open. */
function Crumbs({ pathname }: { pathname: string }) {
  const active = section(pathname);
  const current = NAV.find((item) => item.href === active) ?? NAV[0]!;
  const deeper = pathname.startsWith('/clients/view');
  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem>
          {deeper ? <BreadcrumbLink render={<Link href={current.href} />}>{current.label}</BreadcrumbLink> : <BreadcrumbPage>{current.label}</BreadcrumbPage>}
        </BreadcrumbItem>
        {deeper ? (
          <>
            <BreadcrumbSeparator />
            <BreadcrumbItem><BreadcrumbPage>Client</BreadcrumbPage></BreadcrumbItem>
          </>
        ) : null}
      </BreadcrumbList>
    </Breadcrumb>
  );
}

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { signOut } = useAuth();
  const admin = useAdmin();
  const active = section(pathname);
  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader className="px-4 pt-5">
          <span className="flex items-center gap-2 text-lg font-bold">
            <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground"><Dumbbell className="size-4" aria-hidden="true" /></span>
            <span>Strivo <span className="text-primary">Admin</span></span>
          </span>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Coaching</SidebarGroupLabel>
            <SidebarGroupContent>
              <nav aria-label="Main">
                <SidebarMenu>
                  {NAV.map((item) => (
                    <SidebarMenuItem key={item.href}>
                      <SidebarMenuButton
                        isActive={item.href === active}
                        render={<Link href={item.href} aria-current={item.href === active ? 'page' : undefined} />}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </nav>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter className="p-2">
          <SidebarMenu>
            <SidebarMenuItem>
              <DropdownMenu>
                <DropdownMenuTrigger render={<SidebarMenuButton size="lg" aria-label="Account menu" />}>
                  <UserAvatar email={admin.email} size="sm" />
                  <span className="min-w-0 flex-1 truncate text-left">{admin.email || admin.uid}</span>
                  <ChevronsUpDown className="ml-auto size-4 text-muted-foreground" />
                </DropdownMenuTrigger>
                <DropdownMenuContent side="top" align="start" className="min-w-56">
                  <div className="px-1.5 py-1 text-xs break-all text-muted-foreground">{admin.email || admin.uid}</div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={() => void signOut()}><LogOut /> Sign out</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset id="content" tabIndex={-1} className="min-w-0">
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-1 data-vertical:h-4 data-vertical:self-center" />
          <Crumbs pathname={pathname} />
        </header>
        {children}
      </SidebarInset>
    </SidebarProvider>
  );
}
