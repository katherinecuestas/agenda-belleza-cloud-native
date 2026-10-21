import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CitaRequest,
  CitaResponse,
  EspecialistaResponse,
  ReservasService,
  ServicioResponse,
  mensajeDe,
} from '../../api/reservas.service';
import {
  ahoraEnChileLocal,
  chileAIsoUtc,
  fechaHoraChile,
  horaChile,
  isoAChileLocal,
  pesos,
} from '../../compartido/formato';

@Component({
  selector: 'app-mis-citas',
  imports: [FormsModule],
  templateUrl: './mis-citas.html',
})
export class MisCitasComponent implements OnInit {
  private readonly api = inject(ReservasService);

  protected readonly citas = signal<CitaResponse[]>([]);
  protected readonly servicios = signal<ServicioResponse[]>([]);
  protected readonly especialistas = signal<EspecialistaResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  // Formulario: sirve para reservar y para editar (editandoId distinto de null)
  protected readonly editandoId = signal<number | null>(null);
  protected readonly servicioId = signal<number | null>(null);
  protected readonly especialistaId = signal<number | null>(null);
  protected readonly inicio = signal('');
  protected readonly notas = signal('');
  protected readonly minimo = ahoraEnChileLocal();

  protected readonly serviciosActivos = computed(() => this.servicios().filter((s) => s.activo));
  protected readonly especialistasActivos = computed(() => this.especialistas().filter((e) => e.activo));

  protected readonly fechaHora = fechaHoraChile;
  protected readonly hora = horaChile;
  protected readonly pesos = pesos;

  async ngOnInit(): Promise<void> {
    await Promise.all([this.cargarCatalogo(), this.cargarCitas()]);
  }

  protected async cargarCitas(): Promise<void> {
    this.cargando.set(true);
    try {
      this.citas.set(await this.api.listarMisCitas());
    } catch (e) {
      this.error.set(mensajeDe(e));
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarCatalogo(): Promise<void> {
    try {
      const [servicios, especialistas] = await Promise.all([
        this.api.listarServicios(),
        this.api.listarEspecialistas(),
      ]);
      this.servicios.set(servicios);
      this.especialistas.set(especialistas);
    } catch (e) {
      this.error.set(`No se pudo cargar el catálogo: ${mensajeDe(e)}`);
    }
  }

  protected async guardar(): Promise<void> {
    this.exito.set(null);
    const servicioId = this.servicioId();
    const especialistaId = this.especialistaId();
    const inicio = this.inicio();
    if (servicioId === null || especialistaId === null || !inicio) {
      this.error.set('Elige servicio, especialista, fecha y hora.');
      return;
    }

    const req: CitaRequest = {
      servicioId,
      especialistaId,
      inicio: chileAIsoUtc(inicio),
      notasCliente: this.notas().trim() || null,
    };

    this.guardando.set(true);
    this.error.set(null);
    try {
      const id = this.editandoId();
      if (id === null) {
        await this.api.crearCita(req);
        this.exito.set('Cita reservada. Queda PENDIENTE hasta que el salón la apruebe.');
      } else {
        await this.api.actualizarCita(id, req);
        this.exito.set('Cita actualizada.');
      }
      this.limpiarFormulario();
      await this.cargarCitas();
    } catch (e) {
      this.error.set(mensajeDe(e));
    } finally {
      this.guardando.set(false);
    }
  }

  /** CitaResponse trae nombres, no ids: se buscan en el catálogo para rellenar los select. */
  protected editar(cita: CitaResponse): void {
    this.error.set(null);
    this.exito.set(null);
    this.editandoId.set(cita.id);
    this.servicioId.set(this.serviciosActivos().find((s) => s.nombre === cita.servicio)?.id ?? null);
    this.especialistaId.set(
      this.especialistasActivos().find((e) => e.nombre === cita.especialista)?.id ?? null,
    );
    this.inicio.set(isoAChileLocal(cita.inicio));
    this.notas.set(cita.notasCliente ?? '');
    if (this.servicioId() === null || this.especialistaId() === null) {
      this.error.set('El servicio o el especialista de esta cita ya no está disponible: elige otro.');
    }
  }

  protected async cancelar(cita: CitaResponse): Promise<void> {
    const confirmado = confirm(`¿Cancelar la cita de ${cita.servicio} del ${this.fechaHora(cita.inicio)}?`);
    if (!confirmado) return;

    this.error.set(null);
    this.exito.set(null);
    try {
      await this.api.cancelarCita(cita.id);
      if (this.editandoId() === cita.id) this.limpiarFormulario();
      this.exito.set('Cita cancelada.');
      await this.cargarCitas();
    } catch (e) {
      this.error.set(mensajeDe(e));
    }
  }

  protected limpiarFormulario(): void {
    this.editandoId.set(null);
    this.servicioId.set(null);
    this.especialistaId.set(null);
    this.inicio.set('');
    this.notas.set('');
  }
}
