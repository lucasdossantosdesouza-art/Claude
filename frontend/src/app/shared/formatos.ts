import { Pipe, PipeTransform } from '@angular/core';
import { STATUS_COMISSAO, StatusComissao } from '../core/models';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function moeda(v: number | null | undefined): string {
  return BRL.format(v ?? 0);
}

export function dataBr(v: string | null | undefined): string {
  if (!v) return '';
  const [a, m, d] = v.substring(0, 10).split('-');
  return `${d}/${m}/${a}`;
}

export function hojeIso(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
}

export function somarDias(iso: string, dias: number): string {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + dias);
  return d.toISOString().substring(0, 10);
}

/** Converte "1.234,56" ou "1234.56" em número. */
export function numeroBr(v: unknown): number {
  if (typeof v === 'number') return v;
  if (v === null || v === undefined || v === '') return 0;
  let t = String(v).trim();
  if (t.includes(',')) t = t.replace(/\./g, '').replace(',', '.');
  const n = parseFloat(t);
  return isNaN(n) ? 0 : n;
}

@Pipe({ name: 'moeda' })
export class MoedaPipe implements PipeTransform {
  transform = moeda;
}

@Pipe({ name: 'dataBr' })
export class DataBrPipe implements PipeTransform {
  transform = dataBr;
}

@Pipe({ name: 'pct' })
export class PercentualPipe implements PipeTransform {
  transform(v: number | null | undefined): string {
    return (v ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 4 }) + '%';
  }
}

@Pipe({ name: 'statusComissao' })
export class StatusComissaoPipe implements PipeTransform {
  transform(v: StatusComissao | null | undefined): string {
    return v ? STATUS_COMISSAO[v] : '';
  }
}

export const FORMATOS = [MoedaPipe, DataBrPipe, PercentualPipe, StatusComissaoPipe];
