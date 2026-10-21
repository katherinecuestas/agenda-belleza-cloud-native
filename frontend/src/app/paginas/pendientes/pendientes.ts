import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CitaResponse, ReservasService, mensajeDe } from '../../api/reservas.service';
import { fechaHoraChile, horaChile } from '../../compartido/formato';

@Component({
  selector: 'app-pendientes',
  imports: [FormsModule],
  templateUrl: './pendientes.html',
})
export class PendientesComponent implements OnInit {
  private readonly api = inject(ReservasService);

  protected readonly pendientes = signal<CitaResponse[]>([]);
  protected readonly comentarios = signal<Record<number, string>>({});
  protected readonly cargando = signal(true);
  protected readonly procesando = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);
  protected readonly errorCita = signal<{ id: number; mensaje: string } | null>(null);

  protected readonly fechaHora = fechaHoraChile;
  protected readonly hora = horaChile;

  ngOnInit(): Promise<void> {
    return this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.pendientes.set(await this.api.listarPendientes());
    } catch (e) {
      this.error.set(mensajeDe(e));
    } finally {
      this.cargando.set(false);
    }
  }

  protected comentario(id: number): string {
    return this.comentarios()[id] ?? '';
  }

  protected ponerComentario(id: number, texto: string): void {
    this.comentarios.update((actuales) => ({ ...actuales, [id]: texto }));
  }

  protected async resolver(cita: CitaResponse, aprobar: boolean): Promise<void> {
    const comentario = this.comentario(cita.id).trim();
    this.exito.set(null);
    this.errorCita.set(null);

    // El backend también lo exige (409); se avisa antes para no hacer el viaje.
    if (!aprobar && !comentario) {
      this.errorCita.set({ id: cita.id, mensaje: 'Para rechazar, escribe un comentario para la clienta.' });
      return;
    }

    this.procesando.set(cita.id);
    try {
      await this.api.resolver(cita.id, { aprobar, comentario: comentario || null });
      this.exito.set(`Cita de ${cita.servicio} ${aprobar ? 'aprobada' : 'rechazada'}.`);
      this.comentarios.update((actuales) => {
        const copia = { ...actuales };
        delete copia[cita.id];
        return copia;
      });
      await this.cargar();
    } catch (e) {
      this.errorCita.set({ id: cita.id, mensaje: mensajeDe(e) });
    } finally {
      this.procesando.set(null);
    }
  }
}
