import { Component, effect, inject, input, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ItemLista, OsDetalhe } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS, hojeIso, numeroBr } from '../../shared';

const TIPOS_OCORRENCIA = ['Defeito', 'Troca', 'Retrabalho', 'Insatisfação', 'Atraso', 'Outro'];

@Component({
  selector: 'app-os-detalhe',
  imports: [COMUNS],
  templateUrl: './os-detalhe.html',
  styles: `
    .pre { white-space: pre-line; }
    .receber { display: flex; flex-wrap: wrap; gap: .4rem; align-items: center; }
    .receber input[type=date] { font: inherit; padding: .3rem; border: 1px solid #c8cfda; border-radius: 6px; }
    .cheio { width: 100%; }
  `,
})
export class OsDetalhePage {
  readonly id = input.required<string>();

  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(Auth);
  protected readonly arquivos = inject(Arquivos);

  protected readonly os = signal<OsDetalhe | null>(null);
  protected readonly motivos = signal<ItemLista[]>([]);
  protected readonly ocupado = signal(false);
  protected readonly hoje = hojeIso();
  protected readonly window = window;
  protected readonly tiposOcorrencia = TIPOS_OCORRENCIA;

  protected datasRecebimento: Record<number, string> = {};
  protected comprovantes: Record<number, File | null> = {};
  protected dataEntrega = hojeIso();
  protected ocorrencia = { tipo: 'Defeito', descricao: '' };
  protected cancelamento = { motivo: '', estorno: false, motivoEstornoId: null as number | null, valorEstorno: '' as string | number };
  protected comprovanteEstorno: File | null = null;

  constructor() {
    effect(() => this.carregar(+this.id()));
    this.api.configuracao().subscribe((c) => this.motivos.set(c.motivosEstorno.filter((m) => m.ativo)));
  }

  private carregar(id: number): void {
    this.api.os(id).subscribe((o) => this.atualizar(o));
  }

  private atualizar(o: OsDetalhe): void {
    this.os.set(o);
    this.cancelamento.valorEstorno = o.recebido;
    this.ocupado.set(false);
  }

  protected podeEditar(): boolean {
    return !this.auth.isProducao() && this.os()?.situacao !== 'CANCELADA';
  }

  protected arquivoEscolhido(parcelaId: number, e: Event): void {
    this.comprovantes[parcelaId] = (e.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected receber(parcelaId: number): void {
    const arq = this.comprovantes[parcelaId];
    if (!arq) {
      this.snack.open('Anexe o comprovante do recebimento.', 'OK', { duration: 4000 });
      return;
    }
    this.ocupado.set(true);
    this.api.receberParcela(this.os()!.id, parcelaId, this.datasRecebimento[parcelaId] ?? this.hoje, arq).subscribe({
      next: (o) => {
        this.atualizar(o);
        this.snack.open(o.situacao === 'FECHADA' ? 'Recebimento registrado. Todas as parcelas recebidas: OS fechada.' : 'Recebimento registrado.', 'OK', { duration: 5000 });
      },
      error: () => this.ocupado.set(false),
    });
  }

  protected desfazer(parcelaId: number): void {
    if (!confirm('Desfazer o recebimento desta parcela?')) return;
    this.api.desfazerRecebimento(this.os()!.id, parcelaId).subscribe((o) => this.atualizar(o));
  }

  protected confirmarEntrega(): void {
    this.api.confirmarEntrega(this.os()!.id, this.dataEntrega).subscribe((o) => this.atualizar(o));
  }

  protected registrarOcorrencia(): void {
    if (!this.ocorrencia.descricao.trim()) return;
    this.api.registrarOcorrencia(this.os()!.id, this.ocorrencia.tipo, this.ocorrencia.descricao).subscribe((o) => {
      this.ocorrencia.descricao = '';
      this.atualizar(o);
    });
  }

  protected resolver(id: number): void {
    this.api.resolverOcorrencia(id).subscribe(() => this.carregar(this.os()!.id));
  }

  protected escolherComprovanteEstorno(e: Event): void {
    this.comprovanteEstorno = (e.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected cancelar(): void {
    const c = this.cancelamento;
    if (c.motivo.trim().length < 5) {
      this.snack.open('A justificativa do cancelamento é obrigatória.', 'OK', { duration: 4000 });
      return;
    }
    if (c.estorno && (!c.motivoEstornoId || !this.comprovanteEstorno)) {
      this.snack.open('Para estorno, selecione o motivo e anexe o comprovante.', 'OK', { duration: 5000 });
      return;
    }
    if (!confirm('Confirmar o cancelamento desta OS?')) return;
    this.api.cancelarOs(this.os()!.id, { motivo: c.motivo, estorno: c.estorno, motivoEstornoId: c.motivoEstornoId,
      valorEstorno: c.estorno ? numeroBr(c.valorEstorno) : null }, c.estorno ? this.comprovanteEstorno : null)
      .subscribe((o) => this.atualizar(o));
  }
}
