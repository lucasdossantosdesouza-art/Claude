import { Component, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { PainelFinanceiro } from '../../core/models';
import { Arquivos } from '../../shared/arquivos';
import { COMUNS, hojeIso } from '../../shared';

@Component({
  selector: 'app-painel-financeiro',
  imports: [COMUNS],
  templateUrl: './painel-financeiro.html',
})
export class PainelFinanceiroPage implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  protected readonly arquivos = inject(Arquivos);
  protected readonly p = signal<PainelFinanceiro | null>(null);
  protected data = hojeIso();
  protected selecionadas = new Set<number>();
  protected readonly hoje = hojeIso();

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.painelFinanceiro(this.data).subscribe((d) => {
      this.p.set(d);
      this.selecionadas.clear();
    });
  }

  alternar(id: number, marcado: boolean): void {
    marcado ? this.selecionadas.add(id) : this.selecionadas.delete(id);
  }

  pagar(): void {
    this.api.pagarComissoes([...this.selecionadas]).subscribe((r) => {
      this.snack.open(`${r.pagas} comissão(ões) marcada(s) como paga(s).`, 'OK', { duration: 4000 });
      this.carregar();
    });
  }

  exportar(nome: 'comissoes' | 'contas-receber', formato: 'XLSX' | 'PDF'): void {
    this.api.relatorio(nome, { formato }).subscribe((r) => this.arquivos.baixar(r, `${nome}.${formato.toLowerCase()}`));
  }

  progressoTeto(d: PainelFinanceiro): number {
    return Math.min(100, (d.faturamentoMes / d.tetoEmpresa) * 100);
  }
}
