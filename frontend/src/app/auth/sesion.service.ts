import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthService, Tokens } from './auth.service';
import { decodificarJwt } from './jwt';

export type Grupo = 'clientes' | 'aprobadores';

/**
 * Estado de la sesión como signals. Los grupos salen del ACCESS token (es el que
 * valida el backend); el email, del ID token (es el que describe a la persona).
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly auth = inject(AuthService);
  private readonly tokens = signal<Tokens | null>(this.auth.getTokens());

  private readonly accessClaims = computed(() => decodificarJwt(this.tokens()?.access_token));
  private readonly idClaims = computed(() => decodificarJwt(this.tokens()?.id_token));

  /** Error del canje del code (state/nonce inválidos, Cognito con ?error=...). */
  readonly errorLogin = signal<string | null>(null);

  readonly sesionIniciada = computed(() => this.accessClaims() !== null);
  readonly grupos = computed<string[]>(() => {
    const grupos = this.accessClaims()?.['cognito:groups'];
    return Array.isArray(grupos) ? grupos : [];
  });
  readonly email = computed<string | null>(() => this.idClaims()?.['email'] ?? null);
  readonly esCliente = computed(() => this.grupos().includes('clientes'));
  readonly esAprobadora = computed(() => this.grupos().includes('aprobadores'));

  /** Se ejecuta una vez al arrancar: si la URL trae ?code=, lo canjea por tokens. */
  async inicializar(): Promise<void> {
    try {
      const tokens = await this.auth.procesarRetorno();
      if (tokens) this.tokens.set(tokens);
    } catch (e) {
      this.errorLogin.set((e as Error).message);
    }
  }

  /** El access token dura 60 minutos y no hay refresh: vencido, hay que volver a entrar. */
  vigente(): boolean {
    const exp = this.accessClaims()?.['exp'];
    return typeof exp === 'number' && exp * 1000 > Date.now();
  }

  tieneGrupo(grupo: Grupo): boolean {
    return this.grupos().includes(grupo);
  }

  /** Página de inicio según el grupo. Si tiene ambos grupos, prima el de aprobadora. */
  rutaInicial(): string | null {
    if (this.esAprobadora()) return '/pendientes';
    if (this.esCliente()) return '/mis-citas';
    return null;
  }

  login(): Promise<void> {
    return this.auth.login();
  }

  logout(): void {
    this.auth.logout();
  }
}
