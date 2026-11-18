import { computed, inject, Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';

export type Perfil = 'GESTOR' | 'PRODUCAO' | 'VENDEDOR';

/** Usuário logado a partir do token do Keycloak. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly keycloak = inject(Keycloak);

  private readonly papeis = signal<string[]>(this.keycloak.tokenParsed?.['realm_access']?.roles ?? []);
  readonly nome = signal<string>(this.keycloak.tokenParsed?.['name'] ?? this.keycloak.tokenParsed?.['preferred_username'] ?? '');
  readonly login = signal<string>(this.keycloak.tokenParsed?.['preferred_username'] ?? '');

  readonly perfil = computed<Perfil>(() => {
    const p = this.papeis();
    return p.includes('GESTOR') ? 'GESTOR' : p.includes('PRODUCAO') ? 'PRODUCAO' : 'VENDEDOR';
  });
  readonly isGestor = computed(() => this.perfil() === 'GESTOR');
  readonly isVendedor = computed(() => this.perfil() === 'VENDEDOR');
  readonly isProducao = computed(() => this.perfil() === 'PRODUCAO');

  readonly nomePerfil = computed(
    () => ({ GESTOR: 'Gestor(a) / Financeiro', PRODUCAO: 'Chefe de Produção', VENDEDOR: 'Vendedor' })[this.perfil()],
  );

  temPerfil(...perfis: Perfil[]): boolean {
    return perfis.includes(this.perfil());
  }

  /** Token atualizado, para downloads autenticados fora do HttpClient. */
  async token(): Promise<string> {
    await this.keycloak.updateToken(30);
    return this.keycloak.token ?? '';
  }

  contaUrl(): string {
    return this.keycloak.createAccountUrl();
  }

  sair(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
