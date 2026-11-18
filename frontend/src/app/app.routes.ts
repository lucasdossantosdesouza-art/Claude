import { Routes } from '@angular/router';
import { inicioGuard, perfilGuard } from './core/perfil.guard';
import { Shell } from './layout/shell';

const G = ['GESTOR'];
const VG = ['VENDEDOR', 'GESTOR'];

/** Rotas com carregamento sob demanda; data.perfis restringe por perfil do Keycloak. */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivateChild: [perfilGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [inicioGuard], children: [] },
      { path: 'painel', data: { perfis: VG }, title: 'Painel', loadComponent: () => import('./features/painel/painel-vendedor').then((m) => m.PainelVendedorPage) },
      { path: 'os', title: 'Ordens de Serviço', loadComponent: () => import('./features/os/os-lista').then((m) => m.OsLista) },
      { path: 'os/nova', data: { perfis: VG }, title: 'Nova OS', loadComponent: () => import('./features/os/os-form').then((m) => m.OsForm) },
      { path: 'os/:id', title: 'OS', loadComponent: () => import('./features/os/os-detalhe').then((m) => m.OsDetalhePage) },
      { path: 'os/:id/editar', data: { perfis: VG }, title: 'Editar OS', loadComponent: () => import('./features/os/os-form').then((m) => m.OsForm) },
      { path: 'financeiro', data: { perfis: G }, title: 'Financeiro', loadComponent: () => import('./features/financeiro/painel-financeiro').then((m) => m.PainelFinanceiroPage) },
      { path: 'receber', data: { perfis: VG }, title: 'Contas a receber', loadComponent: () => import('./features/financeiro/contas-receber').then((m) => m.ContasReceber) },
      { path: 'comissoes', data: { perfis: VG }, title: 'Comissões', loadComponent: () => import('./features/comissoes/comissoes').then((m) => m.Comissoes) },
      { path: 'fechamento', data: { perfis: G }, title: 'Fechamento mensal', loadComponent: () => import('./features/comissoes/fechamento').then((m) => m.Fechamento) },
      { path: 'metas', data: { perfis: VG }, title: 'Meta semanal', loadComponent: () => import('./features/comissoes/metas').then((m) => m.Metas) },
      { path: 'calendario', title: 'Calendário de produção', loadComponent: () => import('./features/producao/calendario').then((m) => m.Calendario) },
      { path: 'avisos', title: 'Avisos', loadComponent: () => import('./features/producao/avisos').then((m) => m.Avisos) },
      { path: 'posvenda', title: 'Pós-venda', loadComponent: () => import('./features/posvenda/posvenda').then((m) => m.PosVenda) },
      { path: 'clientes/:id', title: 'Cliente', loadComponent: () => import('./features/posvenda/cliente-historico').then((m) => m.ClienteHistorico) },
      { path: 'busca', title: 'Busca', loadComponent: () => import('./features/consultas/busca').then((m) => m.Busca) },
      { path: 'estornos', data: { perfis: VG }, title: 'Estornos', loadComponent: () => import('./features/consultas/estornos').then((m) => m.Estornos) },
      { path: 'comprovantes', data: { perfis: G }, title: 'Comprovantes', loadComponent: () => import('./features/consultas/comprovantes').then((m) => m.Comprovantes) },
      { path: 'auditoria', data: { perfis: G }, title: 'Log de alterações', loadComponent: () => import('./features/consultas/auditoria').then((m) => m.Auditoria) },
      { path: 'parametros', data: { perfis: G }, title: 'Parametrização', loadComponent: () => import('./features/parametros/parametros').then((m) => m.Parametros) },
      { path: '**', redirectTo: '' },
    ],
  },
];
