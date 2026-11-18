import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Aviso, ItemLista, OsResumo } from '../../core/models';
import { COMUNS } from '../../shared';

/** Observações com notificação de urgência: mural geral ou vinculada (OS, produto ou data). */
@Component({
  selector: 'app-avisos',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Avisos e urgências da produção</h1>
      @if (podeEditar()) {
        <section class="cartao">
          <h2>Novo aviso</h2>
          <div class="campos">
            <mat-form-field><mat-label>Formato</mat-label>
              <mat-select [(ngModel)]="novo.tipo">
                <mat-option value="GERAL">Geral (mural para todos os vendedores)</mat-option>
                <mat-option value="VINCULADO">Vinculado (OS, produto ou data)</mat-option>
              </mat-select></mat-form-field>
            <mat-checkbox [(ngModel)]="novo.urgente">Urgente (aparece no topo para todos)</mat-checkbox>
          </div>
          @if (novo.tipo === 'VINCULADO') {
            <div class="campos">
              <mat-form-field><mat-label>OS</mat-label>
                <mat-select [(ngModel)]="novo.osId"><mat-option [value]="null">—</mat-option>
                  @for (o of oss(); track o.id) { <mat-option [value]="o.id">{{ o.codigo || o.id }} — {{ o.cliente }}</mat-option> }
                </mat-select></mat-form-field>
              <mat-form-field><mat-label>Produto</mat-label>
                <mat-select [(ngModel)]="novo.produtoId"><mat-option [value]="null">—</mat-option>
                  @for (p of produtos(); track p.id) { <mat-option [value]="p.id">{{ p.nome }}</mat-option> }
                </mat-select></mat-form-field>
              <mat-form-field><mat-label>Data do calendário</mat-label><input matInput type="date" [(ngModel)]="novo.data" /></mat-form-field>
            </div>
          }
          <mat-form-field style="width: 100%"><mat-label>Mensagem</mat-label><textarea matInput rows="2" [(ngModel)]="novo.texto"></textarea></mat-form-field>
          <button mat-flat-button (click)="publicar()"><mat-icon>campaign</mat-icon> Publicar</button>
        </section>
      }
      <section class="cartao tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Quando</th><th>Formato</th><th>Mensagem</th><th>Vínculo</th><th>Autor</th><th></th></tr></thead>
          <tbody>@for (a of lista(); track a.id) {
            <tr [style.opacity]="a.ativo ? 1 : .5">
              <td>{{ a.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td>
              <td>@if (a.urgente) { <span class="badge urgente">URGENTE</span> } {{ a.tipo === 'GERAL' ? 'Geral' : 'Vinculado' }}</td>
              <td>{{ a.texto }}</td>
              <td>@if (a.osId) { OS <a [routerLink]="['/os', a.osId]">{{ a.osCodigo || a.osId }}</a> } {{ a.produto }} {{ a.data | dataBr }}</td>
              <td>{{ a.autor }}</td>
              <td>@if (a.ativo && podeEditar()) { <button mat-button (click)="arquivar(a.id)">arquivar</button> } @else if (!a.ativo) { arquivado }</td>
            </tr>
          } @empty { <tr><td colspan="6" class="suave">Nenhum aviso.</td></tr> }</tbody>
        </table>
      </section>
    </div>
  `,
})
export class Avisos implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly auth = inject(Auth);
  protected readonly lista = signal<Aviso[]>([]);
  protected readonly produtos = signal<ItemLista[]>([]);
  protected readonly oss = signal<OsResumo[]>([]);
  protected readonly podeEditar = computed(() => this.auth.temPerfil('PRODUCAO', 'GESTOR'));
  protected novo = this.vazio();

  private vazio() {
    return { tipo: 'GERAL' as 'GERAL' | 'VINCULADO', texto: '', urgente: false, osId: null as number | null,
      produtoId: null as number | null, data: '' };
  }

  ngOnInit(): void {
    this.carregar();
    if (this.podeEditar()) {
      this.api.configuracao().subscribe((c) => this.produtos.set(c.produtosProducao.filter((p) => p.ativo)));
      this.api.listarOs({ situacao: 'ABERTA' }).subscribe((l) => this.oss.set(l));
    }
  }

  carregar(): void {
    this.api.avisos().subscribe((l) => this.lista.set(l));
  }

  publicar(): void {
    if (!this.novo.texto.trim()) return;
    this.api.criarAviso({ ...this.novo, data: this.novo.data || null }).subscribe(() => {
      this.snack.open('Aviso publicado.', 'OK', { duration: 3000 });
      this.novo = this.vazio();
      this.carregar();
    });
  }

  arquivar(id: number): void {
    this.api.arquivarAviso(id).subscribe(() => this.carregar());
  }
}
