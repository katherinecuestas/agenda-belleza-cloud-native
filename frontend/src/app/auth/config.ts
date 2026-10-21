import { InjectionToken } from '@angular/core';

export interface AppConfig {
  region: string;
  cognitoDomain: string;
  clientId: string;
  redirectUri: string;
  apiUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export async function cargarConfig(): Promise<AppConfig> {
  // no-store: siempre la versión vigente, nunca una copia vieja del navegador
  const resp = await fetch('/config.json', { cache: 'no-store' });
  if (!resp.ok) {
    throw new Error(`No se pudo leer /config.json (HTTP ${resp.status})`);
  }
  return resp.json();
}

/** Devuelve las claves sin valor. Igual que en la guía 1.2.9b. */
export function configuracionIncompleta(cfg: AppConfig): string[] {
  return Object.entries(cfg).filter(([, valor]) => !valor).map(([clave]) => clave);
}