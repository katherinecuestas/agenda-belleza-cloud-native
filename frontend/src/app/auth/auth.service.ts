import { Injectable, inject } from '@angular/core';
import { APP_CONFIG } from './config';
import { aleatorioBase64Url, calcularChallenge } from './pkce';
import { decodificarJwt } from './jwt';

const CLAVE_VERIFIER = 'belleza.pkce_verifier';
const CLAVE_STATE = 'belleza.state';
const CLAVE_NONCE = 'belleza.nonce';
const CLAVE_TOKENS = 'belleza.tokens';

export interface Tokens {
  access_token: string;
  id_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly cfg = inject(APP_CONFIG);
  private readonly scopes = 'openid email profile';

  async login(): Promise<void> {
    const verifier = aleatorioBase64Url();
    const challenge = await calcularChallenge(verifier);
    const state = aleatorioBase64Url(16);
    const nonce = aleatorioBase64Url(16);

    sessionStorage.setItem(CLAVE_VERIFIER, verifier);
    sessionStorage.setItem(CLAVE_STATE, state);
    sessionStorage.setItem(CLAVE_NONCE, nonce);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.cfg.clientId,
      redirect_uri: this.cfg.redirectUri,
      scope: this.scopes,
      state,
      nonce,
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    window.location.assign(`${this.cfg.cognitoDomain}/oauth2/authorize?${params}`);
  }

  /** Si la URL trae ?code=, lo canjea por tokens. Si no, devuelve null. */
  async procesarRetorno(): Promise<Tokens | null> {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const stateRecibido = url.searchParams.get('state');
    const errorCognito = url.searchParams.get('error');

    if (errorCognito) {
      this.limpiarUrl();
      throw new Error(`Cognito respondió: ${errorCognito}`);
    }
    if (!code) return null;

    const verifier = sessionStorage.getItem(CLAVE_VERIFIER);
    const stateGuardado = sessionStorage.getItem(CLAVE_STATE);
    const nonceGuardado = sessionStorage.getItem(CLAVE_NONCE);

    // 1. state: ¿esta respuesta corresponde a un login que YO inicié?
    if (!verifier || !stateGuardado || stateRecibido !== stateGuardado) {
      this.limpiarFlujo();
      this.limpiarUrl();
      throw new Error('El state no coincide: respuesta descartada.');
    }

    // 2. Canje: el code viaja con el verifier. Cognito compara SHA-256(verifier) con el challenge.
    const respuesta = await fetch(`${this.cfg.cognitoDomain}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: this.cfg.clientId,
        code,
        redirect_uri: this.cfg.redirectUri,
        code_verifier: verifier,
      }),
    });
    const datos = await respuesta.json();
    this.limpiarUrl();

    if (!respuesta.ok) {
      this.limpiarFlujo();
      throw new Error(`/token respondió ${respuesta.status}: ${datos.error ?? 'error desconocido'}`);
    }

    // 3. nonce: ¿este id_token se emitió para ESTE login?
    const nonceToken = decodificarJwt(datos.id_token)?.['nonce'];
    this.limpiarFlujo();
    if (!nonceGuardado || nonceToken !== nonceGuardado) {
      throw new Error('El nonce no coincide: token descartado.');
    }

    // 4. Recién aquí se guardan los tokens.
    sessionStorage.setItem(CLAVE_TOKENS, JSON.stringify(datos));
    return datos as Tokens;
  }

  getTokens(): Tokens | null {
    const texto = sessionStorage.getItem(CLAVE_TOKENS);
    return texto ? (JSON.parse(texto) as Tokens) : null;
  }

  /** Cierra la sesión en la app Y en Cognito. Sin /logout, el próximo login entra solo (SSO). */
    logout(): void {
    sessionStorage.removeItem(CLAVE_TOKENS);
    const params = new URLSearchParams({
        client_id: this.cfg.clientId,
        logout_uri: this.cfg.redirectUri,
    });
    window.location.assign(`${this.cfg.cognitoDomain}/logout?${params}`);
    }

  /** verifier, state y nonce son de un solo uso: cumplida su función, se borran. */
  private limpiarFlujo(): void {
    sessionStorage.removeItem(CLAVE_VERIFIER);
    sessionStorage.removeItem(CLAVE_STATE);
    sessionStorage.removeItem(CLAVE_NONCE);
  }

  /** Quita ?code=&state= de la barra, sin recargar la página. */
  private limpiarUrl(): void {
    window.history.replaceState(null, '', '/');
  }
}