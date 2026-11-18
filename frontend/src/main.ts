import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { criarAppConfig } from './app/app.config';
import { AppConfig } from './app/core/app-config';

/** Carrega /config.json antes do bootstrap (URL da API e do Keycloak por ambiente). */
fetch('/config.json')
  .then((r) => r.json() as Promise<AppConfig>)
  .then((config) => bootstrapApplication(App, criarAppConfig(config)))
  .catch((err) => console.error('Falha ao iniciar a aplicação', err));
