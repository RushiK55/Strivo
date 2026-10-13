'use client';
// The five tabs of a user's page. Read-only: an admin cannot change a user's data.
import { useCallback, useState } from 'react';
import { EventList, KindChips } from '@/components/EventList';
import { Badge, Empty, ErrorBox, Loading, MoreButton, Sparkline } from '@/components/ui';
import { getProfile, listEvents, listUserRows, type Cursor } from '@/lib/data';
import { fmtNumber } from '@/lib/format';
import { useLoad, usePaged } from '@/lib/hooks';
import { dayTotals, describeSets, durationText, eventLabel, parseJsonField, sessionVolume, weightSeries } from '@/lib/logic';
import type { ExerciseRow, FoodRow, PlanRow, SessionExercise, WeightRow, WorkoutRow } from '@/lib/types';

export const TABS = [['activity', 'Activity'], ['workouts', 'Workouts'], ['plans', 'Plans'], ['body', 'Body'], ['food', 'Food']] as const;
export type TabKey = (typeof TABS)[number][0];

const DemoBadge = ({ row }: { row: { isDemo?: number } }) => (row.isDemo ? <Badge>demo data</Badge> : null);

/** Runs a loader and shows the spinner, the error or what it returned. */
function Loaded<T>({ load, children }: { load: () => Promise<T>; children: (data: T) => React.ReactNode }) {
  const { data, error } = useLoad(load);
  if (error) return <ErrorBox error={error} />;
  if (data === undefined) return <Loading />;
  return <>{children(data)}</>;
}

export function UserTab({ tab, uid }: { tab: TabKey; uid: string }) {
  switch (tab) {
    case 'activity': return <ActivityTab uid={uid} />;
    case 'workouts': return <WorkoutsTab uid={uid} />;
    case 'plans': return <PlansTab uid={uid} />;
    case 'body': return <BodyTab uid={uid} />;
    case 'food': return <FoodTab uid={uid} />;
  }
}

function ActivityTab({ uid }: { uid: string }) {
  const fetchPage = useCallback((after: Cursor | null) => listEvents({ uid, after }), [uid]);
  const events = usePaged(fetchPage);
  const [kind, setKind] = useState('all');
  if (events.error) return <ErrorBox error={events.error} />;
  if (events.loading) return <Loading />;
  return (
    <div>
      <KindChips events={events.items} picked={kind} onPick={setKind} />
      <EventList events={events.items.filter((event) => kind === 'all' || eventLabel(event.type) === kind)} />
      <MoreButton hasMore={events.hasMore} busy={events.loadingMore} error={events.moreError} onClick={events.loadMore}>Load older activity</MoreButton>
    </div>
  );
}

function WorkoutsTab({ uid }: { uid: string }) {
  const load = useCallback(() => listUserRows<WorkoutRow>(uid, 'sessions', { orderField: 'date', max: 60 }), [uid]);
  return (
    <Loaded load={load}>
      {(sessions) => sessions.length === 0 ? <Empty>No workouts finished yet.</Empty> : (
        <div className="stack">
          {sessions.map((session) => {
            const exercises = parseJsonField<SessionExercise[]>(session.exercises, []);
            const volume = sessionVolume(exercises);
            return (
              <details key={session.id} className="card">
                <summary>
                  <strong>{session.planName || 'Workout'}</strong>
                  <span className="muted">{` · ${String(session.date || '').replace('T', ' ').slice(0, 16)}`}</span>
                  {` · ${durationText(session.totalTime) || '–'} · ${exercises.length} ${exercises.length === 1 ? 'exercise' : 'exercises'}`}
                  {volume > 0 ? ` · ${fmtNumber(Math.round(volume))} kg` : ''}{' '}
                  <DemoBadge row={session} />
                </summary>
                <table className="table compact">
                  <thead><tr><th>Exercise</th><th>Sets (W warm-up, D drop, F failure)</th></tr></thead>
                  <tbody>
                    {exercises.map((exercise, index) => (
                      <tr key={index}><td>{exercise.name}</td><td>{describeSets(exercise.sets) || '–'}</td></tr>
                    ))}
                  </tbody>
                </table>
              </details>
            );
          })}
        </div>
      )}
    </Loaded>
  );
}

const DAY_ORDER = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday', 'Prebuilt'];

function PlansTab({ uid }: { uid: string }) {
  const load = useCallback(async () => {
    const [plans, exercises] = await Promise.all([
      listUserRows<PlanRow>(uid, 'plans', { max: 200 }),
      listUserRows<ExerciseRow>(uid, 'exercises', { max: 1000 }),
    ]);
    return { plans, exercises };
  }, [uid]);
  return (
    <Loaded load={load}>
      {({ plans, exercises }) => {
        if (!plans.length) return <Empty>No plans yet.</Empty>;
        const byPlan = new Map<string, ExerciseRow[]>();
        for (const exercise of exercises) {
          const key = String(exercise.planId);
          byPlan.set(key, [...(byPlan.get(key) ?? []), exercise]);
        }
        const ordered = [...plans].sort((a, b) =>
          DAY_ORDER.indexOf(a.planDay ?? '') - DAY_ORDER.indexOf(b.planDay ?? '') || String(a.planName).localeCompare(String(b.planName)));
        return (
          <div className="stack">
            {ordered.map((plan) => {
              const items = [...(byPlan.get(String(plan.planId)) ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
              return (
                <section key={plan.id} className="card">
                  <h3>{plan.planName} <Badge tone="info">{plan.planDay === 'Prebuilt' ? 'Library' : plan.planDay}</Badge> <DemoBadge row={plan} /></h3>
                  {items.length ? (
                    <ul className="plain">
                      {items.map((exercise) => (
                        <li key={exercise.id}>
                          <strong>{exercise.name}</strong>
                          <span className="muted">
                            {` ${exercise.sets} × ${exercise.reps}${Number(exercise.dropSets) > 0 ? ` + ${exercise.dropSets} drop` : ''}${Number(exercise.weight) > 0 ? ` @ ${exercise.weight} kg` : ', bodyweight'}`}
                          </span>
                          {exercise.notes ? <div className="note">{exercise.notes}</div> : null}
                        </li>
                      ))}
                    </ul>
                  ) : <p className="muted">No exercises.</p>}
                </section>
              );
            })}
          </div>
        );
      }}
    </Loaded>
  );
}

function Fact({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="stat">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function BodyTab({ uid }: { uid: string }) {
  const load = useCallback(async () => {
    const [profile, weights] = await Promise.all([getProfile(uid), listUserRows<WeightRow>(uid, 'weights', { orderField: 'date', max: 120 })]);
    return { profile, series: weightSeries(weights) };
  }, [uid]);
  return (
    <Loaded load={load}>
      {({ profile, series }) => {
        const first = series[0];
        const latest = series.at(-1);
        return (
          <div>
            {profile ? (
              <div className="stats">
                <Fact label="Gender" value={profile.gender || '–'} />
                <Fact label="Age" value={profile.age ?? '–'} />
                <Fact label="Height" value={profile.height ? `${profile.height} cm` : '–'} />
                <Fact label="Weight" value={profile.weight ? `${profile.weight} kg` : '–'} />
                <Fact label="Rest days" value={(profile.restDays ?? []).join(', ') || '–'} />
              </div>
            ) : <Empty>No profile yet.</Empty>}
            {first && latest ? (
              <section className="panel">
                <h2>Weight over time</h2>
                <Sparkline values={series.map((point) => point.kg)} />
                <p className="muted">{`${first.date}: ${first.kg} kg → ${latest.date}: ${latest.kg} kg (${series.length} weigh-ins)`}</p>
                <table className="table compact">
                  <tbody>
                    {series.slice(-12).reverse().map((point) => <tr key={point.date}><td>{point.date}</td><td>{`${point.kg} kg`}</td></tr>)}
                  </tbody>
                </table>
              </section>
            ) : <Empty>No weigh-ins yet.</Empty>}
          </div>
        );
      }}
    </Loaded>
  );
}

function FoodTab({ uid }: { uid: string }) {
  const load = useCallback(() => listUserRows<FoodRow>(uid, 'food', { orderField: 'date', max: 300 }), [uid]);
  return (
    <Loaded load={load}>
      {(foods) => foods.length === 0 ? <Empty>Nothing logged yet.</Empty> : (
        <div className="stack">
          {dayTotals(foods).slice(0, 30).map((day) => (
            <details key={day.date} className="card">
              <summary>
                <strong>{day.date}</strong>{` · ${fmtNumber(day.kcal)} kcal · ${day.items.length} ${day.items.length === 1 ? 'item' : 'items'}`}
              </summary>
              <table className="table compact">
                <tbody>
                  {day.items.map((item) => (
                    <tr key={item.id}><td>{item.meal}</td><td>{item.name}</td><td>{`${item.calories} kcal`}</td><td><DemoBadge row={item} /></td></tr>
                  ))}
                </tbody>
              </table>
            </details>
          ))}
        </div>
      )}
    </Loaded>
  );
}
