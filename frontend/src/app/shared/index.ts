import { CurrencyPipe, DatePipe } from "@angular/common";
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FORMATOS } from './formatos';
import { Kpi } from './kpi';
import { TabelaResponsiva } from './tabela-responsiva';

/** Imports comuns às telas. */
export const COMUNS = [
  FormsModule, RouterLink, CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
  MatSelectModule, MatCheckboxModule, MatTooltipModule, MatProgressBarModule, Kpi, TabelaResponsiva, ...FORMATOS,
];

export * from './formatos';
