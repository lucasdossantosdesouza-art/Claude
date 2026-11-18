import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import {
  createInterceptorCondition,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
} from 'keycloak-angular';

import { routes } from './app.routes';
import { APP_CONFIG, AppConfig } from './core/app-config';
import { erroInterceptor } from './core/erro.interceptor';

registerLocaleData(localePt);

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function criarAppConfig(config: AppConfig): ApplicationConfig {
  // envia o token do Keycloak apenas para a nossa API
  const apiCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
    urlPattern: new RegExp('^' + escapeRegex(config.apiUrl) + '(/.*)?$', 'i'),
    bearerPrefix: 'Bearer',
  });

  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      { provide: APP_CONFIG, useValue: config },
      provideKeycloak({
        config: config.keycloak,
        initOptions: {
          onLoad: 'login-required',
          pkceMethod: 'S256',
          checkLoginIframe: false,
        },
      }),
      { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiCondition] },
      provideHttpClient(withInterceptors([includeBearerTokenInterceptor, erroInterceptor])),
      provideRouter(routes, withComponentInputBinding()),
      { provide: LOCALE_ID, useValue: 'pt-BR' },
      { provide: MAT_DATE_LOCALE, useValue: 'pt-BR' },
      { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline', subscriptSizing: 'dynamic' } },
    ],
  };
}
