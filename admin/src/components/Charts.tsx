'use client';
// The two charts the dashboard uses, as thin wrappers over shadcn's chart component (Recharts): bars and a line.
import { Bar, BarChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts';
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@/components/ui/chart';

type Row = Record<string, string | number>;

interface SeriesProps {
  data: Row[];
  /** The field on the x axis (a ready-to-show label). */
  xKey: string;
  /** The field to draw. */
  yKey: string;
  /** What the value is called in the tooltip. */
  label: string;
  /** One of the theme's chart colours, 1 to 5. */
  color?: 1 | 2 | 3 | 4 | 5;
  className?: string;
}

const configFor = ({ yKey, label, color = 1 }: Pick<SeriesProps, 'yKey' | 'label' | 'color'>): ChartConfig => ({ [yKey]: { label, color: `var(--chart-${color})` } });

export function BarSeries({ data, xKey, yKey, label, color, className = 'h-56 w-full' }: SeriesProps) {
  return (
    <ChartContainer config={configFor({ yKey, label, color })} className={className}>
      <BarChart data={data} margin={{ left: 0, right: 0 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey={xKey} tickLine={false} axisLine={false} tickMargin={8} interval="preserveStartEnd" />
        <ChartTooltip cursor={false} content={<ChartTooltipContent hideLabel={false} />} />
        <Bar dataKey={yKey} fill={`var(--color-${yKey})`} radius={4} isAnimationActive={false} />
      </BarChart>
    </ChartContainer>
  );
}

export function LineSeries({ data, xKey, yKey, label, color, className = 'h-56 w-full' }: SeriesProps) {
  return (
    <ChartContainer config={configFor({ yKey, label, color })} className={className}>
      <LineChart data={data} margin={{ left: 4, right: 12, top: 8 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey={xKey} tickLine={false} axisLine={false} tickMargin={8} interval="preserveStartEnd" />
        <YAxis domain={['auto', 'auto']} tickLine={false} axisLine={false} width={40} />
        <ChartTooltip cursor={false} content={<ChartTooltipContent />} />
        <Line dataKey={yKey} type="monotone" stroke={`var(--color-${yKey})`} strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
      </LineChart>
    </ChartContainer>
  );
}
