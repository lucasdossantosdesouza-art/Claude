import { Component, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Api } from '../../core/api';
import { Configuracao, Faixa, ItemLista, Parametro } from '../../core/models';
import { COMUNS, numeroBr } from '../../shared';

interface FaixaEdicao {
  valorMinimo: number | string;
  valor: number | string;
}

const LISTAS = [
  { chave: 'formas-pagamento', titulo: 'Formas de pagamento', campo: 'formasPagamento' },
  { chave: 'motivos-estorno', titulo: 'Motivos de estorno', campo: 'motivosEstorno' },
  { chave: 'produtos-producao', titulo: 'Produtos / tipos de produção (calendário)', campo: 'produtosProducao' },
] as const;

/** Parametrização de valores, percentuais, dias, faixas e listas usadas nas regras de negócio. */
@Component({
  selector: 'app-parametros',
  imports: [COMUNS, MatSlideToggleModule],
  templateUrl: './parametros.html',
  styles: `
    .faixas input { font: inherit; padding: .4rem; border: 1px solid #c8cfda; border-radius: 6px; width: 140px; }
    .lista { display: flex; flex-direction: column; gap: .2rem; margin-bottom: .6rem; }
    .param-bool { display: flex; flex-direction: column; justify-content: center; gap: .2rem; padding-bottom: 1rem; }
  `,
})
export class Parametros implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  protected readonly cfg = signal<Configuracao | null>(null);
  protected readonly listas = LISTAS;
  protected gerais: Record<string, number | string | boolean> = {};
  protected faixasComissao: FaixaEdicao[] = [];
  protected faixasMeta: FaixaEdicao[] = [];
  protected novos: Record<string, string> = {};

  ngOnInit(): void {
    this.api.configuracao().subscribe((c) => this.aplicar(c));
  }

  private aplicar(c: Configuracao): void {
    this.cfg.set(c);
    this.gerais = Object.fromEntries(c.gerais.map((p) => [p.chave, p.tipo === 'BOOLEANO' ? p.valor >= 1 : p.valor]));
    this.faixasComissao = c.faixasComissao.map((f) => ({ ...f }));
    this.faixasMeta = c.faixasMetaSemanal.map((f) => ({ ...f }));
  }

  protected sufixo(p: Parametro): string {
    return { MOEDA: 'R$', PERCENTUAL: '%', DIAS: 'dias', NUMERO: '', BOOLEANO: '' }[p.tipo];
  }

  protected itensDa(campo: (typeof LISTAS)[number]['campo']): ItemLista[] {
    return this.cfg()?.[campo] ?? [];
  }

  salvarGerais(): void {
    const valores: Record<string, number> = {};
    for (const p of this.cfg()!.gerais) {
      const v = this.gerais[p.chave];
      valores[p.chave] = p.tipo === 'BOOLEANO' ? (v ? 1 : 0) : numeroBr(v);
    }
    this.api.salvarParametros(valores).subscribe((c) => {
      this.aplicar(c);
      this.snack.open('Parâmetros salvos. Comissões do mês aberto recalculadas.', 'OK', { duration: 4000 });
    });
  }

  private converter(l: FaixaEdicao[]): Faixa[] {
    return l.filter((f) => numeroBr(f.valorMinimo) > 0).map((f) => ({ valorMinimo: numeroBr(f.valorMinimo), valor: numeroBr(f.valor) }));
  }

  salvarFaixasComissao(): void {
    this.api.salvarFaixasComissao(this.converter(this.faixasComissao)).subscribe((c) => {
      this.aplicar(c);
      this.snack.open('Faixas de comissão salvas (meses já fechados mantêm a faixa travada).', 'OK', { duration: 5000 });
    });
  }

  salvarFaixasMeta(): void {
    this.api.salvarFaixasMeta(this.converter(this.faixasMeta)).subscribe((c) => {
      this.aplicar(c);
      this.snack.open('Patamares da meta semanal salvos.', 'OK', { duration: 4000 });
    });
  }

  alternarItem(lista: string, item: ItemLista, ativo: boolean): void {
    this.api.ativarItemLista(lista, item.id, ativo).subscribe((c) => this.aplicar(c));
  }

  adicionarItem(lista: string): void {
    const nome = (this.novos[lista] ?? '').trim();
    if (!nome) return;
    this.api.adicionarItemLista(lista, nome).subscribe((c) => {
      this.novos[lista] = '';
      this.aplicar(c);
    });
  }
}
