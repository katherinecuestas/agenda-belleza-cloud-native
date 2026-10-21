import { Component, OnInit, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { APP_CONFIG, configuracionIncompleta } from './auth/config';
import { AuthService } from './auth/auth.service';

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

  async ngOnInit() {
    try {
      const tokens = await this.auth.procesarRetorno();
      if (tokens) this.sesionIniciada.set(true);
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
}