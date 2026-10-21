import { Component, computed, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { APP_CONFIG, configuracionIncompleta } from '../../auth/config';
import { AuthService } from '../../auth/auth.service';
import { SesionService } from '../../auth/sesion.service';
import { decodificarJwt } from '../../auth/jwt';
import { ApiService, Resultado } from '../../api/api.service';

/** La página de demo original: claims de los tokens y llamadas con y sin token. */
@Component({
  selector: 'app-identidad',
  imports: [JsonPipe],
  templateUrl: './identidad.html',
})
export class IdentidadComponent {
  protected readonly config = inject(APP_CONFIG);
  protected readonly faltantes = configuracionIncompleta(this.config);
  protected readonly auth = inject(AuthService);
  protected readonly sesion = inject(SesionService);
  protected readonly api = inject(ApiService);

  protected readonly idClaims = computed(() => decodificarJwt(this.auth.getTokens()?.id_token));
  protected readonly accessClaims = computed(() => decodificarJwt(this.auth.getTokens()?.access_token));
  protected readonly resultado = signal<Resultado | null>(null);
  protected readonly cargando = signal(false);

  protected async llamar(fn: () => Promise<Resultado>) {
    this.cargando.set(true);
    this.resultado.set(await fn());
    this.cargando.set(false);
  }
}
