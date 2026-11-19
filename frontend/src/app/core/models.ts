// Contratos da API (espelham os DTOs do backend Spring Boot)

export type SituacaoOs = 'ABERTA' | 'FECHADA' | 'CANCELADA';
export type StatusParcela = 'PENDENTE' | 'RECEBIDA' | 'CANCELADA';
export type StatusComissao = 'PENDENTE_RECEBIMENTO' | 'PROVISORIA' | 'AUTORIZADA' | 'PAGA' | 'CANCELADA';
export type TipoItem = 'PRODUTO' | 'SERVICO';

export const STATUS_COMISSAO: Record<StatusComissao, string> = {
  PENDENTE_RECEBIMENTO: 'Pendente de recebimento',
  PROVISORIA: 'Recebida (mês aberto)',
  AUTORIZADA: 'Autorizada p/ pagamento',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
};

export interface Usuario {
  id: number;
  login: string;
  nome: string;
  email?: string;
  perfil: 'GESTOR' | 'PRODUCAO' | 'VENDEDOR';
}

export interface Cliente {
  id?: number | null;
  nome: string;
  razaoSocial?: string | null;
  cnpj?: string | null;
  codigo?: string | null;
  conta?: string | null;
  telefone?: string | null;
  endereco?: string | null;
  numero?: string | null;
  bairro?: string | null;
  cidade?: string | null;
  uf?: string | null;
  cep?: string | null;
}

export interface Item {
  id?: number | null;
  tipo: TipoItem;
  codigo?: string | null;
  descricao: string;
  unidade?: string | null;
  quantidade: number;
  valorUnitario: number;
  desconto: number;
  valorTotal: number;
}

export interface ParcelaRequest {
  id?: number | null;
  forma: string;
  valor: number;
  vencimento: string;
}

export interface OsRequest {
  codigo?: string | null;
  vendedorId?: number | null;
  dataEmissao: string;
  hora?: string | null;
  previsaoEntrega?: string | null;
  servicos?: string | null;
  observacoes?: string | null;
  desconto: number;
  total: number;
  prazoPagamentoDias?: number | null;
  meiaMargem: boolean;
  documentoOrigem?: string | null;
  cliente: Cliente;
  itens: Item[];
  parcelas: ParcelaRequest[];
}

export interface OsResumo {
  id: number;
  codigo: string | null;
  dataEmissao: string;
  previsaoEntrega: string | null;
  entregue: boolean;
  clienteId: number;
  cliente: string;
  vendedorId: number;
  vendedor: string;
  situacao: SituacaoOs;
  meiaMargem: boolean;
  total: number;
  recebido: number;
  comissaoAPagar: number;
  atrasada: boolean;
}

export interface Parcela {
  id: number;
  numero: number;
  forma: string;
  valor: number;
  vencimento: string;
  status: StatusParcela;
  dataRecebimento: string | null;
  comprovante: string | null;
  atrasada: boolean;
  comissao: number | null;
  percentualComissao: number | null;
  statusComissao: StatusComissao | null;
}

export interface FaixaCalculada {
  mes: string;
  totalVendido: number;
  faturamentoEmpresa: number;
  percentual: number;
  regra: string;
  travado: boolean;
  proximaFaixaValor: number | null;
  proximaFaixaPercentual: number | null;
  faltaProximaFaixa: number;
}

export interface Ocorrencia {
  id: number;
  osId: number;
  osCodigo: string | null;
  cliente: string;
  vendedor: string;
  tipo: string;
  descricao: string;
  status: string;
  usuario: string | null;
  criadoEm: string;
  resolvidoEm: string | null;
}

export interface LogAlteracao {
  id: number;
  osId: number | null;
  usuario: string;
  acao: string;
  detalhe: string;
  criadoEm: string;
}

export interface OsDetalhe {
  id: number;
  codigo: string | null;
  situacao: SituacaoOs;
  dataEmissao: string;
  hora: string | null;
  previsaoEntrega: string | null;
  vendedorId: number;
  vendedor: string;
  servicos: string | null;
  observacoes: string | null;
  totalProdutos: number;
  totalServicos: number;
  desconto: number;
  total: number;
  recebido: number;
  prazoPagamentoDias: number | null;
  meiaMargem: boolean;
  documentoOrigem: string | null;
  cancelMotivo: string | null;
  cancelData: string | null;
  estorno: boolean;
  estornoMotivo: string | null;
  estornoValor: number | null;
  estornoComprovante: string | null;
  entregue: boolean;
  dataEntregaReal: string | null;
  cliente: Cliente;
  itens: Item[];
  parcelas: Parcela[];
  comissao: {
    percentualAplicado: number;
    faixaTravada: boolean;
    faixaDoMes: FaixaCalculada;
    pendente: number;
    aPagar: number;
    paga: number;
  } | null;
  ocorrencias: Ocorrencia[];
  historico: LogAlteracao[];
  avisos: { id: number; texto: string; urgente: boolean }[];
  bloqueiosNaEntrega: string[];
}

export interface ResultadoImportacao {
  metodo: 'IA' | 'PDF_TEXTO' | 'PDF_OCR' | 'OCR';
  confianca: number;
  avisos: string[];
  dados: {
    codigo: string | null;
    dataEmissao: string | null;
    hora: string | null;
    previsaoEntrega: string | null;
    vendedor: string | null;
    cliente: Cliente | null;
    servicos: string | null;
    itens: Item[];
    desconto: number | null;
    total: number | null;
    prazoPagamentoDias: number | null;
    parcelas: { forma: string; valor: number; vencimento: string }[];
    observacoes: string | null;
  };
  documentoOrigem: string;
  clienteExistenteId: number | null;
  bloqueiosEntrega: string[];
}

export interface ParcelaReceber {
  parcelaId: number;
  osId: number;
  osCodigo: string | null;
  cliente: string;
  vendedorId: number;
  vendedor: string;
  numero: number;
  forma: string;
  vencimento: string;
  valor: number;
  atrasada: boolean;
  diasAtraso: number;
}

export interface TotalPeriodo {
  periodo: string;
  parcelas: number;
  total: number;
}

export interface ComissaoItem {
  id: number;
  osId: number;
  osCodigo: string | null;
  cliente: string;
  vendedorId: number;
  vendedor: string;
  parcela: number;
  forma: string | null;
  vencimento: string | null;
  dataRecebimento: string | null;
  comprovante: string | null;
  meiaMargem: boolean;
  base: number;
  percentual: number;
  valor: number;
  status: StatusComissao;
  pagaEm: string | null;
}

export interface PainelFinanceiro {
  data: string;
  totalDia: number;
  totalMes: number;
  totalGeral: number;
  porMes: TotalPeriodo[];
  porDia: TotalPeriodo[];
  comissoesAPagar: ComissaoItem[];
  totalAutorizado: number;
  totalProvisorio: number;
  osFechadas: OsResumo[];
  inadimplencia: ParcelaReceber[];
  mesesPendentesFechamento: string[];
  faturamentoMes: number;
  tetoEmpresa: number;
}

export interface MetaSemanal {
  semanaInicio: string;
  semanaFim: string;
  total: number;
  bonus: number;
  proximoPatamar: number | null;
  proximoBonus: number | null;
  faltaProximo: number;
  paga: boolean;
  encerrada: boolean;
}

export interface AvisoPainel {
  id: number;
  tipo: string;
  texto: string;
  urgente: boolean;
  produto: string | null;
  data: string | null;
  osId: number | null;
  osCodigo: string | null;
}

export interface PainelVendedor {
  mes: string;
  vendedorId: number;
  vendedor: string;
  faixa: FaixaCalculada;
  metaSemana: MetaSemanal;
  salarioFixo: number;
  comissaoAutorizada: number;
  comissaoProvisoria: number;
  comissaoPendente: number;
  comissaoPaga: number;
  atrasadas: ParcelaReceber[];
  aVencer: ParcelaReceber[];
  avisos: AvisoPainel[];
}

export interface ResumoVendedor {
  vendedorId: number;
  vendedor: string;
  totalVendido: number;
  percentual: number;
  travado: boolean;
  regra: string;
  salarioFixo: number;
  autorizada: number;
  provisoria: number;
  paga: number;
  pendente: number;
  bonusSemanal: number;
}

export interface ComissoesDoMes {
  mes: string;
  fechado: boolean;
  resumo: ResumoVendedor[];
  detalhe: ComissaoItem[];
}

export interface PainelFechamento {
  mesAtual: string;
  pendentes: string[];
  previa: { vendedor: string; faixa: FaixaCalculada }[];
  historico: {
    mes: string;
    vendedor: string;
    totalVendido: number;
    faturamentoEmpresa: number;
    percentual: number;
    regra: string;
    salarioFixo: number;
    fechadoEm: string;
  }[];
}

export interface MetasVendedor {
  vendedorId: number;
  vendedor: string;
  semanas: MetaSemanal[];
}

export interface Bloqueio {
  data: string;
  produtoId: number;
  produto: string;
  observacao: string | null;
}

export interface Aviso {
  id: number;
  tipo: 'GERAL' | 'VINCULADO';
  texto: string;
  urgente: boolean;
  osId: number | null;
  osCodigo: string | null;
  produtoId: number | null;
  produto: string | null;
  data: string | null;
  autor: string | null;
  ativo: boolean;
  criadoEm: string;
}

export interface DiaCalendario {
  data: string;
  bloqueios: Bloqueio[];
  avisos: Aviso[];
  entregas: number;
}

export interface PainelPosVenda {
  pendentesEntrega: OsResumo[];
  entregues: number;
  entreguesNoPrazo: number;
  ocorrencias: Ocorrencia[];
}

export interface Estorno {
  osId: number;
  osCodigo: string | null;
  cliente: string;
  vendedor: string;
  cancelData: string;
  justificativa: string;
  estorno: boolean;
  motivo: string | null;
  valorEstornado: number | null;
  comprovante: string | null;
}

export interface PainelEstornos {
  lista: Estorno[];
  indicador: { motivo: string; quantidade: number; valor: number }[];
}

export interface Comprovante {
  tipo: 'RECEBIMENTO' | 'ESTORNO' | 'DOCUMENTO_OS';
  osId: number;
  osCodigo: string | null;
  cliente: string;
  vendedor: string;
  parcela: number | null;
  forma: string | null;
  data: string | null;
  valor: number | null;
  comissao: number | null;
  statusComissao: StatusComissao | null;
  arquivo: string;
}

export interface ParcelaCliente {
  id: number;
  osId: number;
  osCodigo: string | null;
  cliente: string;
  numero: number;
  forma: string;
  valor: number;
  vencimento: string;
  status: StatusParcela;
  dataRecebimento: string | null;
}

export interface HistoricoCliente {
  cliente: Cliente;
  totalComprado: number;
  emAberto: number;
  compras: OsResumo[];
  parcelas: ParcelaCliente[];
  ocorrencias: Ocorrencia[];
  comissoesPagas: ComissaoItem[];
}

export interface ResultadoBusca {
  clientes: Cliente[];
  vendas: OsResumo[];
  datasPagamento: ParcelaCliente[];
  comissoesPagas: ComissaoItem[];
}

export type ChaveParametro = string;

export interface Parametro {
  chave: ChaveParametro;
  valor: number;
  vigenteDesde: string | null;
  descricao: string;
  tipo: 'MOEDA' | 'PERCENTUAL' | 'NUMERO' | 'DIAS' | 'BOOLEANO';
  grupo: string;
  origem: string | null;
  valorDocumento: number | null;
}

export interface Faixa {
  valorMinimo: number;
  valor: number;
}

export interface ItemLista {
  id: number;
  nome: string;
  ativo: boolean;
}

export interface TabelaFaixas {
  vigenteDesde: string | null;
  faixas: Faixa[];
  documento: Faixa[];
}

export interface Configuracao {
  mes: string;
  mesAtual: string;
  primeiroMesEditavel: string;
  gerais: Parametro[];
  faixasComissao: TabelaFaixas;
  faixasMetaSemanal: TabelaFaixas;
  formasPagamento: ItemLista[];
  motivosEstorno: ItemLista[];
  produtosProducao: ItemLista[];
  tiposOcorrencia: ItemLista[];
  iaConfigurada: boolean;
}

export interface ResultadoParametrizacao {
  alteracoes: string[];
  configuracao: Configuracao;
}

export interface AlteracaoVigencia {
  mes: string;
  situacao: 'AGENDADA' | 'VIGENTE' | 'HISTORICA';
  alteracoes: string[];
  registradoPor: string | null;
  registradoEm: string | null;
  removivel: boolean;
}

/** Valor vigente de um parâmetro (com padrão caso não exista). */
export function parametro(c: Configuracao | null, chave: string, padrao = 0): number {
  return c?.gerais.find((p) => p.chave === chave)?.valor ?? padrao;
}
