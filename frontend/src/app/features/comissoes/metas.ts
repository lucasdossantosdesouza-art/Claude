import { Component, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Faixa, MetasVendedor } from '../../core/models';
import { COMUNS, hojeIso } from '../../shared';

@Component({
  selector: 'app-metas',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Meta da semana</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Mês</mat-label><input matInput type="month" [(ngModel)]="mes" (change)="carregar()" /></mat-form-field>
        <span class="suave">Bônus fixo por patamar de venda semanal, apurado por vendedor e pago separado da comissão mensal.
          Patamares: @for (f of faixas(); track f.valorMinimo) { a partir de {{ f.valorMinimo | moeda }} → {{ f.valor | moeda }}{{ $last ? '' : '; ' }} }</span>
      </section>
      @for (v of dados(); track v.vendedorId) {
        <section class="cartao tabela-wrap"><h2>{{ v.vendedor }}</h2>
          <table class="tabela responsiva">
            <thead><tr><th>Semana</th><th class="num">Vendido</th><th class="num">Bônus</th><th>Próximo patamar</th><th>Status</th></tr></thead>
            <tbody>@for (s of v.semanas; track s.semanaInicio) {
              <tr><td>{{ s.semanaInicio | dataBr }} a {{ s.semanaFim | dataBr }}</td><td class="num">{{ s.total | moeda }}</td>
                <td class="num" [class.verde]="s.bonus > 0"><b>{{ s.bonus | moeda }}</b></td>
                <td>@if (s.proximoPatamar) { faltam {{ s.faltaProximo | moeda }} p/ {{ s.proximoBonus | moeda }} } @else { máximo }</td>
                <td>
                  @if (s.paga) { <span class="badge PAGA">paga</span> }
                  @else if (s.bonus > 0 && s.encerrada && auth.isGestor()) { <button mat-stroked-button (click)="pagar(v.vendedorId, s.semanaInicio)">Marcar como paga</button> }
                  @else if (s.bonus > 0 && s.encerrada) { <span class="badge AUTORIZADA">a pagar</span> }
                  @else if (!s.encerrada) { <span class="badge">em andamento</span> } @else { — }
                </td></tr>
            }</tbody>
          </table>
        </section>
      }
    </div>
  `,
})
export class Metas implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly dados = signal<MetasVendedor[]>([]);
  protected readonly faixas = signal<Faixa[]>([]);
  protected mes = hojeIso().substring(0, 7);

  ngOnInit(): void {
    this.api.configuracao().subscribe((c) => this.faixas.set(c.faixasMetaSemanal.faixas));
    this.carregar();
  }

  carregar(): void {
    this.api.metas(this.mes).subscribe((d) => this.dados.set(d));
  }

  pagar(vendedorId: number, semana: string): void {
    this.api.pagarMeta(vendedorId, semana).subscribe(() => this.carregar());
  }
}
