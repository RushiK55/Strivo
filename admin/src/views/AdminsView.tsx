'use client';
// Who can use this dashboard. An admin can add another admin by their account id and remove anyone but themselves.
import { useState, type FormEvent } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { ErrorBox, Field, Loading } from '@/components/ui';
import { addAdmin, listAdmins, removeAdmin } from '@/lib/data';
import { fmtDate } from '@/lib/format';
import { useLoad } from '@/lib/hooks';

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

export function AdminsView() {
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
      toast('Admin added');
      setUid('');
      setEmail('');
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function remove(id: string, label: string) {
    if (!await confirm({ title: 'Remove admin', message: `Remove ${label} as an admin?`, confirmText: 'Remove' })) return;
    try {
      await removeAdmin(id);
      toast('Admin removed');
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  return (
    <div className="page">
      <h1>Admins</h1>
      <p className="muted">Admins can see all users and manage the library. They cannot change a user&apos;s own data.</p>
      {error ? <ErrorBox error={error} /> : null}
      {!admins ? (error ? null : <Loading />) : (
        <table className="table">
          <thead>
            <tr>{['Email', 'Account id', 'Added', ''].map((title) => <th key={title} scope="col">{title}</th>)}</tr>
          </thead>
          <tbody>
            {admins.map((admin) => (
              <tr key={admin.id}>
                <td>{admin.email || '–'}{admin.id === me.uid ? ' (you)' : ''}</td>
                <td className="mono">{admin.id}</td>
                <td>{fmtDate(admin.addedAt)}</td>
                <td>
                  {admin.id === me.uid ? null : (
                    <button className="btn btn-small btn-danger" type="button" onClick={() => void remove(admin.id, admin.email || admin.id)}>Remove</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <h2>Add an admin</h2>
      <p className="muted">The person must already have an account (they sign up in the app, or you add them in Firebase Authentication). Copy their uid from the Authentication page.</p>
      <form className="form inline" onSubmit={(event) => void add(event)}>
        <Field label="Account id">
          <input type="text" name="uid" placeholder="Account id (uid) from Firebase Authentication" autoComplete="off" value={uid} onChange={(event) => setUid(event.target.value)} />
        </Field>
        <Field label="Email">
          <input type="email" name="email" placeholder="their email, to recognise them later" autoComplete="off" value={email} onChange={(event) => setEmail(event.target.value)} />
        </Field>
        <button className="btn btn-primary" type="submit">Add admin</button>
      </form>
    </div>
  );
}
