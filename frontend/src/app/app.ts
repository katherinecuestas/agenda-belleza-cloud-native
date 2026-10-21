import { Component, inject } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { APP_CONFIG, configuracionIncompleta } from './auth/config';

@Component({
  imports: [RouterOutlet, JsonPipe],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly config = inject(APP_CONFIG);
  protected readonly faltantes = configuracionIncompleta(this.config);
}