import { Component, inject, OnInit, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api';
import { PainelFechamento } from '../../core/models';
import { COMUNS } from '../../shared';

@Component({
  selector: 'app-fechamento',
  imports: [COMUNS],
  template: `
    <div class="pagina">
      <h1>Fechamento mensal</h1>
      <section class="cartao">
        <p>No fechamento (virada do mês) o sistema soma o total vendido por vendedor, define a faixa (%) e a <b>trava</b> para o mês
          da venda. As comissões das parcelas já recebidas passam a <b>autorizadas para pagamento</b>; as parcelas futuras geram
          comissão com essa mesma faixa quando forem recebidas.</p>
        @if (p(); as d) {
          @for (m of d.pendentes; track m) {
            <button mat-flat-button (click)="fechar(m)" style="margin-right: .5rem"><mat-icon>lock</mat-icon> Fechar mês {{ m }}</button>
          } @empty { <p class="verde">✔ Nenhum mês pendente de fechamento.</p> }
        }
        <p class="suave">O fechamento automático na virada do mês pode ser ligado/desligado em Parametrização.</p>
      </section>
      @if (p(); as d) {
        <section class="cartao tabela-wrap"><h2>Prévia do mês corrente ({{ d.mesAtual }})</h2>
          <table class="tabela responsiva">
            <thead><tr><th>Vendedor</th><th class="num">Vendido</th><th class="num">Faturamento empresa</th><th>Faixa provisória</th><th>Próxima faixa</th></tr></thead>
            <tbody>@for (x of d.previa; track x.vendedor) {
              <tr><td>{{ x.vendedor }}</td><td class="num">{{ x.faixa.totalVendido | moeda }}</td><td class="num">{{ x.faixa.faturamentoEmpresa | moeda }}</td>
                <td><b>{{ x.faixa.percentual | pct }}</b> <span class="suave">{{ x.faixa.regra }}</span></td>
                <td>@if (x.faixa.proximaFaixaValor) { faltam {{ x.faixa.faltaProximaFaixa | moeda }} p/ {{ x.faixa.proximaFaixaPercentual | pct }} } @else { máxima }</td></tr>
            }</tbody>
          </table>
        </section>
        <section class="cartao tabela-wrap"><h2>Meses fechados</h2>
          <table class="tabela responsiva">
            <thead><tr><th>Mês</th><th>Vendedor</th><th class="num">Vendido</th><th class="num">Faturamento empresa</th><th>Faixa travada</th><th class="num">Fixo</th><th>Fechado em</th></tr></thead>
            <tbody>@for (h of d.historico; track h.mes + h.vendedor) {
              <tr><td>{{ h.mes }}</td><td>{{ h.vendedor }}</td><td class="num">{{ h.totalVendido | moeda }}</td><td class="num">{{ h.faturamentoEmpresa | moeda }}</td>
                <td>🔒 <b>{{ h.percentual | pct }}</b> <span class="suave">{{ h.regra }}</span></td><td class="num">{{ h.salarioFixo | moeda }}</td>
                <td>{{ h.fechadoEm | date: 'dd/MM/yyyy HH:mm' }}</td></tr>
            } @empty { <tr><td colspan="7" class="suave">Nenhum fechamento ainda.</td></tr> }</tbody>
          </table>
        </section>
      }
    </div>
  `,
})
export class Fechamento implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  protected readonly p = signal<PainelFechamento | null>(null);

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.fechamentos().subscribe((d) => this.p.set(d));
  }

  fechar(mes: string): void {
    if (!confirm(`Fechar o mês ${mes}? A faixa de cada vendedor ficará travada.`)) return;
    this.api.fecharMes(mes).subscribe((r) => {
      this.snack.open(`Mês ${mes} fechado: faixa travada para ${r.vendedores} vendedor(es).`, 'OK', { duration: 5000 });
      this.carregar();
    });
  }
}
