// Small pieces of a client's summary that the client list, the attendance page and the client page all show.
import { ArrowDownRight, ArrowUpRight } from 'lucide-react';
import { Progress } from '@/components/ui/progress';
import { adherence, TRAINING_LABEL, TRAINING_TONE, trainingStatus } from '@/lib/coaching';
import type { ClientStats } from '@/lib/types';
import { ToneBadge } from './ui';

export function TrainingBadge({ stats }: { stats: ClientStats | undefined }) {
  const status = trainingStatus(stats);
  return <ToneBadge tone={TRAINING_TONE[status]}>{TRAINING_LABEL[status]}</ToneBadge>;
}

/** "2 of 4 days", with a bar for how much of the plan was done this week. */
export function WeekCell({ stats, workouts }: { stats: ClientStats | undefined; workouts?: number }) {
  if (!stats) return <span className="text-muted-foreground">–</span>;
  const done = workouts ?? stats.workouts7d;
  const percent = adherence({ workouts7d: done, plannedDays: stats.plannedDays });
  return (
    <div className="flex min-w-28 flex-col gap-1">
      <span className="tabular-nums">{stats.plannedDays ? `${done} of ${stats.plannedDays} days` : `${done} ${done === 1 ? 'workout' : 'workouts'}`}</span>
      {percent !== null ? <Progress value={percent} aria-label="Plan done this week" /> : <span className="text-xs text-muted-foreground">no weekly plan</span>}
    </div>
  );
}

/** Body weight and how it moved over the last month. */
export function WeightCell({ stats }: { stats: ClientStats | undefined }) {
  if (stats?.weightKg === undefined) return <span className="text-muted-foreground">–</span>;
  const change = stats.weightChange30dKg;
  return (
    <div className="flex flex-col">
      <span className="tabular-nums">{`${stats.weightKg} kg`}</span>
      {change !== undefined && change !== 0 ? (
        <span className="flex items-center gap-0.5 text-xs text-muted-foreground tabular-nums">
          {change > 0 ? <ArrowUpRight className="size-3" aria-hidden="true" /> : <ArrowDownRight className="size-3" aria-hidden="true" />}
          {`${change > 0 ? '+' : ''}${change} kg in 30 days`}
        </span>
      ) : null}
    </div>
  );
}

/** Average calories and protein of the last week (totals only: what was eaten is private). */
export function IntakeCell({ stats }: { stats: ClientStats | undefined }) {
  if (stats?.avgCalories7d === undefined) return <span className="text-muted-foreground">–</span>;
  return (
    <div className="flex flex-col">
      <span className="tabular-nums">{`${stats.avgCalories7d.toLocaleString('en-US')} kcal`}</span>
      {stats.avgProteinG7d !== undefined ? <span className="text-xs text-muted-foreground tabular-nums">{`${stats.avgProteinG7d} g protein`}</span> : null}
    </div>
  );
}
