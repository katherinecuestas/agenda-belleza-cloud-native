import { Component, OnInit, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { APP_CONFIG, configuracionIncompleta } from './auth/config';
import { AuthService } from './auth/auth.service';
import { decodificarJwt } from './auth/jwt';
import { ApiService, Resultado } from './api/api.service';

@Component({
  imports: [RouterOutlet, JsonPipe],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly config = inject(APP_CONFIG);
  protected readonly faltantes = configuracionIncompleta(this.config);
  protected readonly auth = inject(AuthService);

  protected readonly sesionIniciada = signal(this.auth.getTokens() !== null);
  protected readonly error = signal<string | null>(null);
  protected readonly idClaims = signal<Record<string, any> | null>(null);
  protected readonly accessClaims = signal<Record<string, any> | null>(null);
  protected readonly api = inject(ApiService);
  protected readonly resultado = signal<Resultado | null>(null);
  protected readonly cargando = signal(false);

  protected async llamar(fn: () => Promise<Resultado>) {
    this.cargando.set(true);
    this.resultado.set(await fn());
    this.cargando.set(false);
  }

  private mostrarClaims() {
    const t = this.auth.getTokens();
    this.idClaims.set(decodificarJwt(t?.id_token));
    this.accessClaims.set(decodificarJwt(t?.access_token));
  }

  async ngOnInit() {
    try {
      const tokens = await this.auth.procesarRetorno();
      if (tokens) this.sesionIniciada.set(true);
    } catch (e) {
      this.error.set((e as Error).message);
    }
    this.mostrarClaims();
  }
  
}