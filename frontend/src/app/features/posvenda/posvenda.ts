import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { PainelPosVenda } from '../../core/models';
import { COMUNS, hojeIso } from '../../shared';

@Component({
  selector: 'app-posvenda',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Pós-venda</h1>
      @if (p(); as d) {
        <div class="grade">
          <app-kpi rotulo="Entregas pendentes" [valor]="'' + d.pendentesEntrega.length" />
          <app-kpi rotulo="Pontualidade das entregas" [valor]="pontualidade()" [sub]="d.entreguesNoPrazo + ' de ' + d.entregues + ' no prazo prometido'" />
          <app-kpi rotulo="Ocorrências" [valor]="'' + d.ocorrencias.length" [sub]="status || 'todas'" />
        </div>
        <section class="cartao tabela-wrap"><h2>Aguardando entrega/retirada</h2>
          <table class="tabela responsiva">
            <thead><tr><th>Previsão</th><th>OS</th><th>Cliente</th><th>Vendedor</th><th>Situação</th></tr></thead>
            <tbody>@for (o of d.pendentesEntrega; track o.id) {
              <tr [class.linha-vermelha]="o.previsaoEntrega && o.previsaoEntrega < hoje">
                <td>{{ (o.previsaoEntrega | dataBr) || '—' }} @if (o.previsaoEntrega && o.previsaoEntrega < hoje) { <b class="vermelho">atrasada</b> }</td>
                <td><a [routerLink]="['/os', o.id]" fragment="posvenda">{{ o.codigo || o.id }}</a></td><td>{{ o.cliente }}</td><td>{{ o.vendedor }}</td>
                <td><span class="badge {{ o.situacao }}">{{ o.situacao }}</span></td></tr>
            } @empty { <tr><td colspan="5" class="suave">Nada pendente.</td></tr> }</tbody>
          </table>
        </section>
        <section class="cartao tabela-wrap"><h2>Problemas / reclamações</h2>
          <mat-form-field><mat-label>Status</mat-label>
            <mat-select [(ngModel)]="status" (selectionChange)="carregar()">
              <mat-option value="ABERTA">Abertas</mat-option><mat-option value="RESOLVIDA">Resolvidas</mat-option><mat-option value="">Todas</mat-option>
            </mat-select></mat-form-field>
          <table class="tabela responsiva">
            <thead><tr><th>Data</th><th>OS</th><th>Cliente</th><th>Vendedor</th><th>Tipo</th><th>Descrição</th><th>Status</th></tr></thead>
            <tbody>@for (oc of d.ocorrencias; track oc.id) {
              <tr><td>{{ oc.criadoEm | dataBr }}</td><td><a [routerLink]="['/os', oc.osId]" fragment="posvenda">{{ oc.osCodigo || oc.osId }}</a></td>
                <td>{{ oc.cliente }}</td><td>{{ oc.vendedor }}</td><td>{{ oc.tipo }}</td><td>{{ oc.descricao }}</td>
                <td><span class="badge {{ oc.status }}">{{ oc.status }}</span></td></tr>
            } @empty { <tr><td colspan="7" class="suave">Nenhuma ocorrência.</td></tr> }</tbody>
          </table>
        </section>
      }
    </div>
  `,
})
export class PosVenda implements OnInit {
  private readonly api = inject(Api);
  protected readonly p = signal<PainelPosVenda | null>(null);
  protected readonly hoje = hojeIso();
  protected status = 'ABERTA';
  protected readonly pontualidade = computed(() => {
    const d = this.p();
    return d && d.entregues ? Math.round((d.entreguesNoPrazo / d.entregues) * 100) + '%' : '—';
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.posVenda(this.status).subscribe((d) => this.p.set(d));
  }
}
