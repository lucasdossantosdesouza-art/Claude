import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from './app-config';
import * as M from './models';

type Params = Record<string, string | number | boolean | null | undefined>;

/** Cliente tipado da API REST. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = inject(APP_CONFIG).apiUrl;

  private params(p: Params = {}): HttpParams {
    let hp = new HttpParams();
    for (const [k, v] of Object.entries(p)) {
      if (v !== null && v !== undefined && v !== '') hp = hp.set(k, String(v));
    }
    return hp;
  }

  private get<T>(url: string, p?: Params): Observable<T> {
    return this.http.get<T>(this.base + url, { params: this.params(p) });
  }

  // usuários
  me = () => this.get<M.Usuario>('/me');
  vendedores = () => this.get<M.Usuario[]>('/vendedores');

  // ordens de serviço
  listarOs = (f: Params) => this.get<M.OsResumo[]>('/os', f);
  os = (id: number) => this.get<M.OsDetalhe>(`/os/${id}`);
  criarOs = (req: M.OsRequest) => this.http.post<M.OsDetalhe>(`${this.base}/os`, req);
  atualizarOs = (id: number, req: M.OsRequest) => this.http.put<M.OsDetalhe>(`${this.base}/os/${id}`, req);

  importarDocumento(arquivo: File) {
    const fd = new FormData();
    fd.append('arquivo', arquivo);
    return this.http.post<M.ResultadoImportacao>(`${this.base}/os/importacao`, fd);
  }

  capacidadesLeitura = () => this.get<{ ia: boolean; ocr: boolean }>('/os/importacao/capacidades');

  receberParcela(osId: number, parcelaId: number, data: string, comprovante: File) {
    const fd = new FormData();
    fd.append('comprovante', comprovante);
    fd.append('dataRecebimento', data);
    return this.http.post<M.OsDetalhe>(`${this.base}/os/${osId}/parcelas/${parcelaId}/recebimento`, fd);
  }

  desfazerRecebimento = (osId: number, parcelaId: number) =>
    this.http.delete<M.OsDetalhe>(`${this.base}/os/${osId}/parcelas/${parcelaId}/recebimento`);

  cancelarOs(osId: number, dados: { motivo: string; estorno: boolean; motivoEstornoId?: number | null; valorEstorno?: number | null }, comprovante?: File | null) {
    const fd = new FormData();
    fd.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
    if (comprovante) fd.append('comprovante', comprovante);
    return this.http.post<M.OsDetalhe>(`${this.base}/os/${osId}/cancelamento`, fd);
  }

  confirmarEntrega = (osId: number, dataEntregaReal: string) =>
    this.http.post<M.OsDetalhe>(`${this.base}/os/${osId}/entrega`, { dataEntregaReal });
  registrarOcorrencia = (osId: number, tipo: string, descricao: string) =>
    this.http.post<M.OsDetalhe>(`${this.base}/os/${osId}/ocorrencias`, { tipo, descricao });
  resolverOcorrencia = (id: number) => this.http.post<void>(`${this.base}/ocorrencias/${id}/resolucao`, {});

  // clientes e busca
  clientes = (q: string) => this.get<M.Cliente[]>('/clientes', { q });
  historicoCliente = (id: number) => this.get<M.HistoricoCliente>(`/clientes/${id}/historico`);
  busca = (q: string) => this.get<M.ResultadoBusca>('/busca', { q });

  // painéis e financeiro
  painelVendedor = (vendedorId?: number | null) => this.get<M.PainelVendedor>('/painel/vendedor', { vendedorId });
  painelFinanceiro = (data?: string) => this.get<M.PainelFinanceiro>('/financeiro/painel', { data });
  contasReceber = (f: Params) => this.get<M.ParcelaReceber[]>('/financeiro/contas-receber', f);

  // comissões, fechamento, metas
  comissoes = (f: Params) => this.get<M.ComissoesDoMes>('/comissoes', f);
  pagarComissoes = (ids: number[]) => this.http.post<{ pagas: number }>(`${this.base}/comissoes/pagamento`, { ids });
  fechamentos = () => this.get<M.PainelFechamento>('/fechamentos');
  fecharMes = (mes: string) => this.http.post<{ vendedores: number }>(`${this.base}/fechamentos/${mes}`, {});
  metas = (mes?: string) => this.get<M.MetasVendedor[]>('/metas-semanais', { mes });
  pagarMeta = (vendedorId: number, semanaInicio: string) =>
    this.http.post<M.MetaSemanal>(`${this.base}/metas-semanais/pagamento`, { vendedorId, semanaInicio });

  // produção
  calendario = (mes: string) => this.get<M.DiaCalendario[]>('/calendario', { mes });
  disponibilidade = (data: string) => this.get<M.DiaCalendario>('/calendario/disponibilidade', { data });
  definirBloqueios = (data: string, produtoIds: number[], observacao: string | null) =>
    this.http.put<M.DiaCalendario>(`${this.base}/calendario/${data}`, { produtoIds, observacao });
  avisos = () => this.get<M.Aviso[]>('/avisos');
  avisosUrgentes = () => this.get<M.Aviso[]>('/avisos/urgentes');
  criarAviso = (a: Partial<M.Aviso>) => this.http.post<M.Aviso>(`${this.base}/avisos`, a);
  arquivarAviso = (id: number) => this.http.post<void>(`${this.base}/avisos/${id}/arquivamento`, {});

  // pós-venda e consultas
  posVenda = (status: string) => this.get<M.PainelPosVenda>('/posvenda', { status });
  estornos = (f: Params) => this.get<M.PainelEstornos>('/estornos', f);
  comprovantes = (q: string) => this.get<M.Comprovante[]>('/comprovantes', { q });
  auditoria = (q: string) => this.get<M.LogAlteracao[]>('/auditoria', { q });

  // parametrização
  configuracao = () => this.get<M.Configuracao>('/parametros');
  salvarParametros = (valores: Record<string, number>) => this.http.put<M.Configuracao>(`${this.base}/parametros/gerais`, valores);
  salvarFaixasComissao = (f: M.Faixa[]) => this.http.put<M.Configuracao>(`${this.base}/parametros/faixas-comissao`, f);
  salvarFaixasMeta = (f: M.Faixa[]) => this.http.put<M.Configuracao>(`${this.base}/parametros/faixas-meta-semanal`, f);
  adicionarItemLista = (lista: string, nome: string) => this.http.post<M.Configuracao>(`${this.base}/parametros/${lista}`, { nome });
  ativarItemLista = (lista: string, id: number, ativo: boolean) =>
    this.http.patch<M.Configuracao>(`${this.base}/parametros/${lista}/${id}`, { ativo });

  // arquivos e relatórios (blob autenticado)
  arquivo = (nome: string) => this.http.get(`${this.base}/arquivos/${encodeURIComponent(nome)}`, { responseType: 'blob' });
  relatorio = (nome: 'comissoes' | 'contas-receber', p: Params) =>
    this.http.get(`${this.base}/relatorios/${nome}`, { params: this.params(p), responseType: 'blob', observe: 'response' });
}
