import { HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Api } from '../core/api';

/** Abre/baixa arquivos protegidos (o download passa pelo token do Keycloak). */
@Injectable({ providedIn: 'root' })
export class Arquivos {
  private readonly api = inject(Api);

  abrir(nome: string): void {
    const aba = window.open('', '_blank');
    this.api.arquivo(nome).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      if (aba) aba.location.href = url;
      else window.location.href = url;
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    });
  }

  baixar(r: HttpResponse<Blob>, padrao: string): void {
    const cd = r.headers.get('Content-Disposition') ?? '';
    const nome = /filename="?([^";]+)"?/.exec(cd)?.[1] ?? padrao;
    const url = URL.createObjectURL(r.body!);
    const a = document.createElement('a');
    a.href = url;
    a.download = nome;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }
}
