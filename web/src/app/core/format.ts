/** Formato de importes y fechas para la interfaz (es-PE). */

export function money(amount: number, currency = 'PEN'): string {
  const symbol = currency === 'PEN' ? 'S/' : currency;
  return `${symbol} ${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const DAY = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' });
const TIME = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: false });

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "Hoy", "Ayer" o la fecha corta; sirve para agrupar movimientos por día. */
export function dayLabel(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(date, now)) return 'Hoy';
  if (sameDay(date, yesterday)) return 'Ayer';
  return DAY.format(date);
}

export function timeLabel(iso: string): string {
  return TIME.format(new Date(iso));
}

export function dateTimeLabel(iso: string): string {
  return `${DAY.format(new Date(iso))}, ${TIME.format(new Date(iso))}`;
}
