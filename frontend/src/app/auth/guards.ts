import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Grupo, SesionService } from './sesion.service';

/**
 * Deja pasar solo a quien pertenece al grupo. Es una ayuda de navegación:
 * la autorización real la hacen el API Gateway (scopes) y Spring (roles).
 *
 * El motivo del rechazo viaja como ?aviso=<código>; la barra superior (App)
 * lo traduce a un mensaje y lo quita de la URL.
 */
export function grupoGuard(grupo: Grupo): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);

    if (!sesion.sesionIniciada()) {
      return router.createUrlTree(['/']);
    }
    if (!sesion.vigente()) {
      return router.createUrlTree(['/'], { queryParams: { aviso: 'sesion-expirada' } });
    }
    if (!sesion.tieneGrupo(grupo)) {
      // Directo a su propia pantalla: pasar por "/" haría una segunda redirección
      return router.createUrlTree([sesion.rutaInicial() ?? '/'], {
        queryParams: { aviso: 'sin-permiso' },
      });
    }
    return true;
  };
}
