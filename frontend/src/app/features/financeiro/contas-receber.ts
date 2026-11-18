import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ParcelaReceber } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS } from '../../shared';

@Component({
  selector: 'app-contas-receber',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Contas a receber</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>De</mat-label><input matInput type="date" [(ngModel)]="f.inicio" /></mat-form-field>
        <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [(ngModel)]="f.fim" /></mat-form-field>
        <mat-form-field><mat-label>Cliente</mat-label><input matInput [(ngModel)]="f.cliente" (keyup.enter)="carregar()" /></mat-form-field>
        <button mat-flat-button (click)="carregar()"><mat-icon>search</mat-icon> Filtrar</button>
        @if (auth.isGestor()) {
          <button mat-stroked-button (click)="exportar('XLSX')"><mat-icon>download</mat-icon> Excel</button>
          <button mat-stroked-button (click)="exportar('PDF')"><mat-icon>download</mat-icon> PDF</button>
        }
      </section>
      <div class="grade">
        <app-kpi rotulo="Total no período" [valor]="total() | moeda" [sub]="linhas().length + ' parcela(s)'" />
        <app-kpi rotulo="Em atraso" [valor]="atrasado() | moeda" [destaque]="atrasado() > 0" />
      </div>
      <section class="cartao tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Vencimento</th><th>Cliente</th><th>OS</th><th>Parcela</th><th>Forma</th><th>Vendedor</th><th class="num">Valor</th></tr></thead>
          <tbody>
            @for (p of linhas(); track p.parcelaId) {
              <tr [class.linha-vermelha]="p.atrasada">
                <td>{{ p.vencimento | dataBr }} @if (p.atrasada) { <b class="vermelho">{{ p.diasAtraso }}d atraso</b> }</td>
                <td>{{ p.cliente }}</td><td><a [routerLink]="['/os', p.osId]">{{ p.osCodigo || p.osId }}</a></td>
                <td>{{ p.numero }}</td><td>{{ p.forma }}</td><td>{{ p.vendedor }}</td><td class="num">{{ p.valor | moeda }}</td>
              </tr>
            } @empty { <tr><td colspan="7" class="suave">Nada a receber no período.</td></tr> }
          </tbody>
        </table>
      </section>
    </div>
  `,
})
export class ContasReceber implements OnInit {
  private readonly api = inject(Api);
  private readonly arquivos = inject(Arquivos);
  protected readonly auth = inject(Auth);
  protected readonly linhas = signal<ParcelaReceber[]>([]);
  protected readonly total = computed(() => this.linhas().reduce((s, p) => s + p.valor, 0));
  protected readonly atrasado = computed(() => this.linhas().filter((p) => p.atrasada).reduce((s, p) => s + p.valor, 0));
  protected f = { inicio: '', fim: '', cliente: '' };

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    this.f.inicio = q.get('inicio') ?? '';
    const fim = q.get('fim') ?? '';
    // "AAAA-MM-31" vindo do painel: ajusta para o último dia real do mês
    this.f.fim = fim.endsWith('-31') ? new Date(+fim.substring(0, 4), +fim.substring(5, 7), 0).toISOString().substring(0, 10) : fim;
  }

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.contasReceber(this.f).subscribe((l) => this.linhas.set(l));
  }

  exportar(formato: 'XLSX' | 'PDF'): void {
    this.api.relatorio('contas-receber', { formato, inicio: this.f.inicio, fim: this.f.fim })
      .subscribe((r) => this.arquivos.baixar(r, 'contas_a_receber.' + formato.toLowerCase()));
  }
}
