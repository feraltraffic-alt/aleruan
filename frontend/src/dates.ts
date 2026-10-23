/** Date-only helpers on ISO yyyy-mm-dd strings, in UTC so time zones never shift a day. */
const DAY = 86_400_000;
const parse = (s: string) => new Date(`${s}T00:00:00Z`);

export const isoDay = (d: Date) => d.toISOString().slice(0, 10);

export const addDays = (s: string, n: number) => isoDay(new Date(parse(s).getTime() + n * DAY));

/** Monday of the week containing the date. */
export const mondayOf = (s: string) => addDays(s, -((parse(s).getUTCDay() + 6) % 7));

/** Days from a to b, both included. */
export const daysInclusive = (a: string, b: string) => Math.round((parse(b).getTime() - parse(a).getTime()) / DAY) + 1;

/** Today in the user's local calendar. */
export const localToday = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
