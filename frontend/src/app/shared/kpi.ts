import { Component, input } from '@angular/core';

/** Cartão de indicador (rótulo, valor e complemento). */
@Component({
  selector: 'app-kpi',
  template: `
    <div class="kpi" [class.destaque]="destaque()">
      <div class="rot">{{ rotulo() }}</div>
      <div class="val">{{ valor() }}</div>
      @if (sub()) {
        <div class="sub">{{ sub() }}</div>
      }
      <ng-content />
    </div>
  `,
})
export class Kpi {
  readonly rotulo = input.required<string>();
  readonly valor = input.required<string>();
  readonly sub = input<string>();
  readonly destaque = input(false);
}
