import { Component, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { LogAlteracao } from '../../core/models';
import { COMUNS } from '../../shared';

@Component({
  selector: 'app-auditoria',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Histórico / log de alterações</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Filtrar</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="carregar()" /></mat-form-field>
        <button mat-flat-button (click)="carregar()"><mat-icon>search</mat-icon> Filtrar</button>
      </section>
      <section class="cartao tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Quando</th><th>Quem</th><th>OS</th><th>Ação</th><th>Detalhe</th></tr></thead>
          <tbody>@for (l of lista(); track l.id) {
            <tr><td>{{ l.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td><td>{{ l.usuario }}</td>
              <td>@if (l.osId) { <a [routerLink]="['/os', l.osId]">{{ l.osId }}</a> }</td><td>{{ l.acao }}</td><td>{{ l.detalhe }}</td></tr>
          }</tbody>
        </table>
      </section>
    </div>
  `,
})
export class Auditoria implements OnInit {
  private readonly api = inject(Api);
  protected readonly lista = signal<LogAlteracao[]>([]);
  protected q = '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.auditoria(this.q).subscribe((l) => this.lista.set(l));
  }
}
