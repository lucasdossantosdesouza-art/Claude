import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { filter, map } from 'rxjs';
import { Api } from '../core/api';
import { Auth, Perfil } from '../core/auth';
import { Aviso } from '../core/models';

interface ItemMenu {
  rota: string;
  rotulo: string;
  icone: string;
  perfis: Perfil[];
}

const MENU: ItemMenu[] = [
  { rota: '/painel', rotulo: 'Painel', icone: 'dashboard', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/financeiro', rotulo: 'Financeiro', icone: 'account_balance', perfis: ['GESTOR'] },
  { rota: '/os/nova', rotulo: 'Nova OS', icone: 'note_add', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/os', rotulo: 'Ordens de Serviço', icone: 'receipt_long', perfis: ['VENDEDOR', 'GESTOR', 'PRODUCAO'] },
  { rota: '/receber', rotulo: 'Contas a receber', icone: 'payments', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/comissoes', rotulo: 'Comissões', icone: 'percent', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/fechamento', rotulo: 'Fechamento mensal', icone: 'lock_clock', perfis: ['GESTOR'] },
  { rota: '/metas', rotulo: 'Meta semanal', icone: 'flag', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/calendario', rotulo: 'Calendário de produção', icone: 'calendar_month', perfis: ['VENDEDOR', 'GESTOR', 'PRODUCAO'] },
  { rota: '/avisos', rotulo: 'Avisos / urgências', icone: 'campaign', perfis: ['VENDEDOR', 'GESTOR', 'PRODUCAO'] },
  { rota: '/posvenda', rotulo: 'Pós-venda', icone: 'support_agent', perfis: ['VENDEDOR', 'GESTOR', 'PRODUCAO'] },
  { rota: '/estornos', rotulo: 'Estornos', icone: 'undo', perfis: ['VENDEDOR', 'GESTOR'] },
  { rota: '/comprovantes', rotulo: 'Comprovantes', icone: 'attach_file', perfis: ['GESTOR'] },
  { rota: '/parametros', rotulo: 'Parametrização', icone: 'tune', perfis: ['GESTOR'] },
  { rota: '/auditoria', rotulo: 'Log de alterações', icone: 'history', perfis: ['GESTOR'] },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, MatToolbarModule, MatSidenavModule, MatListModule,
    MatIconModule, MatButtonModule, MatMenuModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(Auth);
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly celular = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
  protected readonly menu = computed(() => MENU.filter((m) => m.perfis.includes(this.auth.perfil())));
  protected readonly urgentes = signal<Aviso[]>([]);
  protected busca = '';

  constructor() {
    this.carregarUrgentes();
    // sincroniza o usuário com a API no primeiro acesso e atualiza avisos a cada navegação
    this.api.me().subscribe();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.carregarUrgentes());
  }

  private carregarUrgentes(): void {
    this.api.avisosUrgentes().subscribe((a) => this.urgentes.set(a));
  }

  protected buscar(): void {
    if (this.busca.trim()) this.router.navigate(['/busca'], { queryParams: { q: this.busca.trim() } });
  }
}
