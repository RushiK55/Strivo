'use client';
// Who can use this dashboard. An admin can add another admin by their account id and remove anyone but themselves.
import { Trash2, UserPlus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { UserAvatar } from '@/components/UserAvatar';
import { ErrorBox, FormField, Page, PageSkeleton } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { addAdmin, listAdmins, removeAdmin } from '@/lib/data';
import { fmtDate } from '@/lib/format';
import { useLoad } from '@/lib/hooks';

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

export function TeamView() {
  const me = useAdmin();
  const { toast, confirm } = useFeedback();
  const { data: admins, error, reload } = useLoad(listAdmins);
  const [uid, setUid] = useState('');
  const [email, setEmail] = useState('');

  async function add(event: FormEvent) {
    event.preventDefault();
    const id = uid.trim();
    if (!/^[A-Za-z0-9]{10,}$/.test(id)) { toast('That does not look like an account id.', 'error'); return; }
    try {
      await addAdmin(id, email.trim(), me.uid);
      toast('Added to the team');
      setUid('');
      setEmail('');
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function remove(id: string, label: string) {
    if (!await confirm({ title: 'Remove from team', message: `Remove ${label} from the team? They will no longer be able to open this dashboard.`, confirmText: 'Remove' })) return;
    try {
      await removeAdmin(id);
      toast('Removed from the team');
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  return (
    <Page title="Team" description="The trainers and owners who can use this dashboard. They see clients' training, body weight and daily calories and protein, never what a client eats, and cannot change a client's data.">
      {error ? <ErrorBox error={error} /> : null}
      {!admins ? (error ? null : <PageSkeleton />) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">People with access</CardTitle>
            <CardDescription>{`${admins.length} ${admins.length === 1 ? 'person' : 'people'}`}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>{['Email', 'Account id', 'Added', ''].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
                </TableHeader>
                <TableBody>
                  {admins.map((admin) => (
                    <TableRow key={admin.id}>
                      <TableCell>
                        <span className="flex items-center gap-3">
                          <UserAvatar email={admin.email} />
                          <span>{admin.email || '–'}{admin.id === me.uid ? ' (you)' : ''}</span>
                        </span>
                      </TableCell>
                      <TableCell className="font-mono text-xs">{admin.id}</TableCell>
                      <TableCell>{fmtDate(admin.addedAt)}</TableCell>
                      <TableCell className="text-right">
                        {admin.id === me.uid ? null : (
                          <Button variant="destructive" size="sm" type="button" onClick={() => void remove(admin.id, admin.email || admin.id)}><Trash2 /> Remove</Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Add a trainer or owner</CardTitle>
          <CardDescription>
            The person must already have an account (they sign up in the app, or you add them in Firebase Authentication). Copy their uid from the Authentication page. This gives them the same access as you.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid items-end gap-4 md:grid-cols-[1fr_1fr_auto]" onSubmit={(event) => void add(event)}>
            <FormField label="Account id">
              {(id) => <Input id={id} type="text" name="uid" placeholder="Account id (uid) from Firebase Authentication" autoComplete="off" value={uid} onChange={(event) => setUid(event.target.value)} />}
            </FormField>
            <FormField label="Email">
              {(id) => <Input id={id} type="email" name="email" placeholder="their email, to recognise them later" autoComplete="off" value={email} onChange={(event) => setEmail(event.target.value)} />}
            </FormField>
            <Button type="submit"><UserPlus /> Add to team</Button>
          </form>
        </CardContent>
      </Card>
    </Page>
  );
}
