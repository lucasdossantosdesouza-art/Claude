import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth, Perfil } from './auth';

/** Restringe a rota aos perfis informados em data.perfis. */
export const perfilGuard: CanActivateFn = (route) => {
  const auth = inject(Auth);
  const perfis = (route.data?.['perfis'] ?? []) as Perfil[];
  return perfis.length === 0 || auth.temPerfil(...perfis) ? true : inject(Router).parseUrl('/');
};

/** Página inicial conforme o perfil. */
export const inicioGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const destino = { GESTOR: '/financeiro', PRODUCAO: '/calendario', VENDEDOR: '/painel' }[auth.perfil()];
  return inject(Router).parseUrl(destino);
};
