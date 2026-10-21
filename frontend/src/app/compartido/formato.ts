/**
 * Fechas en hora de Chile continental, sin librerías: el backend guarda y
 * devuelve instantes UTC, y la pantalla siempre muestra y pide hora de Chile,
 * aunque el navegador esté en otra zona.
 */
const ZONA = 'America/Santiago';

const formatoPartes = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const formatoFechaHora = new Intl.DateTimeFormat('es-CL', {
  timeZone: ZONA,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const formatoHora = new Intl.DateTimeFormat('es-CL', {
  timeZone: ZONA,
  hour: '2-digit',
  minute: '2-digit',
});

const formatoPesos = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

function partesEnChile(fecha: Date): Record<string, string> {
  const partes: Record<string, string> = {};
  for (const { type, value } of formatoPartes.formatToParts(fecha)) partes[type] = value;
  return partes;
}

/** Milisegundos que la hora de Chile va adelantada (o atrasada) respecto de UTC en ese instante. */
function desfaseChile(fecha: Date): number {
  const p = partesEnChile(fecha);
  const comoUtc = Date.UTC(+p['year'], +p['month'] - 1, +p['day'], +p['hour'], +p['minute'], +p['second']);
  return comoUtc - Math.floor(fecha.getTime() / 1000) * 1000;
}

/** "2026-10-25T15:30" (hora de Chile, de un datetime-local) → ISO UTC para el backend. */
export function chileAIsoUtc(valor: string): string {
  const [fecha, hora] = valor.split('T');
  const [anio, mes, dia] = fecha.split('-').map(Number);
  const [h, min] = hora.split(':').map(Number);
  const comoUtc = Date.UTC(anio, mes - 1, dia, h, min);
  // Dos pasadas: la segunda corrige si el desfase cambia ese día (horario de verano).
  let instante = comoUtc - desfaseChile(new Date(comoUtc));
  instante = comoUtc - desfaseChile(new Date(instante));
  return new Date(instante).toISOString();
}

/** ISO UTC del backend → "2026-10-25T15:30" en hora de Chile, para un datetime-local. */
export function isoAChileLocal(iso: string): string {
  const p = partesEnChile(new Date(iso));
  return `${p['year']}-${p['month']}-${p['day']}T${p['hour']}:${p['minute']}`;
}

export function ahoraEnChileLocal(): string {
  return isoAChileLocal(new Date().toISOString());
}

export function fechaHoraChile(iso: string): string {
  return formatoFechaHora.format(new Date(iso));
}

export function horaChile(iso: string): string {
  return formatoHora.format(new Date(iso));
}

export function pesos(valor: number): string {
  return formatoPesos.format(valor);
}
