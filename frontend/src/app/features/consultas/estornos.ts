import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { ItemLista, PainelEstornos } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS } from '../../shared';

/** Pesquisa de cancelamentos/estornos e indicador dos principais motivos. */
@Component({
  selector: 'app-estornos',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Cancelamentos e estornos</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Cliente / justificativa / OS</mat-label><input matInput [(ngModel)]="f.q" (keyup.enter)="carregar()" /></mat-form-field>
        <mat-form-field><mat-label>Motivo do estorno</mat-label>
          <mat-select [(ngModel)]="f.motivoId" (selectionChange)="carregar()"><mat-option [value]="null">Todos</mat-option>
            @for (m of motivos(); track m.id) { <mat-option [value]="m.id">{{ m.nome }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>De</mat-label><input matInput type="date" [(ngModel)]="f.inicio" /></mat-form-field>
        <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [(ngModel)]="f.fim" /></mat-form-field>
        <button mat-flat-button (click)="carregar()"><mat-icon>search</mat-icon> Pesquisar</button>
      </section>
      @if (p(); as d) {
        <section class="cartao"><h2>Indicador — principais motivos</h2>
          @for (i of d.indicador; track i.motivo) {
            <div class="barra"><span class="nome">{{ i.motivo }}</span>
              <span class="b" [style.width.%]="(i.quantidade / maximo()) * 60"></span>
              <span><b>{{ i.quantidade }}</b> · {{ i.valor | moeda }}</span></div>
          } @empty { <p class="suave">Sem cancelamentos no filtro.</p> }
        </section>
        <section class="cartao tabela-wrap">
          <table class="tabela responsiva">
            <thead><tr><th>Data</th><th>OS</th><th>Cliente</th><th>Vendedor</th><th>Justificativa</th><th>Motivo estorno</th><th class="num">Valor estornado</th><th>Comprovante</th></tr></thead>
            <tbody>@for (e of d.lista; track e.osId) {
              <tr><td>{{ e.cancelData | dataBr }}</td><td><a [routerLink]="['/os', e.osId]">{{ e.osCodigo || e.osId }}</a></td><td>{{ e.cliente }}</td>
                <td>{{ e.vendedor }}</td><td>{{ e.justificativa }}</td><td>{{ e.motivo || '—' }}</td>
                <td class="num">@if (e.estorno) { {{ e.valorEstornado | moeda }} }</td>
                <td>@if (e.comprovante) { <a (click)="arquivos.abrir(e.comprovante)"><mat-icon inline>attach_file</mat-icon></a> }</td></tr>
            } @empty { <tr><td colspan="8" class="suave">Nenhum registro.</td></tr> }</tbody>
          </table>
        </section>
      }
    </div>
  `,
  styles: `
    .barra { display: flex; align-items: center; gap: .5rem; margin: .3rem 0; font-size: .88rem; }
    .barra .nome { width: 200px; flex-shrink: 0; }
    .barra .b { background: var(--vermelho); height: 16px; border-radius: 4px; min-width: 2px; }
  `,
})
export class Estornos implements OnInit {
  private readonly api = inject(Api);
  protected readonly arquivos = inject(Arquivos);
  protected readonly p = signal<PainelEstornos | null>(null);
  protected readonly motivos = signal<ItemLista[]>([]);
  protected readonly maximo = computed(() => Math.max(1, ...(this.p()?.indicador.map((i) => i.quantidade) ?? [1])));
  protected f = { q: '', motivoId: null as number | null, inicio: '', fim: '' };

  ngOnInit(): void {
    this.api.configuracao().subscribe((c) => this.motivos.set(c.motivosEstorno));
    this.carregar();
  }

  carregar(): void {
    this.api.estornos(this.f).subscribe((d) => this.p.set(d));
  }
}
