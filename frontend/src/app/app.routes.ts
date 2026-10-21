import { Routes } from '@angular/router';
import { grupoGuard } from './auth/guards';
import { InicioComponent } from './paginas/inicio/inicio';
import { MisCitasComponent } from './paginas/mis-citas/mis-citas';
import { PendientesComponent } from './paginas/pendientes/pendientes';
import { IdentidadComponent } from './paginas/identidad/identidad';

export const routes: Routes = [
  // Cognito vuelve aquí con ?code=: el canje ya ocurrió al arrancar la app
  { path: '', component: InicioComponent, pathMatch: 'full', title: 'Agenda Belleza' },
  {
    path: 'mis-citas',
    component: MisCitasComponent,
    canActivate: [grupoGuard('clientes')],
    title: 'Mis citas · Agenda Belleza',
  },
  {
    path: 'pendientes',
    component: PendientesComponent,
    canActivate: [grupoGuard('aprobadores')],
    title: 'Pendientes · Agenda Belleza',
  },
  { path: 'identidad', component: IdentidadComponent, title: 'Identidad · Agenda Belleza' },
  { path: '**', redirectTo: '' },
];
