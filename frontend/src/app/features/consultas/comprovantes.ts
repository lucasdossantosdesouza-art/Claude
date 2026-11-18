import { Component, inject, OnInit, signal } from '@angular/core';
import { Api } from '../../core/api';
import { Comprovante } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS } from '../../shared';

const TIPOS = { RECEBIMENTO: 'Recebimento', ESTORNO: 'Estorno', DOCUMENTO_OS: 'Documento da OS' };

@Component({
  selector: 'app-comprovantes',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Comprovantes anexados</h1>
      <section class="cartao filtros">
        <mat-form-field><mat-label>Cliente ou OS</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="carregar()" /></mat-form-field>
        <button mat-flat-button (click)="carregar()"><mat-icon>search</mat-icon> Buscar</button>
      </section>
      <section class="cartao tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Tipo</th><th>Data</th><th>OS</th><th>Cliente</th><th>Vendedor</th><th>Parcela</th><th class="num">Valor</th><th class="num">Comissão</th><th>Arquivo</th></tr></thead>
          <tbody>@for (c of lista(); track c.arquivo) {
            <tr [class.linha-vermelha]="c.statusComissao === 'AUTORIZADA' || c.statusComissao === 'PROVISORIA'">
              <td>{{ tipos[c.tipo] }}</td><td>{{ c.data | dataBr }}</td><td><a [routerLink]="['/os', c.osId]">{{ c.osCodigo || c.osId }}</a></td>
              <td>{{ c.cliente }}</td><td>{{ c.vendedor }}</td><td>@if (c.parcela) { {{ c.parcela }} · {{ c.forma }} }</td>
              <td class="num">{{ c.valor | moeda }}</td>
              <td class="num">@if (c.statusComissao) { <span [class.vermelho]="c.statusComissao === 'AUTORIZADA'">{{ c.comissao | moeda }}</span><br /><span class="badge {{ c.statusComissao }}">{{ c.statusComissao | statusComissao }}</span> }</td>
              <td><a (click)="arquivos.abrir(c.arquivo)"><mat-icon inline>open_in_new</mat-icon> abrir</a></td></tr>
          } @empty { <tr><td colspan="9" class="suave">Nenhum comprovante.</td></tr> }</tbody>
        </table>
      </section>
    </div>
  `,
})
export class Comprovantes implements OnInit {
  private readonly api = inject(Api);
  protected readonly arquivos = inject(Arquivos);
  protected readonly lista = signal<Comprovante[]>([]);
  protected readonly tipos = TIPOS;
  protected q = '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.comprovantes(this.q).subscribe((l) => this.lista.set(l));
  }
}
