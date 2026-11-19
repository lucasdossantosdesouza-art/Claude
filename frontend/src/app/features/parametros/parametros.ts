import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Api } from '../../core/api';
import { AlteracaoVigencia, Configuracao, Faixa, ItemLista, Parametro, ResultadoParametrizacao } from '../../core/models';
import { COMUNS, moeda, numeroBr } from '../../shared';

interface FaixaEdicao {
  valorMinimo: number | string;
  valor: number | string;
}

const LISTAS = [
  { chave: 'formas-pagamento', titulo: 'Formas de pagamento', campo: 'formasPagamento' },
  { chave: 'motivos-estorno', titulo: 'Motivos de estorno', campo: 'motivosEstorno' },
  { chave: 'produtos-producao', titulo: 'Produtos / tipos de produção (calendário)', campo: 'produtosProducao' },
  { chave: 'tipos-ocorrencia', titulo: 'Tipos de ocorrência do pós-venda', campo: 'tiposOcorrencia' },
] as const;

const GRUPOS = ['Remuneração', 'Comissão mensal', 'Meta da semana', 'Prazos e alertas', 'Sistema'];

/** "2027-01" → "01/2027" */
export function mesBr(m: string | null | undefined): string {
  return m ? `${m.substring(5, 7)}/${m.substring(0, 4)}` : '';
}

/**
 * Parametrização de valores, percentuais e prazos com vigência: cada alteração vale a partir de um mês,
 * o histórico é preservado e os valores do documento de requisitos ficam como referência.
 */
@Component({
  selector: 'app-parametros',
  imports: [COMUNS, MatSlideToggleModule],
  templateUrl: './parametros.html',
  styleUrl: './parametros.scss',
})
export class Parametros implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  protected readonly cfg = signal<Configuracao | null>(null);
  protected readonly historico = signal<AlteracaoVigencia[]>([]);
  protected readonly listas = LISTAS;
  protected readonly mesBr = mesBr;
  protected readonly diasSemana = ['Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado', 'Domingo'];

  /** Mês cujas regras estão na tela. */
  protected mesConsulta = '';
  /** Mês a partir do qual as alterações passam a valer. */
  protected vigencia = '';

  protected gerais: Record<string, number | string | boolean> = {};
  protected faixasComissao: FaixaEdicao[] = [];
  protected faixasMeta: FaixaEdicao[] = [];
  protected novos: Record<string, string> = {};

  protected readonly grupos = computed(() => {
    const c = this.cfg();
    if (!c) return [];
    return GRUPOS.map((g) => ({ nome: g, itens: c.gerais.filter((p) => p.grupo === g) })).filter((g) => g.itens.length);
  });

  ngOnInit(): void {
    this.carregar();
    this.carregarHistorico();
  }

  carregar(mes?: string): void {
    this.api.configuracao(mes).subscribe((c) => this.aplicar(c));
  }

  private carregarHistorico(): void {
    this.api.vigencias().subscribe((h) => this.historico.set(h));
  }

  private aplicar(c: Configuracao): void {
    this.cfg.set(c);
    this.mesConsulta = c.mes;
    if (!this.vigencia) {
      this.vigencia = c.mesAtual < c.primeiroMesEditavel ? c.primeiroMesEditavel : c.mesAtual;
    }
    this.gerais = Object.fromEntries(c.gerais.map((p) => [p.chave, p.tipo === 'BOOLEANO' ? p.valor >= 1 : Number(p.valor)]));
    this.faixasComissao = c.faixasComissao.faixas.map((f) => ({ ...f }));
    this.faixasMeta = c.faixasMetaSemanal.faixas.map((f) => ({ ...f }));
  }

  private concluir(r: ResultadoParametrizacao, msgSemMudanca = 'Nenhuma alteração.'): void {
    this.aplicar(r.configuracao);
    this.carregarHistorico();
    const msg = r.alteracoes.length
      ? `Salvo com vigência a partir de ${mesBr(this.vigencia)}: ${r.alteracoes.join('; ')}`
      : msgSemMudanca;
    this.snack.open(msg, 'OK', { duration: 7000 });
  }

  // ------------------------------------------------------------ exibição

  protected sufixo(p: Parametro): string {
    return { MOEDA: 'R$', PERCENTUAL: '%', DIAS: 'dias', NUMERO: '', BOOLEANO: '' }[p.tipo];
  }

  protected formatar(p: Parametro, v: number | null): string {
    if (v === null || v === undefined) return '—';
    switch (p.tipo) {
      case 'MOEDA': return moeda(v);
      case 'PERCENTUAL': return v.toLocaleString('pt-BR') + '%';
      case 'DIAS': return p.chave === 'DIA_INICIO_SEMANA' ? this.diasSemana[v - 1] ?? String(v) : v + ' dia(s)';
      case 'BOOLEANO': return v >= 1 ? 'Sim' : 'Não';
      default: return v.toLocaleString('pt-BR');
    }
  }

  protected difereDoDocumento(p: Parametro): boolean {
    return p.valorDocumento !== null && Number(p.valor) !== Number(p.valorDocumento);
  }

  protected faixasDiferentes(atual: Faixa[], doc: Faixa[]): boolean {
    return JSON.stringify(atual.map((f) => [+f.valorMinimo, +f.valor])) !== JSON.stringify(doc.map((f) => [+f.valorMinimo, +f.valor]));
  }

  protected itensDa(campo: (typeof LISTAS)[number]['campo']): ItemLista[] {
    return this.cfg()?.[campo] ?? [];
  }

  protected vigenciaValida(): boolean {
    const c = this.cfg();
    return !!c && !!this.vigencia && this.vigencia >= c.primeiroMesEditavel;
  }

  // ------------------------------------------------------------ ações

  salvarGerais(): void {
    if (!this.vigenciaValida()) return this.avisarVigencia();
    const valores: Record<string, number> = {};
    for (const p of this.cfg()!.gerais) {
      const v = this.gerais[p.chave];
      valores[p.chave] = p.tipo === 'BOOLEANO' ? (v ? 1 : 0) : numeroBr(v);
    }
    this.api.salvarParametros(this.vigencia, valores).subscribe((r) => this.concluir(r));
  }

  private converter(l: FaixaEdicao[]): Faixa[] {
    return l.filter((f) => numeroBr(f.valorMinimo) > 0).map((f) => ({ valorMinimo: numeroBr(f.valorMinimo), valor: numeroBr(f.valor) }));
  }

  salvarFaixasComissao(): void {
    if (!this.vigenciaValida()) return this.avisarVigencia();
    this.api.salvarFaixasComissao(this.vigencia, this.converter(this.faixasComissao)).subscribe((r) => this.concluir(r));
  }

  salvarFaixasMeta(): void {
    if (!this.vigenciaValida()) return this.avisarVigencia();
    this.api.salvarFaixasMeta(this.vigencia, this.converter(this.faixasMeta)).subscribe((r) => this.concluir(r));
  }

  usarDocumento(tabela: 'comissao' | 'meta'): void {
    const c = this.cfg()!;
    if (tabela === 'comissao') this.faixasComissao = c.faixasComissao.documento.map((f) => ({ ...f }));
    else this.faixasMeta = c.faixasMetaSemanal.documento.map((f) => ({ ...f }));
  }

  restaurarDocumento(): void {
    if (!this.vigenciaValida()) return this.avisarVigencia();
    if (!confirm(`Voltar todos os valores, percentuais e faixas ao documento de requisitos a partir de ${mesBr(this.vigencia)}?`)) return;
    this.api.restaurarDocumento(this.vigencia).subscribe((r) => this.concluir(r, 'Os valores já são os do documento.'));
  }

  excluirVigencia(v: AlteracaoVigencia): void {
    if (!confirm(`Excluir as alterações com vigência a partir de ${mesBr(v.mes)}?`)) return;
    this.api.excluirVigencia(v.mes).subscribe((h) => {
      this.historico.set(h);
      this.carregar(this.mesConsulta);
      this.snack.open('Alteração excluída.', 'OK', { duration: 4000 });
    });
  }

  private avisarVigencia(): void {
    this.snack.open(`Escolha um mês a partir de ${mesBr(this.cfg()?.primeiroMesEditavel)} (meses anteriores já foram fechados).`, 'OK', { duration: 6000 });
  }

  alternarItem(lista: string, item: ItemLista, ativo: boolean): void {
    this.api.ativarItemLista(lista, item.id, ativo).subscribe((c) => this.cfg.set(c));
  }

  adicionarItem(lista: string): void {
    const nome = (this.novos[lista] ?? '').trim();
    if (!nome) return;
    this.api.adicionarItemLista(lista, nome).subscribe((c) => {
      this.novos[lista] = '';
      this.cfg.set(c);
    });
  }
}
