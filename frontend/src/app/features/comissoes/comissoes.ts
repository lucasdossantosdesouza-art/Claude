import { Component, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ComissoesDoMes, STATUS_COMISSAO, Usuario } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS, hojeIso } from '../../shared';

@Component({
  selector: 'app-comissoes',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Comissões — mês de referência {{ f.mes }}
        @if (d(); as x) { <span class="badge" [class.FECHADA]="x.fechado" [class.PROVISORIA]="!x.fechado">{{ x.fechado ? 'mês fechado' : 'mês aberto (faixa provisória)' }}</span> }
      </h1>
      <p class="suave">O mês de referência é o mês da <b>venda</b>. A faixa (%) é travada no fechamento do mês e a comissão
        é gerada parcela a parcela, conforme os recebimentos, sempre com a faixa do mês da venda.</p>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Mês da venda</mat-label><input matInput type="month" [(ngModel)]="f.mes" (change)="carregar()" /></mat-form-field>
        <mat-form-field><mat-label>Status</mat-label>
          <mat-select [(ngModel)]="f.status" (selectionChange)="carregar()">
            <mat-option value="">Todos</mat-option>
            @for (s of status; track s[0]) { <mat-option [value]="s[0]">{{ s[1] }}</mat-option> }
          </mat-select></mat-form-field>
        @if (auth.isGestor()) {
          <mat-form-field><mat-label>Vendedor</mat-label>
            <mat-select [(ngModel)]="f.vendedorId" (selectionChange)="carregar()">
              <mat-option [value]="null">Todos</mat-option>
              @for (v of vendedores(); track v.id) { <mat-option [value]="v.id">{{ v.nome }}</mat-option> }
            </mat-select></mat-form-field>
          <button mat-stroked-button (click)="exportar('XLSX')"><mat-icon>download</mat-icon> Excel</button>
          <button mat-stroked-button (click)="exportar('PDF')"><mat-icon>download</mat-icon> PDF</button>
        }
      </section>
      @if (d(); as x) {
        <section class="cartao tabela-wrap"><h2>Resumo por vendedor</h2>
          <table class="tabela responsiva">
            <thead><tr><th>Vendedor</th><th class="num">Total vendido</th><th>Faixa</th><th class="num">Fixo</th><th class="num">Autorizada</th>
              <th class="num">Provisória</th><th class="num">Paga</th><th class="num">Pendente receb.</th><th class="num">Bônus semanal</th></tr></thead>
            <tbody>@for (r of x.resumo; track r.vendedorId) {
              <tr><td>{{ r.vendedor }}</td><td class="num">{{ r.totalVendido | moeda }}</td>
                <td><b>{{ r.percentual | pct }}</b> @if (r.travado) { 🔒 }<br /><span class="suave">{{ r.regra }}</span></td>
                <td class="num">{{ r.salarioFixo | moeda }}</td><td class="num vermelho"><b>{{ r.autorizada | moeda }}</b></td>
                <td class="num">{{ r.provisoria | moeda }}</td><td class="num verde">{{ r.paga | moeda }}</td>
                <td class="num">{{ r.pendente | moeda }}</td><td class="num">{{ r.bonusSemanal | moeda }}</td></tr>
            }</tbody>
          </table>
        </section>
        <section class="cartao tabela-wrap"><h2>Detalhe por parcela</h2>
          <table class="tabela responsiva">
            <thead><tr>@if (auth.isGestor()) { <th></th> }<th>Vendedor</th><th>OS</th><th>Cliente</th><th>Parc.</th><th>Vencimento</th>
              <th>Recebida</th><th class="num">Base</th><th class="num">%</th><th class="num">Comissão</th><th>Status</th></tr></thead>
            <tbody>@for (c of x.detalhe; track c.id) {
              <tr [class.linha-vermelha]="c.status === 'AUTORIZADA'">
                @if (auth.isGestor()) { <td>@if (c.status === 'AUTORIZADA') { <mat-checkbox (change)="alternar(c.id, $event.checked)" /> }</td> }
                <td>{{ c.vendedor }}</td><td><a [routerLink]="['/os', c.osId]">{{ c.osCodigo || c.osId }}</a> @if (c.meiaMargem) { ½ }</td>
                <td>{{ c.cliente }}</td><td>{{ c.parcela }}</td><td>{{ c.vencimento | dataBr }}</td><td>{{ c.dataRecebimento | dataBr }}</td>
                <td class="num">{{ c.base | moeda }}</td><td class="num">{{ c.percentual | pct }}</td>
                <td class="num" [class.vermelho]="c.status === 'AUTORIZADA'"><b>{{ c.valor | moeda }}</b></td>
                <td><span class="badge {{ c.status }}">{{ c.status | statusComissao }}</span>@if (c.pagaEm) { <br /><span class="suave">{{ c.pagaEm | dataBr }}</span> }</td></tr>
            } @empty { <tr><td colspan="11" class="suave">Sem comissões neste mês.</td></tr> }</tbody>
          </table>
          @if (auth.isGestor() && x.detalhe.length) {
            <button mat-flat-button class="mt" [disabled]="!selecionadas.size" (click)="pagar()"><mat-icon>paid</mat-icon> Marcar selecionadas como pagas</button>
          }
        </section>
      }
    </div>
  `,
})
export class Comissoes implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly arquivos = inject(Arquivos);
  protected readonly auth = inject(Auth);
  protected readonly d = signal<ComissoesDoMes | null>(null);
  protected readonly vendedores = signal<Usuario[]>([]);
  protected readonly status = Object.entries(STATUS_COMISSAO).filter(([k]) => k !== 'CANCELADA');
  protected f = { mes: hojeIso().substring(0, 7), status: '', vendedorId: null as number | null };
  protected selecionadas = new Set<number>();

  ngOnInit(): void {
    if (this.auth.isGestor()) this.api.vendedores().subscribe((v) => this.vendedores.set(v));
    this.carregar();
  }

  carregar(): void {
    this.selecionadas.clear();
    this.api.comissoes(this.f).subscribe((d) => this.d.set(d));
  }

  alternar(id: number, marcado: boolean): void {
    marcado ? this.selecionadas.add(id) : this.selecionadas.delete(id);
  }

  pagar(): void {
    this.api.pagarComissoes([...this.selecionadas]).subscribe((r) => {
      this.snack.open(`${r.pagas} comissão(ões) paga(s).`, 'OK', { duration: 4000 });
      this.carregar();
    });
  }

  exportar(formato: 'XLSX' | 'PDF'): void {
    this.api.relatorio('comissoes', { mes: this.f.mes, formato })
      .subscribe((r) => this.arquivos.baixar(r, `comissoes_${this.f.mes}.${formato.toLowerCase()}`));
  }
}
