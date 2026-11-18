import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { OsResumo, Usuario } from '../../core/models';
import { COMUNS } from '../../shared';

@Component({
  selector: 'app-os-lista',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <div class="linha entre">
        <h1>Ordens de Serviço</h1>
        @if (!auth.isProducao()) { <a mat-flat-button routerLink="/os/nova"><mat-icon>note_add</mat-icon> Nova OS</a> }
      </div>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Cliente / código / CNPJ</mat-label><input matInput [(ngModel)]="f.q" (keyup.enter)="carregar()" /></mat-form-field>
        <mat-form-field><mat-label>Situação</mat-label>
          <mat-select [(ngModel)]="f.situacao" (selectionChange)="carregar()">
            <mat-option value="">Todas</mat-option><mat-option value="ABERTA">Aberta</mat-option>
            <mat-option value="FECHADA">Fechada</mat-option><mat-option value="CANCELADA">Cancelada</mat-option>
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Mês da venda</mat-label><input matInput type="month" [(ngModel)]="f.mes" (change)="carregar()" /></mat-form-field>
        @if (auth.isGestor()) {
          <mat-form-field><mat-label>Vendedor</mat-label>
            <mat-select [(ngModel)]="f.vendedorId" (selectionChange)="carregar()">
              <mat-option [value]="null">Todos</mat-option>
              @for (v of vendedores(); track v.id) { <mat-option [value]="v.id">{{ v.nome }}</mat-option> }
            </mat-select></mat-form-field>
        }
        <button mat-flat-button (click)="carregar()"><mat-icon>search</mat-icon> Filtrar</button>
      </section>
      <section class="cartao tabela-wrap">
        @if (carregando()) { <mat-progress-bar mode="indeterminate" /> }
        <table class="tabela responsiva">
          <thead><tr><th>OS</th><th>Emissão</th><th>Entrega</th><th>Cliente</th><th>Vendedor</th><th>Situação</th>
            <th class="num">Total</th><th class="num">Recebido</th><th class="num">Comissão a pagar</th></tr></thead>
          <tbody>
            @for (o of lista(); track o.id) {
              <tr [class.linha-vermelha]="o.atrasada">
                <td><a [routerLink]="['/os', o.id]">{{ o.codigo || o.id }}</a> @if (o.meiaMargem) { <span class="badge meia">½ margem</span> }</td>
                <td>{{ o.dataEmissao | dataBr }}</td>
                <td>{{ o.previsaoEntrega | dataBr }} @if (o.entregue) { ✔ }</td>
                <td><a [routerLink]="['/clientes', o.clienteId]">{{ o.cliente }}</a></td>
                <td>{{ o.vendedor }}</td>
                <td><span class="badge {{ o.situacao }}">{{ o.situacao }}</span> @if (o.atrasada) { <span class="badge atraso">atraso</span> }</td>
                <td class="num">{{ o.total | moeda }}</td>
                <td class="num">{{ o.recebido | moeda }}</td>
                <td class="num vermelho">@if (o.comissaoAPagar) { <b>{{ o.comissaoAPagar | moeda }}</b> }</td>
              </tr>
            } @empty { <tr><td colspan="9" class="suave">Nenhuma OS encontrada.</td></tr> }
          </tbody>
        </table>
      </section>
    </div>
  `,
})
export class OsLista implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly lista = signal<OsResumo[]>([]);
  protected readonly vendedores = signal<Usuario[]>([]);
  protected readonly carregando = signal(false);
  protected f = { q: '', situacao: '', mes: '', vendedorId: null as number | null };

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    this.f.situacao = q.get('situacao') ?? '';
  }

  ngOnInit(): void {
    if (this.auth.isGestor()) this.api.vendedores().subscribe((v) => this.vendedores.set(v));
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listarOs(this.f).subscribe((l) => {
      this.lista.set(l);
      this.carregando.set(false);
    });
  }
}
