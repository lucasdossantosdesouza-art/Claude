import { dataBr, moeda, numeroBr, somarDias } from './formatos';

describe('formatos', () => {
  it('formata moeda em reais', () => {
    expect(moeda(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50');
    expect(moeda(null).replace(/\s/g, ' ')).toBe('R$ 0,00');
  });

  it('converte números no formato brasileiro', () => {
    expect(numeroBr('1.234,56')).toBe(1234.56);
    expect(numeroBr('1234.56')).toBe(1234.56);
    expect(numeroBr('')).toBe(0);
    expect(numeroBr(10)).toBe(10);
  });

  it('formata e soma datas ISO', () => {
    expect(dataBr('2026-11-05')).toBe('05/11/2026');
    expect(somarDias('2026-11-25', 10)).toBe('2026-12-05');
  });
});
