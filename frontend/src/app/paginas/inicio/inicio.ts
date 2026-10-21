import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { APP_CONFIG, configuracionIncompleta } from '../../auth/config';
import { SesionService } from '../../auth/sesion.service';

/**
 * Página "/": aquí vuelve Cognito con ?code=. El canje ya ocurrió al arrancar
 * (SesionService.inicializar), así que solo queda decidir a dónde ir.
 */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink],
  templateUrl: './inicio.html',
})
export class InicioComponent implements OnInit {
  protected readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  protected readonly faltantes = configuracionIncompleta(inject(APP_CONFIG));

  ngOnInit(): void {
    if (this.sesion.sesionIniciada() && this.sesion.vigente()) {
      const ruta = this.sesion.rutaInicial();
      if (ruta) this.router.navigateByUrl(ruta, { replaceUrl: true });
    }
  }
}
