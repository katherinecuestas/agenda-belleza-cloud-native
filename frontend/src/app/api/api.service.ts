import { Injectable, inject } from '@angular/core';
import { APP_CONFIG } from '../auth/config';
import { AuthService } from '../auth/auth.service';

export interface Resultado {
  descripcion: string;
  status: number;
  ok: boolean;
  cuerpo: unknown;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly cfg = inject(APP_CONFIG);
  private readonly auth = inject(AuthService);

  private get accessToken(): string {
    return this.auth.getTokens()?.access_token ?? '';
  }

  /** Respuesta uniforme para mostrar SIEMPRE el código HTTP en pantalla. */
  private async ejecutar(descripcion: string, peticion: Promise<Response>): Promise<Resultado> {
    try {
      const respuesta = await peticion;
      const texto = await respuesta.text();
      let cuerpo: unknown;
      try { cuerpo = JSON.parse(texto); } catch { cuerpo = texto; }
      return { descripcion, status: respuesta.status, ok: respuesta.ok, cuerpo };
    } catch (e) {
      // Un fallo de red aquí casi siempre es CORS: el navegador no deja leer la respuesta.
      return { descripcion, status: 0, ok: false, cuerpo: `Error de red o CORS: ${(e as Error).message}` };
    }
  }

  /** Endpoint estándar de OIDC: la identidad según el estándar. */
  obtenerUserInfo(): Promise<Resultado> {
    return this.ejecutar(
      'GET /oauth2/userInfo (OIDC)',
      fetch(`${this.cfg.cognitoDomain}/oauth2/userInfo`, {
        headers: { Authorization: `Bearer ${this.accessToken}` },
      }),
    );
  }

  /** La ruta del API Gateway, protegida por el JWT authorizer. */
  obtenerIndicadores(conToken: boolean): Promise<Resultado> {
    return this.ejecutar(
      conToken ? 'GET /datos con token' : 'GET /datos SIN token',
      fetch(`${this.cfg.apiUrl}/datos`, {
        cache: 'no-store',
        headers: conToken ? { Authorization: `Bearer ${this.accessToken}` } : {},
      }),
    );
  }
}