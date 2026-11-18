import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { DiaCalendario, ItemLista } from '../../core/models';
import { COMUNS, dataBr, hojeIso } from '../../shared';

/** Calendário visual: o Chefe de Produção bloqueia produtos por dia (vermelho); os vendedores consultam. */
@Component({
  selector: 'app-calendario',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <div class="linha entre">
        <h1>Calendário de produção — {{ mes() }}</h1>
        <div class="linha">
          <button mat-icon-button (click)="navegar(-1)" aria-label="Mês anterior"><mat-icon>chevron_left</mat-icon></button>
          <input type="month" [value]="mes()" (change)="irPara($any($event.target).value)" class="mes" />
          <button mat-icon-button (click)="navegar(1)" aria-label="Próximo mês"><mat-icon>chevron_right</mat-icon></button>
        </div>
      </div>
      <p class="suave">Em <span class="bloq inline">vermelho</span>: produção sem capacidade no dia.
        @if (podeEditar()) { Clique em um dia para marcar ou desmarcar bloqueios. } @else { Consulte antes de definir a data de entrega da OS. }</p>
      <section class="cartao">
        <div class="cal">
          @for (d of ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']; track d) { <div class="cab">{{ d }}</div> }
          @for (dia of grade(); track $index) {
            @if (dia) {
              <div class="dia" [class.hoje]="dia.data === hoje" [class.editavel]="podeEditar()" [class.selecionado]="selecionado()?.data === dia.data"
                   (click)="selecionar(dia)">
                <span class="n">{{ +dia.data.substring(8) }}</span>
                @if (dia.entregas) { <span class="ent">· {{ dia.entregas }} entrega(s)</span> }
                @for (b of dia.bloqueios; track b.produtoId) { <span class="bloq">{{ b.produto }}</span> }
                @for (a of dia.avisos; track a.id) { <span class="av">{{ a.urgente ? '⚠ ' : '' }}{{ a.texto.substring(0, 40) }}</span> }
              </div>
            } @else { <div class="dia vazio"></div> }
          }
        </div>
      </section>
      @if (selecionado(); as s) {
        @if (podeEditar()) {
          <section class="cartao">
            <h2>Bloqueios em {{ dataBr(s.data) }}</h2>
            <div class="linha">
              @for (p of produtos(); track p.id) {
                <mat-checkbox [checked]="marcados.has(p.id)" (change)="alternar(p.id, $event.checked)">{{ p.nome }}</mat-checkbox>
              }
            </div>
            <mat-form-field class="cheio"><mat-label>Observação</mat-label><input matInput [(ngModel)]="observacao" /></mat-form-field>
            <div class="linha">
              <button mat-flat-button (click)="salvar()"><mat-icon>save</mat-icon> Salvar</button>
              <a mat-button routerLink="/avisos">Criar aviso para esta data</a>
            </div>
          </section>
        } @else if (s.bloqueios.length || s.avisos.length) {
          <section class="cartao"><h2>{{ dataBr(s.data) }}</h2>
            @for (b of s.bloqueios; track b.produtoId) { <p class="vermelho">⛔ {{ b.produto }} @if (b.observacao) { — {{ b.observacao }} }</p> }
            @for (a of s.avisos; track a.id) { <p class="amarelo">⚠ {{ a.texto }}</p> }
          </section>
        }
      }
    </div>
  `,
  styles: `
    .mes { font: inherit; padding: .4rem; border: 1px solid #c8cfda; border-radius: 6px; }
    .cal { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
    .cab { font-weight: 700; text-align: center; font-size: .8rem; color: var(--suave); padding: .3rem 0; }
    .dia { background: #fff; border: 1px solid var(--borda); border-radius: 8px; min-height: 92px; padding: .3rem; font-size: .78rem; }
    .dia.vazio { background: transparent; border: 0; }
    .dia.hoje { outline: 2px solid #2e75b6; }
    .dia.editavel { cursor: pointer; }
    .dia.selecionado { background: #eff6ff; }
    .n { font-weight: 700; font-size: .9rem; }
    .ent { color: var(--suave); font-size: .7rem; }
    .bloq { display: block; background: var(--vermelho); color: #fff; border-radius: 4px; padding: 1px 4px; margin-top: 2px; font-size: .72rem; }
    .bloq.inline { display: inline; }
    .av { display: block; background: #fde68a; color: #7c2d12; border-radius: 4px; padding: 1px 4px; margin-top: 2px; font-size: .72rem; }
    .cheio { width: 100%; margin-top: .6rem; }
    @media (max-width: 760px) { .dia { min-height: 62px; font-size: .66rem; } .bloq, .av { font-size: .6rem; } }
  `,
})
export class Calendario implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly auth = inject(Auth);
  protected readonly dataBr = dataBr;
  protected readonly hoje = hojeIso();
  protected readonly mes = signal(hojeIso().substring(0, 7));
  protected readonly dias = signal<DiaCalendario[]>([]);
  protected readonly produtos = signal<ItemLista[]>([]);
  protected readonly selecionado = signal<DiaCalendario | null>(null);
  protected readonly podeEditar = computed(() => this.auth.temPerfil('PRODUCAO', 'GESTOR'));
  protected marcados = new Set<number>();
  protected observacao = '';

  /** Dias do mês alinhados à semana (segunda a domingo). */
  protected readonly grade = computed<(DiaCalendario | null)[]>(() => {
    const d = this.dias();
    if (!d.length) return [];
    const primeiro = (new Date(d[0].data + 'T12:00:00').getDay() + 6) % 7;
    const g: (DiaCalendario | null)[] = [...Array(primeiro).fill(null), ...d];
    while (g.length % 7) g.push(null);
    return g;
  });

  ngOnInit(): void {
    this.api.configuracao().subscribe((c) => this.produtos.set(c.produtosProducao.filter((p) => p.ativo)));
    this.carregar();
  }

  carregar(): void {
    this.api.calendario(this.mes()).subscribe((d) => this.dias.set(d));
  }

  navegar(delta: number): void {
    const [a, m] = this.mes().split('-').map(Number);
    const d = new Date(a, m - 1 + delta, 1);
    this.irPara(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }

  irPara(mes: string): void {
    this.mes.set(mes);
    this.selecionado.set(null);
    this.carregar();
  }

  selecionar(dia: DiaCalendario): void {
    this.selecionado.set(dia);
    this.marcados = new Set(dia.bloqueios.map((b) => b.produtoId));
    this.observacao = dia.bloqueios[0]?.observacao ?? '';
  }

  alternar(id: number, marcado: boolean): void {
    marcado ? this.marcados.add(id) : this.marcados.delete(id);
  }

  salvar(): void {
    const s = this.selecionado()!;
    this.api.definirBloqueios(s.data, [...this.marcados], this.observacao || null).subscribe(() => {
      this.snack.open(`Calendário de ${dataBr(s.data)} atualizado.`, 'OK', { duration: 3000 });
      this.selecionado.set(null);
      this.carregar();
    });
  }
}
