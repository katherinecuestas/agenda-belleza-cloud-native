import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, Routes, provideRouter } from '@angular/router';
import { App } from './app';
import { APP_CONFIG, AppConfig } from './auth/config';
import { grupoGuard } from './auth/guards';

// main.ts carga config.json antes de arrancar; en el test se entrega a mano
const configPrueba: AppConfig = {
  region: 'us-east-1',
  cognitoDomain: 'https://ejemplo.auth.us-east-1.amazoncognito.com',
  clientId: 'cliente-de-prueba',
  redirectUri: 'http://localhost:4200/',
  apiUrl: 'https://api.ejemplo.test',
};

@Component({ template: 'pagina de prueba' })
class PaginaPrueba {}

// Las rutas reales usan componentes que llaman a la API; aquí basta con el guard real
const rutasPrueba: Routes = [
  { path: '', component: PaginaPrueba },
  { path: 'mis-citas', component: PaginaPrueba, canActivate: [grupoGuard('clientes')] },
  { path: 'pendientes', component: PaginaPrueba, canActivate: [grupoGuard('aprobadores')] },
  { path: 'identidad', component: PaginaPrueba },
];

/** JWT sin firma válida: el front solo lee el payload, igual que decodificarJwt. */
function jwtFalso(claims: Record<string, unknown>): string {
  const base64Url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64Url({ alg: 'none' })}.${base64Url(claims)}.firma`;
}

function iniciarSesionComo(grupos: string[]): void {
  const exp = Math.floor(Date.now() / 1000) + 3600;
  sessionStorage.setItem(
    'belleza.tokens',
    JSON.stringify({
      access_token: jwtFalso({ 'cognito:groups': grupos, exp }),
      id_token: jwtFalso({ email: 'clienta@ejemplo.cl', exp }),
      expires_in: 3600,
      token_type: 'Bearer',
    }),
  );
}

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  async function crear() {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(rutasPrueba),
        provideLocationMocks(),
        { provide: APP_CONFIG, useValue: configPrueba },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, router: TestBed.inject(Router) };
  }

  async function navegar(fixture: Awaited<ReturnType<typeof crear>>['fixture'], url: string) {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
  }

  it('crea la app', async () => {
    const { fixture } = await crear();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('muestra el nombre de la app en la barra', async () => {
    const { fixture } = await crear();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.marca')?.textContent).toContain('Agenda Belleza');
  });

  it('sin sesión no muestra los links ni el botón de cerrar sesión', async () => {
    const { fixture } = await crear();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.navegacion')).toBeNull();
    expect(compiled.textContent).not.toContain('Cerrar sesión');
  });

  describe('aviso de sin permiso', () => {
    beforeEach(() => iniciarSesionComo(['clientes']));

    it('la clienta que entra a /pendientes vuelve a /mis-citas y ve el aviso', async () => {
      const { fixture, router } = await crear();
      await navegar(fixture, '/pendientes');

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.aviso')?.textContent).toContain(
        'No tienes permiso para ver esa sección.',
      );
      // El aviso ya se quitó de la URL: recargar no lo vuelve a mostrar
      expect(router.url).toBe('/mis-citas');
    });

    it('el aviso desaparece en la siguiente navegación', async () => {
      const { fixture } = await crear();
      await navegar(fixture, '/pendientes');
      await navegar(fixture, '/identidad');

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.aviso')).toBeNull();
    });

    it('la X cierra el aviso', async () => {
      const { fixture } = await crear();
      await navegar(fixture, '/pendientes');

      const compiled = fixture.nativeElement as HTMLElement;
      compiled.querySelector<HTMLButtonElement>('button.cerrar')!.click();
      await fixture.whenStable();
      expect(compiled.querySelector('.aviso')).toBeNull();
    });
  });
});
