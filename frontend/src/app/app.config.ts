import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { SesionService } from './auth/sesion.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Canjea ?code= ANTES de la primera navegación: así los guards ya ven la sesión
    provideAppInitializer(() => inject(SesionService).inicializar()),
  ],
};
