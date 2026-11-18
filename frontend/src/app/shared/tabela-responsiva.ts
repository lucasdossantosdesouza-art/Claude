import { AfterViewInit, Directive, ElementRef, inject, OnDestroy } from '@angular/core';

/** No celular, as tabelas viram cartões: copia o título de cada coluna para o atributo data-l da célula. */
@Directive({ selector: 'table.responsiva' })
export class TabelaResponsiva implements AfterViewInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLTableElement>>(ElementRef);
  private observer?: MutationObserver;

  ngAfterViewInit(): void {
    this.rotular();
    this.observer = new MutationObserver(() => this.rotular());
    this.observer.observe(this.el.nativeElement, { childList: true, subtree: true });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private rotular(): void {
    const t = this.el.nativeElement;
    const titulos = Array.from(t.querySelectorAll('thead th')).map((th) => th.textContent?.trim() ?? '');
    t.querySelectorAll('tbody tr').forEach((tr) =>
      Array.from(tr.children).forEach((td, i) => {
        if (td.getAttribute('data-l') !== titulos[i]) td.setAttribute('data-l', titulos[i] ?? '');
      }),
    );
  }
}
