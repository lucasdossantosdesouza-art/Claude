import { Component, effect, inject, input, signal } from '@angular/core';
import { Api } from '../../core/api';
import { HistoricoCliente } from '../../core/models';
import { COMUNS } from '../../shared';

/** Histórico do cliente: compras, datas de pagamento, pós-venda e comissões pagas. */
@Component({
  selector: 'app-cliente-historico',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      @if (h(); as d) {
        <h1>{{ d.cliente.nome }}</h1>
        <p class="suave">{{ d.cliente.razaoSocial }} · CNPJ/CPF {{ d.cliente.cnpj || '—' }} · Tel. {{ d.cliente.telefone || '—' }} ·
          {{ d.cliente.cidade }}@if (d.cliente.uf) {/{{ d.cliente.uf }}}</p>
        <div class="grade">
          <app-kpi rotulo="Total comprado" [valor]="d.totalComprado | moeda" [sub]="d.compras.length + ' OS'" />
          <app-kpi rotulo="Em aberto" [valor]="d.emAberto | moeda" />
          <app-kpi rotulo="Ocorrências de pós-venda" [valor]="'' + d.ocorrencias.length" />
        </div>
        <section class="cartao tabela-wrap"><h2>Histórico de compras</h2>
          <table class="tabela responsiva">
            <thead><tr><th>OS</th><th>Data</th><th>Vendedor</th><th>Situação</th><th>Entrega</th><th class="num">Total</th><th class="num">Recebido</th></tr></thead>
            <tbody>@for (o of d.compras; track o.id) {
              <tr><td><a [routerLink]="['/os', o.id]">{{ o.codigo || o.id }}</a></td><td>{{ o.dataEmissao | dataBr }}</td><td>{{ o.vendedor }}</td>
                <td><span class="badge {{ o.situacao }}">{{ o.situacao }}</span></td>
                <td>@if (o.entregue) { ✔ entregue } @else if (o.previsaoEntrega) { prev. {{ o.previsaoEntrega | dataBr }} }</td>
                <td class="num">{{ o.total | moeda }}</td><td class="num">{{ o.recebido | moeda }}</td></tr>
            }</tbody>
          </table>
        </section>
        <div class="grade-2">
          <section class="cartao tabela-wrap"><h2>Datas de pagamento</h2>
            <table class="tabela responsiva">
              <thead><tr><th>Vencimento</th><th>OS</th><th>Forma</th><th class="num">Valor</th><th>Status</th></tr></thead>
              <tbody>@for (p of d.parcelas; track p.id) {
                <tr><td>{{ p.vencimento | dataBr }}</td><td>{{ p.osCodigo || p.osId }}</td><td>{{ p.forma }}</td><td class="num">{{ p.valor | moeda }}</td>
                  <td><span class="badge {{ p.status }}">{{ p.status }}</span> {{ p.dataRecebimento | dataBr }}</td></tr>
              }</tbody>
            </table>
          </section>
          <section class="cartao"><h2>Pós-venda</h2>
            @for (oc of d.ocorrencias; track oc.id) {
              <p><span class="badge {{ oc.status }}">{{ oc.status }}</span> <b>{{ oc.tipo }}</b> (OS {{ oc.osCodigo || oc.osId }}) — {{ oc.descricao }}
                <span class="suave">{{ oc.criadoEm | dataBr }}</span></p>
            } @empty { <p class="suave">Sem ocorrências.</p> }
            @if (d.comissoesPagas.length) {
              <h3>Comissões pagas</h3>
              @for (c of d.comissoesPagas; track c.id) { <p>OS {{ c.osCodigo || c.osId }}: {{ c.valor | moeda }} em {{ c.pagaEm | dataBr }}</p> }
            }
          </section>
        </div>
      } @else { <mat-progress-bar mode="indeterminate" /> }
    </div>
  `,
})
export class ClienteHistorico {
  readonly id = input.required<string>();
  private readonly api = inject(Api);
  protected readonly h = signal<HistoricoCliente | null>(null);

  constructor() {
    effect(() => this.api.historicoCliente(+this.id()).subscribe((d) => this.h.set(d)));
  }
}
