import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { PainelVendedor, Usuario } from '../../core/models';
import { COMUNS, moeda } from '../../shared';

@Component({
  selector: 'app-painel-vendedor',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <div class="linha entre">
        <h1>Painel do vendedor @if (p(); as d) { — {{ d.vendedor }} · {{ d.mes }} }</h1>
        @if (auth.isGestor()) {
          <mat-form-field><mat-label>Vendedor</mat-label>
            <mat-select [(ngModel)]="vendedorId" (selectionChange)="carregar()">
              @for (v of vendedores(); track v.id) { <mat-option [value]="v.id">{{ v.nome }}</mat-option> }
            </mat-select></mat-form-field>
        } @else {
          <a mat-flat-button routerLink="/os/nova"><mat-icon>note_add</mat-icon> Nova OS</a>
        }
      </div>
      @if (p(); as d) {
        @if (d.atrasadas.length) {
          <div class="alerta erro">⚠ <b>{{ d.atrasadas.length }} parcela(s) em atraso sem comprovante.</b>
            @for (a of d.atrasadas.slice(0, 5); track a.parcelaId) {
              <br />OS <a [routerLink]="['/os', a.osId]">{{ a.osCodigo || a.osId }}</a> — {{ a.cliente }} — {{ a.valor | moeda }}
              venceu em {{ a.vencimento | dataBr }} ({{ a.diasAtraso }} dias)
            }
          </div>
        }
        <div class="grade">
          <app-kpi rotulo="Vendido no mês" [valor]="d.faixa.totalVendido | moeda"
                   [sub]="'Faixa atual: ' + (d.faixa.percentual | pct) + (d.faixa.travado ? ' (travada)' : ' (provisória)')" />
          @if (d.faixa.proximaFaixaValor) {
            <app-kpi rotulo="Falta para a próxima faixa" [valor]="d.faixa.faltaProximaFaixa | moeda"
                     [sub]="'para ' + (d.faixa.proximaFaixaPercentual | pct) + ' (a partir de ' + (d.faixa.proximaFaixaValor | moeda) + ')'">
              <div class="progresso"><div [style.width.%]="progressoFaixa()"></div></div>
            </app-kpi>
          } @else {
            <app-kpi rotulo="Próxima faixa" valor="Faixa máxima ✔" />
          }
          <app-kpi rotulo="Comissão a receber (parcelas já recebidas)" [destaque]="true"
                   [valor]="(d.comissaoAutorizada + d.comissaoProvisoria) | moeda"
                   [sub]="'Autorizada: ' + (d.comissaoAutorizada | moeda) + ' · Provisória (mês aberto): ' + (d.comissaoProvisoria | moeda)" />
          <app-kpi rotulo="Comissão pendente de recebimento" [valor]="d.comissaoPendente | moeda"
                   [sub]="'Gerada conforme as parcelas forem recebidas · Fixo: ' + (d.salarioFixo | moeda)" />
        </div>

        <section class="cartao">
          <h2>Meta da semana ({{ d.metaSemana.semanaInicio | dataBr }} a {{ d.metaSemana.semanaFim | dataBr }})</h2>
          <div class="grade">
            <app-kpi rotulo="Vendido na semana" [valor]="d.metaSemana.total | moeda" />
            <app-kpi rotulo="Bônus atingido" [valor]="d.metaSemana.bonus | moeda" />
            @if (d.metaSemana.proximoPatamar) {
              <app-kpi rotulo="Próximo patamar" [valor]="d.metaSemana.faltaProximo | moeda"
                       [sub]="'faltam para ' + (d.metaSemana.proximoBonus | moeda)">
                <div class="progresso"><div [style.width.%]="progressoMeta()"></div></div>
              </app-kpi>
            } @else { <app-kpi rotulo="Próximo patamar" valor="Máximo atingido ✔" /> }
          </div>
          <a routerLink="/metas">Ver todas as semanas do mês →</a>
        </section>

        <div class="grade-2">
          <section class="cartao">
            <h2>Avisos da produção</h2>
            @for (a of d.avisos; track a.id) {
              <p>@if (a.urgente) { <span class="badge urgente">URGENTE</span> }
                <span class="badge">{{ a.tipo === 'GERAL' ? 'Geral' : 'Vinculado' }}</span> {{ a.texto }}
                <span class="suave">@if (a.produto) { · {{ a.produto }} } @if (a.data) { · {{ a.data | dataBr }} }
                  @if (a.osId) { · OS <a [routerLink]="['/os', a.osId]">{{ a.osCodigo || a.osId }}</a> }</span></p>
            } @empty { <p class="suave">Nenhum aviso.</p> }
          </section>
          <section class="cartao tabela-wrap">
            <h2>Parcelas a vencer</h2>
            <table class="tabela responsiva">
              <thead><tr><th>Vencimento</th><th>Cliente</th><th>Forma</th><th class="num">Valor</th></tr></thead>
              <tbody>
                @for (v of d.aVencer; track v.parcelaId) {
                  <tr><td>{{ v.vencimento | dataBr }}</td><td><a [routerLink]="['/os', v.osId]">{{ v.cliente }}</a></td><td>{{ v.forma }}</td><td class="num">{{ v.valor | moeda }}</td></tr>
                } @empty { <tr><td colspan="4" class="suave">Nada a vencer nos próximos dias.</td></tr> }
              </tbody>
            </table>
          </section>
        </div>
      } @else { <mat-progress-bar mode="indeterminate" /> }
    </div>
  `,
})
export class PainelVendedorPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly p = signal<PainelVendedor | null>(null);
  protected readonly vendedores = signal<Usuario[]>([]);
  protected vendedorId: number | null = null;

  protected readonly progressoFaixa = computed(() => {
    const f = this.p()?.faixa;
    return f?.proximaFaixaValor ? Math.min(100, (f.totalVendido / f.proximaFaixaValor) * 100) : 100;
  });
  protected readonly progressoMeta = computed(() => {
    const m = this.p()?.metaSemana;
    return m?.proximoPatamar ? Math.min(100, (m.total / m.proximoPatamar) * 100) : 100;
  });

  ngOnInit(): void {
    if (this.auth.isGestor()) {
      this.api.vendedores().subscribe((v) => {
        this.vendedores.set(v);
        this.vendedorId = v[0]?.id ?? null;
        this.carregar();
      });
    } else this.carregar();
  }

  carregar(): void {
    this.api.painelVendedor(this.vendedorId).subscribe((d) => this.p.set(d));
  }

  protected readonly moeda = moeda;
}
