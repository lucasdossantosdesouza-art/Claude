import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { debounceTime, Subject, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Cliente, DiaCalendario, Item, OsRequest, ResultadoImportacao, Usuario } from '../../core/models';
import { COMUNS, hojeIso, moeda, numeroBr, somarDias } from '../../shared';

interface ParcelaLinha {
  id: number | null;
  forma: string;
  valor: number | string;
  vencimento: string;
  recebida: boolean;
  importada?: boolean;
}

interface ItemLinha extends Item {
  importado?: boolean;
}

const METODOS: Record<ResultadoImportacao['metodo'], string> = {
  IA: 'IA (Claude)',
  PDF_TEXTO: 'leitura do PDF (layout SAFI)',
  PDF_OCR: 'OCR do PDF digitalizado',
  OCR: 'OCR da foto',
};

/** Criação/edição de OS, com preenchimento automático a partir do documento (PDF/foto). */
@Component({
  selector: 'app-os-form',
  imports: [COMUNS],
  templateUrl: './os-form.html',
  styleUrl: './os-form.scss',
})
export class OsForm implements OnInit {
  readonly id = input<string>();

  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(Auth);

  protected readonly editando = computed(() => !!this.id());
  protected readonly salvando = signal(false);
  protected readonly lendo = signal(false);
  protected readonly importacao = signal<ResultadoImportacao | null>(null);
  protected readonly erroImportacao = signal<string | null>(null);
  protected readonly preenchidos = signal<Set<string>>(new Set());
  protected readonly disponibilidade = signal<DiaCalendario | null>(null);
  protected readonly sugestoesClientes = signal<Cliente[]>([]);
  protected readonly formas = signal<string[]>([]);
  protected readonly vendedores = signal<Usuario[]>([]);
  protected readonly capacidades = signal<{ ia: boolean; ocr: boolean } | null>(null);
  protected arrastando = false;

  // estado do formulário
  protected os = {
    codigo: '', vendedorId: null as number | null, dataEmissao: hojeIso(), hora: '', previsaoEntrega: '',
    servicos: '', observacoes: '', desconto: 0 as number | string, total: '' as number | string,
    prazoPagamentoDias: null as number | null, meiaMargem: false, documentoOrigem: null as string | null,
  };
  protected cliente: Cliente = { id: null, nome: '' };
  protected readonly itens = signal<ItemLinha[]>([]);
  protected readonly parcelas = signal<ParcelaLinha[]>([]);
  private totalManual = false;
  private readonly buscaCliente$ = new Subject<string>();

  ngOnInit(): void {
    this.api.configuracao().subscribe((c) => this.formas.set(c.formasPagamento.filter((f) => f.ativo).map((f) => f.nome)));
    if (this.auth.isGestor()) this.api.vendedores().subscribe((v) => this.vendedores.set(v));
    this.api.capacidadesLeitura().subscribe((c) => this.capacidades.set(c));
    this.buscaCliente$.pipe(debounceTime(250), switchMap((q) => this.api.clientes(q)))
      .subscribe((c) => this.sugestoesClientes.set(c));

    const id = this.id();
    if (id) {
      this.api.os(+id).subscribe((o) => {
        this.os = {
          codigo: o.codigo ?? '', vendedorId: o.vendedorId, dataEmissao: o.dataEmissao, hora: o.hora ?? '',
          previsaoEntrega: o.previsaoEntrega ?? '', servicos: o.servicos ?? '', observacoes: o.observacoes ?? '',
          desconto: o.desconto, total: o.total, prazoPagamentoDias: o.prazoPagamentoDias, meiaMargem: o.meiaMargem,
          documentoOrigem: null,
        };
        this.cliente = { ...o.cliente };
        this.itens.set(o.itens.map((i) => ({ ...i })));
        this.parcelas.set(o.parcelas.filter((p) => p.status !== 'CANCELADA').map((p) => ({
          id: p.id, forma: p.forma, valor: p.valor, vencimento: p.vencimento, recebida: p.status === 'RECEBIDA',
        })));
        this.totalManual = true;
        this.verificarEntrega();
      });
    } else {
      this.adicionarItem();
      this.adicionarParcela('Pix');
    }
  }

  // ------------------------------------------------------------ itens e totais

  adicionarItem(): void {
    this.itens.update((l) => [...l, { tipo: 'PRODUTO', codigo: '', descricao: '', unidade: 'UN', quantidade: 1, valorUnitario: 0, desconto: 0, valorTotal: 0 }]);
  }

  removerItem(i: number): void {
    this.itens.update((l) => l.filter((_, idx) => idx !== i));
    this.recalcular();
  }

  calcularItem(it: ItemLinha): void {
    it.valorTotal = +(numeroBr(it.quantidade) * numeroBr(it.valorUnitario) - numeroBr(it.desconto)).toFixed(2);
    this.recalcular();
  }

  totalTipo(tipo: 'PRODUTO' | 'SERVICO'): number {
    return this.itens().filter((i) => i.tipo === tipo).reduce((s, i) => s + numeroBr(i.valorTotal), 0);
  }

  recalcular(): void {
    this.itens.update((l) => [...l]);
    if (!this.totalManual) {
      const t = this.totalTipo('PRODUTO') + this.totalTipo('SERVICO') - numeroBr(this.os.desconto);
      if (t > 0) this.os.total = +t.toFixed(2);
    }
  }

  totalEditado(): void {
    this.totalManual = true;
  }

  // ------------------------------------------------------------ parcelas

  adicionarParcela(forma = 'Boleto'): void {
    this.parcelas.update((l) => [...l, { id: null, forma, valor: '', vencimento: '', recebida: false }]);
  }

  removerParcela(i: number): void {
    this.parcelas.update((l) => l.filter((_, idx) => idx !== i));
  }

  somaParcelas(): number {
    return this.parcelas().reduce((s, p) => s + numeroBr(p.valor), 0);
  }

  diferenca(): number {
    return +(numeroBr(this.os.total) - this.somaParcelas()).toFixed(2);
  }

  /** Divide o total (menos o já recebido) em N parcelas iguais. */
  dividir(n: number, intervalo: number, forma: string): void {
    const recebidas = this.parcelas().filter((p) => p.recebida);
    const restante = numeroBr(this.os.total) - recebidas.reduce((s, p) => s + numeroBr(p.valor), 0);
    if (restante <= 0 || n < 1) return;
    const base = somarDias(this.os.dataEmissao || hojeIso(), this.os.prazoPagamentoDias ?? 0);
    const novas: ParcelaLinha[] = [];
    let acumulado = 0;
    for (let i = 0; i < n; i++) {
      const v = i === n - 1 ? +(restante - acumulado).toFixed(2) : +(restante / n).toFixed(2);
      acumulado += v;
      novas.push({ id: null, forma, valor: v, vencimento: somarDias(base, intervalo * i), recebida: false });
    }
    this.parcelas.set([...recebidas, ...novas]);
  }

  // ------------------------------------------------------------ cliente

  buscarCliente(): void {
    this.cliente.id = null;
    if (this.cliente.nome.length >= 2) this.buscaCliente$.next(this.cliente.nome);
    else this.sugestoesClientes.set([]);
  }

  escolherCliente(c: Cliente): void {
    this.cliente = { ...c };
    this.sugestoesClientes.set([]);
  }

  // ------------------------------------------------------------ calendário de produção

  verificarEntrega(): void {
    if (!this.os.previsaoEntrega) {
      this.disponibilidade.set(null);
      return;
    }
    this.api.disponibilidade(this.os.previsaoEntrega).subscribe((d) => this.disponibilidade.set(d));
  }

  sugerirEntrega(dias: number): void {
    this.os.previsaoEntrega = somarDias(hojeIso(), dias);
    this.verificarEntrega();
  }

  // ------------------------------------------------------------ importação do documento

  soltarArquivo(e: DragEvent): void {
    e.preventDefault();
    this.arrastando = false;
    const f = e.dataTransfer?.files?.[0];
    if (f) this.importar(f);
  }

  escolherArquivo(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) this.importar(f);
  }

  importar(arquivo: File): void {
    this.lendo.set(true);
    this.erroImportacao.set(null);
    this.importacao.set(null);
    this.api.importarDocumento(arquivo).subscribe({
      next: (r) => {
        this.aplicar(r);
        this.importacao.set(r);
        this.lendo.set(false);
      },
      error: (e) => {
        this.erroImportacao.set(e.error?.detail ?? 'Não foi possível ler o documento.');
        this.lendo.set(false);
      },
    });
  }

  protected nomeMetodo(m: ResultadoImportacao['metodo']): string {
    return METODOS[m];
  }

  protected marcado(campo: string): boolean {
    return this.preenchidos().has(campo);
  }

  private aplicar(r: ResultadoImportacao): void {
    const d = r.dados;
    const marcados = new Set<string>();
    const set = <K extends keyof typeof this.os>(campo: K, v: (typeof this.os)[K] | null | undefined) => {
      if (v !== null && v !== undefined && v !== '') {
        this.os[campo] = v;
        marcados.add(campo);
      }
    };
    set('codigo', d.codigo);
    set('dataEmissao', d.dataEmissao);
    set('hora', d.hora);
    set('previsaoEntrega', d.previsaoEntrega);
    set('servicos', d.servicos);
    set('observacoes', d.observacoes);
    set('prazoPagamentoDias', d.prazoPagamentoDias);
    set('desconto', d.desconto);
    this.os.documentoOrigem = r.documentoOrigem;
    if (d.cliente) {
      for (const [k, v] of Object.entries(d.cliente)) {
        if (v) {
          (this.cliente as unknown as Record<string, unknown>)[k] = v;
          marcados.add('cliente.' + k);
        }
      }
    }
    this.cliente.id = r.clienteExistenteId;
    if (d.vendedor && this.auth.isGestor()) {
      const v = this.vendedores().find((x) => x.nome.toLowerCase().includes(d.vendedor!.toLowerCase()));
      if (v) this.os.vendedorId = v.id;
    }
    if (d.itens?.length) this.itens.set(d.itens.map((i) => ({ ...i, importado: true })));
    if (d.total) {
      this.os.total = d.total;
      this.totalManual = true;
      marcados.add('total');
    }
    if (d.parcelas?.length) {
      this.parcelas.set([
        ...this.parcelas().filter((p) => p.recebida),
        ...d.parcelas.map((p) => ({ id: null, forma: p.forma, valor: p.valor, vencimento: p.vencimento, recebida: false, importada: true })),
      ]);
      d.parcelas.forEach((p) => {
        if (!this.formas().includes(p.forma)) this.formas.update((f) => [...f, p.forma]);
      });
    }
    this.preenchidos.set(marcados);
    this.verificarEntrega();
  }

  // ------------------------------------------------------------ salvar

  salvar(): void {
    if (!this.cliente.nome?.trim()) {
      this.snack.open('Informe o nome do cliente.', 'OK', { duration: 4000 });
      return;
    }
    if (numeroBr(this.os.total) <= 0) {
      this.snack.open('Informe o valor da venda.', 'OK', { duration: 4000 });
      return;
    }
    const parcelas = this.parcelas().filter((p) => numeroBr(p.valor) > 0 && p.vencimento);
    if (!parcelas.length) {
      this.snack.open('Informe ao menos uma parcela (forma, valor e data de pagamento).', 'OK', { duration: 5000 });
      return;
    }
    const req: OsRequest = {
      ...this.os,
      codigo: this.os.codigo || null,
      hora: this.os.hora || null,
      previsaoEntrega: this.os.previsaoEntrega || null,
      desconto: numeroBr(this.os.desconto),
      total: numeroBr(this.os.total),
      cliente: this.cliente,
      itens: this.itens().filter((i) => i.descricao?.trim()).map(({ importado, ...i }) => ({
        ...i, quantidade: numeroBr(i.quantidade), valorUnitario: numeroBr(i.valorUnitario),
        desconto: numeroBr(i.desconto), valorTotal: numeroBr(i.valorTotal),
      })),
      parcelas: parcelas.map((p) => ({ id: p.id, forma: p.forma, valor: numeroBr(p.valor), vencimento: p.vencimento })),
    };
    this.salvando.set(true);
    const id = this.id();
    (id ? this.api.atualizarOs(+id, req) : this.api.criarOs(req)).subscribe({
      next: (o) => {
        const avisos: string[] = [];
        if (Math.abs(this.diferenca()) > 0.05) avisos.push(`soma das parcelas difere do total em ${moeda(this.diferenca())}`);
        if (o.bloqueiosNaEntrega.length) avisos.push('produção bloqueada na data de entrega: ' + o.bloqueiosNaEntrega.join(', '));
        this.snack.open('OS salva.' + (avisos.length ? ' Atenção: ' + avisos.join('; ') : ''), 'OK', { duration: 6000 });
        this.router.navigate(['/os', o.id]);
      },
      error: () => this.salvando.set(false),
    });
  }
}
