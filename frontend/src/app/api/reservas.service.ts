import { Injectable, inject } from '@angular/core';
import { APP_CONFIG } from '../auth/config';
import { AuthService } from '../auth/auth.service';

// Interfaces que calzan con los DTOs del backend (com.belleza.reservas.dto).
// Instant viaja como texto ISO-8601 en UTC; BigDecimal, como número.

export type EstadoCita = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';

export interface CitaRequest {
  servicioId: number;
  especialistaId: number;
  inicio: string;
  notasCliente: string | null;
}

export interface CitaResponse {
  id: number;
  servicio: string;
  especialista: string;
  clienteId: string;
  inicio: string;
  fin: string;
  estado: EstadoCita;
  notasCliente: string | null;
  comentarioAprobador: string | null;
}

export interface ResolucionRequest {
  aprobar: boolean;
  comentario: string | null;
}

export interface ServicioResponse {
  id: number;
  nombre: string;
  descripcion: string | null;
  duracionMinutos: number;
  precio: number;
  activo: boolean;
}

export interface EspecialistaResponse {
  id: number;
  nombre: string;
  email: string;
  especialidad: string | null;
  activo: boolean;
}

/** Error de la API con el código HTTP y un mensaje listo para mostrar. */
export class ErrorApi extends Error {
  constructor(
    readonly status: number,
    mensaje: string,
  ) {
    super(mensaje);
  }
}

export function mensajeDe(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

@Injectable({ providedIn: 'root' })
export class ReservasService {
  private readonly cfg = inject(APP_CONFIG);
  private readonly auth = inject(AuthService);

  // ───────────── Clienta: /v1/citas ─────────────

  listarMisCitas(): Promise<CitaResponse[]> {
    return this.pedir('GET', '/v1/citas');
  }

  crearCita(req: CitaRequest): Promise<CitaResponse> {
    return this.pedir('POST', '/v1/citas', req);
  }

  actualizarCita(id: number, req: CitaRequest): Promise<CitaResponse> {
    return this.pedir('PUT', `/v1/citas/${id}`, req);
  }

  cancelarCita(id: number): Promise<CitaResponse> {
    return this.pedir('DELETE', `/v1/citas/${id}`);
  }

  // ───────────── Aprobadora: /v1/aprobaciones ─────────────

  listarPendientes(): Promise<CitaResponse[]> {
    return this.pedir('GET', '/v1/aprobaciones/pendientes');
  }

  resolver(id: number, req: ResolucionRequest): Promise<CitaResponse> {
    return this.pedir('PATCH', `/v1/aprobaciones/${id}`, req);
  }

  // ───────────── Catálogo ─────────────

  listarServicios(): Promise<ServicioResponse[]> {
    return this.pedir('GET', '/v1/servicios');
  }

  listarEspecialistas(): Promise<EspecialistaResponse[]> {
    return this.pedir('GET', '/v1/especialistas');
  }

  /** Igual que ApiService: fetch con el ACCESS token como Bearer. */
  private async pedir<T>(metodo: string, ruta: string, cuerpo?: unknown): Promise<T> {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.auth.getTokens()?.access_token ?? ''}`,
    };
    if (cuerpo !== undefined) headers['Content-Type'] = 'application/json';

    let respuesta: Response;
    try {
      respuesta = await fetch(`${this.cfg.apiUrl}${ruta}`, {
        method: metodo,
        cache: 'no-store',
        headers,
        body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      });
    } catch (e) {
      // Un fallo de red aquí casi siempre es CORS o el backend apagado.
      throw new ErrorApi(0, `No se pudo contactar la API (red o CORS): ${mensajeDe(e)}`);
    }

    const texto = await respuesta.text();
    let datos: unknown = null;
    try {
      datos = texto ? JSON.parse(texto) : null;
    } catch {
      datos = texto;
    }

    if (!respuesta.ok) {
      throw new ErrorApi(respuesta.status, mensajeDeError(respuesta.status, datos));
    }
    return datos as T;
  }
}

/** Spring responde ProblemDetail ("detail"); el API Gateway, {"message": ...}. */
function mensajeDeError(status: number, datos: unknown): string {
  let detalle: string | null = null;
  if (datos && typeof datos === 'object') {
    const cuerpo = datos as { detail?: unknown; message?: unknown };
    const valor = cuerpo.detail ?? cuerpo.message;
    detalle = typeof valor === 'string' ? valor : null;
  } else if (typeof datos === 'string' && datos.trim()) {
    detalle = datos.trim();
  }

  if (status === 401) {
    return 'Tu sesión expiró o el token no es válido. Cierra sesión y vuelve a entrar.';
  }
  if (status === 403) {
    return `No tienes permiso para esta acción (HTTP 403${detalle ? `: ${detalle}` : ''}).`;
  }
  return detalle ? `${detalle} (HTTP ${status})` : `La API respondió HTTP ${status}.`;
}
