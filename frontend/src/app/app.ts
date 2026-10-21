import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { SesionService } from './auth/sesion.service';

/** Códigos que los guards mandan en ?aviso= y el texto que ve la usuaria. */
const AVISOS: Record<string, string> = {
  'sin-permiso': 'No tienes permiso para ver esa sección.',
  'sesion-expirada': 'Tu sesión expiró. Vuelve a iniciar sesión.',
};

/** Cascarón de la app: barra superior, avisos y la página de la ruta activa. */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  protected readonly aviso = signal<string | null>(null);

  /** true mientras corre la navegación que solo quita ?aviso= de la URL. */
  private limpiandoUrl = false;

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => this.alNavegar(e));
  }

  private alNavegar(evento: NavigationEnd): void {
    // La limpieza de la URL no es una navegación de la usuaria: el aviso se queda
    if (this.limpiandoUrl) {
      this.limpiandoUrl = false;
      return;
    }

    // Cualquier otra navegación reemplaza el aviso anterior (o lo borra si no trae uno)
    const codigo = this.router.parseUrl(evento.urlAfterRedirects).queryParamMap.get('aviso');
    this.aviso.set(codigo ? (AVISOS[codigo] ?? null) : null);

    if (codigo) {
      // Sin ?aviso= en la URL, recargar la página no lo vuelve a mostrar
      this.limpiandoUrl = true;
      this.router
        .navigate([], { queryParams: { aviso: null }, queryParamsHandling: 'merge', replaceUrl: true })
        .then((ok) => {
          if (!ok) this.limpiandoUrl = false;
        });
    }
  }
}
