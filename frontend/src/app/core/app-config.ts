import { InjectionToken } from '@angular/core';

/** Configuração carregada em tempo de execução de /config.json (permite trocar ambiente sem novo build). */
export interface AppConfig {
  apiUrl: string;
  keycloak: { url: string; realm: string; clientId: string };
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');
