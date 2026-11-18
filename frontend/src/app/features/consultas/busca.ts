import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ResultadoBusca } from '../../core/models';
import { COMUNS } from '../../shared';

/** Busca por nome de cliente: vendas, datas de pagamento e comissões pagas. */
@Component({
  selector: 'app-busca',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Busca por cliente</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Nome, razão social ou CNPJ</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="buscar()" /></mat-form-field>
        <button mat-flat-button (click)="buscar()"><mat-icon>search</mat-icon> Buscar</button>
      </section>
      @if (r(); as d) {
        <section class="cartao"><h2>Clientes</h2>
          @for (c of d.clientes; track c.id) { <a [routerLink]="['/clientes', c.id]">{{ c.nome }}</a>@if (c.cnpj) { <span class="suave"> {{ c.cnpj }}</span> }{{ $last ? '' : ' · ' }} }
          @empty { <span class="suave">Nenhum.</span> }
        </section>
        <section class="cartao tabela-wrap"><h2>Vendas</h2>
          <table class="tabela responsiva">
            <thead><tr><th>OS</th><th>Data</th><th>Cliente</th><th>Vendedor</th><th>Situação</th><th class="num">Total</th></tr></thead>
            <tbody>@for (o of d.vendas; track o.id) {
              <tr><td><a [routerLink]="['/os', o.id]">{{ o.codigo || o.id }}</a></td><td>{{ o.dataEmissao | dataBr }}</td><td>{{ o.cliente }}</td><td>{{ o.vendedor }}</td>
                <td><span class="badge {{ o.situacao }}">{{ o.situacao }}</span></td><td class="num">{{ o.total | moeda }}</td></tr>
            } @empty { <tr><td colspan="6" class="suave">Nenhuma.</td></tr> }</tbody>
          </table>
        </section>
        <div class="grade-2">
          <section class="cartao tabela-wrap"><h2>Datas de pagamento</h2>
            <table class="tabela responsiva">
              <thead><tr><th>Vencimento</th><th>Cliente</th><th>OS</th><th class="num">Valor</th><th>Status</th></tr></thead>
              <tbody>@for (p of d.datasPagamento; track p.id) {
                <tr><td>{{ p.vencimento | dataBr }}</td><td>{{ p.cliente }}</td><td><a [routerLink]="['/os', p.osId]">{{ p.osCodigo || p.osId }}</a></td>
                  <td class="num">{{ p.valor | moeda }}</td><td><span class="badge {{ p.status }}">{{ p.status }}</span></td></tr>
              } @empty { <tr><td colspan="5" class="suave">Nenhuma.</td></tr> }</tbody>
            </table>
          </section>
          @if (!auth.isProducao()) {
            <section class="cartao tabela-wrap"><h2>Comissões pagas</h2>
              <table class="tabela responsiva">
                <thead><tr><th>Pago em</th><th>Cliente</th><th>OS</th><th>Vendedor</th><th class="num">Valor</th></tr></thead>
                <tbody>@for (c of d.comissoesPagas; track c.id) {
                  <tr><td>{{ c.pagaEm | dataBr }}</td><td>{{ c.cliente }}</td><td><a [routerLink]="['/os', c.osId]">{{ c.osCodigo || c.osId }}</a></td><td>{{ c.vendedor }}</td><td class="num">{{ c.valor | moeda }}</td></tr>
                } @empty { <tr><td colspan="5" class="suave">Nenhuma.</td></tr> }</tbody>
              </table>
            </section>
          }
        </div>
      }
    </div>
  `,
})
export class Busca {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly r = signal<ResultadoBusca | null>(null);
  protected q = '';

  constructor() {
    inject(ActivatedRoute).queryParamMap.subscribe((p) => {
      this.q = p.get('q') ?? '';
      if (this.q) this.api.busca(this.q).subscribe((d) => this.r.set(d));
    });
  }

  buscar(): void {
    this.router.navigate([], { queryParams: { q: this.q } });
  }
}
