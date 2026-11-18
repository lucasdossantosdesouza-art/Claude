package br.com.comissao.os;

import br.com.comissao.arquivo.ArmazenamentoService;
import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.cliente.Cliente;
import br.com.comissao.cliente.ClienteRepository;
import br.com.comissao.comissao.Comissao;
import br.com.comissao.comissao.ComissaoRepository;
import br.com.comissao.comissao.ComissaoService;
import br.com.comissao.comissao.RegraComissaoService;
import br.com.comissao.comissao.StatusComissao;
import br.com.comissao.os.OsDtos.*;
import br.com.comissao.parametro.MotivoEstorno;
import br.com.comissao.parametro.MotivoEstornoRepository;
import br.com.comissao.posvenda.Ocorrencia;
import br.com.comissao.posvenda.OcorrenciaRepository;
import br.com.comissao.producao.AvisoRepository;
import br.com.comissao.producao.BloqueioCalendarioRepository;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static br.com.comissao.shared.Valores.*;

/** Casos de uso da Ordem de Serviço (perfil Vendedor e Gestor). */
@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private final OrdemServicoRepository ordens;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final ComissaoRepository comissoes;
    private final ComissaoService comissaoService;
    private final RegraComissaoService regras;
    private final MotivoEstornoRepository motivos;
    private final OcorrenciaRepository ocorrencias;
    private final AvisoRepository avisos;
    private final BloqueioCalendarioRepository bloqueios;
    private final ArmazenamentoService armazenamento;
    private final AuditoriaService auditoria;
    private final Clock clock;

    // ---------------------------------------------------------------- consulta

    public record FiltroOs(String q, SituacaoOs situacao, YearMonth mes, Long vendedorId) {
    }

    @Transactional(readOnly = true)
    public List<OsResumo> listar(FiltroOs filtro, Usuario usuario) {
        Long vendedorId = usuario.isVendedor() ? usuario.getId() : filtro.vendedorId();
        Specification<OrdemServico> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            var cliente = root.join("cliente", JoinType.INNER);
            if (vendedorId != null) {
                ps.add(cb.equal(root.get("vendedor").get("id"), vendedorId));
            }
            if (filtro.situacao() != null) {
                ps.add(cb.equal(root.get("situacao"), filtro.situacao()));
            }
            if (filtro.mes() != null) {
                ps.add(cb.between(root.get("dataEmissao"), filtro.mes().atDay(1), filtro.mes().atEndOfMonth()));
            }
            if (filtro.q() != null && !filtro.q().isBlank()) {
                String like = "%" + filtro.q().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(cliente.get("nome")), like),
                        cb.like(cb.lower(cb.coalesce(cliente.get("razaoSocial"), "")), like),
                        cb.like(cb.coalesce(cliente.get("cnpj"), ""), like),
                        cb.like(cb.coalesce(root.get("codigo"), ""), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        List<OrdemServico> lista = ordens.findAll(spec, PageRequest.of(0, 500,
                Sort.by(Sort.Order.desc("dataEmissao"), Sort.Order.desc("id")))).getContent();
        return lista.stream().map(this::resumo).toList();
    }

    public OsResumo resumo(OrdemServico o) {
        LocalDate hoje = LocalDate.now(clock);
        BigDecimal comissaoAPagar = comissoes.findByOsId(o.getId()).stream()
                .filter(c -> c.getStatus() == StatusComissao.AUTORIZADA || c.getStatus() == StatusComissao.PROVISORIA)
                .map(Comissao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean atrasada = !o.isCancelada() && o.getParcelas().stream().anyMatch(p -> p.isAtrasada(hoje));
        return new OsResumo(o.getId(), o.getCodigo(), o.getDataEmissao(), o.getPrevisaoEntrega(), o.isEntregue(),
                o.getCliente().getId(), o.getCliente().getNome(), o.getVendedor().getId(), o.getVendedor().getNome(),
                o.getSituacao(), o.isMeiaMargem(), o.getTotal(), o.totalRecebido(), comissaoAPagar, atrasada);
    }

    @Transactional(readOnly = true)
    public OsDetalhe detalhe(Long id, Usuario usuario) {
        OrdemServico o = carregar(id, usuario);
        LocalDate hoje = LocalDate.now(clock);
        Map<Long, Comissao> porParcela = comissoes.findByOsId(id).stream()
                .collect(Collectors.toMap(c -> c.getParcela().getId(), c -> c));
        boolean verComissao = usuario.getPerfil() != Perfil.PRODUCAO;
        List<ParcelaDto> parcelas = o.getParcelas().stream().map(p -> {
            Comissao c = verComissao ? porParcela.get(p.getId()) : null;
            return new ParcelaDto(p.getId(), p.getNumero(), p.getForma(), p.getValor(), p.getVencimento(), p.getStatus(),
                    p.getDataRecebimento(), p.getComprovante(), p.isAtrasada(hoje),
                    c == null ? null : c.getValor(), c == null ? null : c.getPercentual(), c == null ? null : c.getStatus());
        }).toList();
        ResumoComissaoOs resumoComissao = null;
        if (verComissao) {
            var pct = regras.percentualDaOs(o);
            resumoComissao = new ResumoComissaoOs(pct.percentual(), pct.travado(), pct.faixa(),
                    somar(porParcela.values(), StatusComissao.PENDENTE_RECEBIMENTO),
                    somar(porParcela.values(), StatusComissao.AUTORIZADA, StatusComissao.PROVISORIA),
                    somar(porParcela.values(), StatusComissao.PAGA));
        }
        List<String> bloqueiosEntrega = o.getPrevisaoEntrega() == null || o.isEntregue() ? List.of()
                : bloqueios.findByData(o.getPrevisaoEntrega()).stream().map(b -> b.getProduto().getNome()).toList();
        return new OsDetalhe(o.getId(), o.getCodigo(), o.getSituacao(), o.getDataEmissao(), o.getHora(),
                o.getPrevisaoEntrega(), o.getVendedor().getId(), o.getVendedor().getNome(), o.getServicos(),
                o.getObservacoes(), o.getTotalProdutos(), o.getTotalServicos(), o.getDesconto(), o.getTotal(),
                o.totalRecebido(), o.getPrazoPagamentoDias(), o.isMeiaMargem(), o.getDocumentoOrigem(),
                o.getCancelMotivo(), o.getCancelData(), o.isEstorno(),
                o.getEstornoMotivo() == null ? null : o.getEstornoMotivo().getNome(), o.getEstornoValor(),
                o.getEstornoComprovante(), o.isEntregue(), o.getDataEntregaReal(), clienteDto(o.getCliente()),
                o.getItens().stream().map(i -> new ItemDto(i.getId(), i.getTipo(), i.getCodigo(), i.getDescricao(),
                        i.getUnidade(), i.getQuantidade(), i.getValorUnitario(), i.getDesconto(), i.getValorTotal())).toList(),
                parcelas, resumoComissao,
                ocorrencias.findByOsIdOrderByIdDesc(id).stream().map(OrdemServicoService::ocorrenciaDto).toList(),
                auditoria.daOs(id),
                avisos.findByAtivoTrueAndOsId(id).stream().map(a -> new AvisoResumo(a.getId(), a.getTexto(), a.isUrgente())).toList(),
                bloqueiosEntrega);
    }

    private static BigDecimal somar(Collection<Comissao> lista, StatusComissao... status) {
        Set<StatusComissao> s = EnumSet.copyOf(Arrays.asList(status));
        return lista.stream().filter(c -> s.contains(c.getStatus())).map(Comissao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static ClienteDto clienteDto(Cliente c) {
        return new ClienteDto(c.getId(), c.getNome(), c.getRazaoSocial(), c.getCnpj(), c.getCodigo(), c.getConta(),
                c.getTelefone(), c.getEndereco(), c.getNumero(), c.getBairro(), c.getCidade(), c.getUf(), c.getCep());
    }

    public static OcorrenciaDto ocorrenciaDto(Ocorrencia oc) {
        OrdemServico o = oc.getOs();
        return new OcorrenciaDto(oc.getId(), o.getId(), o.getCodigo(), o.getCliente().getNome(), o.getVendedor().getNome(),
                oc.getTipo(), oc.getDescricao(), oc.getStatus(), oc.getUsuario() == null ? null : oc.getUsuario().getNome(),
                oc.getCriadoEm(), oc.getResolvidoEm());
    }

    /** Carrega a OS garantindo que o vendedor só acesse as próprias. */
    public OrdemServico carregar(Long id, Usuario usuario) {
        OrdemServico o = ordens.findById(id).orElseThrow(() -> new NaoEncontradoException("OS não encontrada"));
        if (usuario.isVendedor() && !o.getVendedor().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("OS de outro vendedor");
        }
        return o;
    }

    // ---------------------------------------------------------------- criação / edição

    @Transactional
    public OrdemServico salvar(Long id, OsRequest req, Usuario usuario) {
        if (usuario.getPerfil() == Perfil.PRODUCAO) {
            throw new AccessDeniedException("Perfil de produção não cria OS");
        }
        Usuario vendedor = usuario.isVendedor() ? usuario : usuarios.findById(Optional.ofNullable(req.vendedorId())
                        .orElseThrow(() -> new NegocioException("Selecione o vendedor.")))
                .orElseThrow(() -> new NaoEncontradoException("Vendedor não encontrado"));

        OrdemServico os;
        Set<YearMonth> mesesAfetados = new HashSet<>();
        List<String> mudancas = new ArrayList<>();
        if (id == null) {
            os = new OrdemServico();
            os.setCriadoEm(LocalDateTime.now(clock));
        } else {
            os = carregar(id, usuario);
            if (os.isCancelada()) {
                throw new NegocioException("OS cancelada não pode ser editada.");
            }
            mesesAfetados.add(YearMonth.from(os.getDataEmissao()));
            registrarMudanca(mudancas, "total", formatarMoeda(os.getTotal()), formatarMoeda(req.total()));
            registrarMudanca(mudancas, "emissão", formatarData(os.getDataEmissao()), formatarData(req.dataEmissao()));
            registrarMudanca(mudancas, "entrega", formatarData(os.getPrevisaoEntrega()), formatarData(req.previsaoEntrega()));
            registrarMudanca(mudancas, "meia margem", String.valueOf(os.isMeiaMargem()), String.valueOf(req.meiaMargem()));
            registrarMudanca(mudancas, "vendedor", os.getVendedor().getNome(), vendedor.getNome());
        }
        os.setCodigo(blankToNull(req.codigo()));
        os.setVendedor(vendedor);
        os.setCliente(salvarCliente(req.cliente()));
        os.setDataEmissao(req.dataEmissao());
        os.setHora(req.hora() != null && !req.hora().isBlank() ? req.hora()
                : LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm")));
        os.setPrevisaoEntrega(req.previsaoEntrega());
        os.setServicos(req.servicos());
        os.setObservacoes(req.observacoes());
        os.setDesconto(moeda(req.desconto()));
        os.setTotal(moeda(req.total()));
        os.setPrazoPagamentoDias(req.prazoPagamentoDias());
        os.setMeiaMargem(req.meiaMargem());
        if (req.documentoOrigem() != null && !req.documentoOrigem().isBlank()) {
            os.setDocumentoOrigem(req.documentoOrigem());
        }

        os.getItens().clear();
        for (ItemDto i : Optional.ofNullable(req.itens()).orElse(List.of())) {
            ItemOs item = new ItemOs();
            item.setTipo(i.tipo() == null ? TipoItem.PRODUTO : i.tipo());
            item.setCodigo(i.codigo());
            item.setDescricao(i.descricao());
            item.setUnidade(i.unidade());
            item.setQuantidade(i.quantidade() == null ? BigDecimal.ONE : i.quantidade());
            item.setValorUnitario(moeda(i.valorUnitario()));
            item.setDesconto(moeda(i.desconto()));
            item.setValorTotal(i.valorTotal() != null ? moeda(i.valorTotal())
                    : moeda(item.getQuantidade().multiply(item.getValorUnitario()).subtract(item.getDesconto())));
            os.adicionarItem(item);
        }
        os.setTotalProdutos(somaItens(os, TipoItem.PRODUTO));
        os.setTotalServicos(somaItens(os, TipoItem.SERVICO));

        atualizarParcelas(os, req.parcelas(), mudancas);
        os.renumerarParcelas();
        os.atualizarSituacao();
        os = ordens.saveAndFlush(os);

        mesesAfetados.add(YearMonth.from(os.getDataEmissao()));
        mesesAfetados.forEach(comissaoService::sincronizarMes);

        if (id == null) {
            auditoria.registrar(os.getId(), usuario, "CRIACAO", "OS criada — total " + formatarMoeda(os.getTotal()) + ", "
                    + os.getParcelas().size() + " parcela(s)" + (os.isMeiaMargem() ? " — Meia Margem" : "")
                    + (os.getDocumentoOrigem() != null ? " — via leitura de documento" : ""));
        } else {
            auditoria.registrar(os.getId(), usuario, "EDICAO", mudancas.isEmpty() ? "Itens/parcelas revisados" : String.join("; ", mudancas));
        }
        return os;
    }

    private void atualizarParcelas(OrdemServico os, List<ParcelaRequest> novas, List<String> mudancas) {
        Map<Long, Parcela> existentes = os.getParcelas().stream().filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Parcela::getId, p -> p));
        Set<Long> mantidas = new HashSet<>();
        for (ParcelaRequest pr : novas) {
            Parcela p = pr.id() == null ? null : existentes.get(pr.id());
            if (p != null) {
                mantidas.add(p.getId());
                if (p.isPendente()) {
                    p.setForma(pr.forma());
                    p.setValor(moeda(pr.valor()));
                    p.setVencimento(pr.vencimento());
                }
            } else {
                Parcela nova = new Parcela();
                nova.setForma(pr.forma());
                nova.setValor(moeda(pr.valor()));
                nova.setVencimento(pr.vencimento());
                os.adicionarParcela(nova);
            }
        }
        // parcelas pendentes removidas da tela são excluídas; recebidas nunca são excluídas.
        // Considera só as que já existiam: o auto-flush da consulta abaixo atribui ID às parcelas novas.
        for (Parcela p : existentes.values()) {
            if (!mantidas.contains(p.getId()) && p.isPendente()) {
                comissoes.findByParcelaId(p.getId()).ifPresent(comissoes::delete);
                os.getParcelas().remove(p);
                mudancas.add("parcela " + p.getNumero() + " (" + formatarMoeda(p.getValor()) + ") removida");
            }
        }
    }

    private Cliente salvarCliente(ClienteDto dto) {
        Cliente c = null;
        if (dto.id() != null) {
            c = clientes.findById(dto.id()).orElse(null);
        }
        if (c == null && dto.cnpj() != null && !dto.cnpj().isBlank()) {
            c = clientes.findFirstByCnpj(dto.cnpj().trim()).orElse(null);
        }
        if (c == null) {
            c = new Cliente();
        }
        c.setNome(dto.nome().trim());
        c.setRazaoSocial(blankToNull(dto.razaoSocial()));
        c.setCnpj(blankToNull(dto.cnpj()));
        c.setCodigo(blankToNull(dto.codigo()));
        c.setConta(blankToNull(dto.conta()));
        c.setTelefone(blankToNull(dto.telefone()));
        c.setEndereco(blankToNull(dto.endereco()));
        c.setNumero(blankToNull(dto.numero()));
        c.setBairro(blankToNull(dto.bairro()));
        c.setCidade(blankToNull(dto.cidade()));
        c.setUf(dto.uf() == null || dto.uf().isBlank() ? null : dto.uf().trim().toUpperCase(Locale.ROOT));
        c.setCep(blankToNull(dto.cep()));
        return clientes.save(c);
    }

    private static BigDecimal somaItens(OrdemServico os, TipoItem tipo) {
        return moeda(os.getItens().stream().filter(i -> i.getTipo() == tipo).map(ItemOs::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static void registrarMudanca(List<String> lista, String campo, String antes, String depois) {
        if (!Objects.equals(antes, depois)) {
            lista.add(campo + ": " + antes + " → " + depois);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---------------------------------------------------------------- recebimentos

    @Transactional
    public OrdemServico receberParcela(Long osId, Long parcelaId, LocalDate dataRecebimento, MultipartFile comprovante, Usuario usuario) {
        OrdemServico os = carregar(osId, usuario);
        if (usuario.getPerfil() == Perfil.PRODUCAO) {
            throw new AccessDeniedException("Perfil sem permissão");
        }
        if (os.isCancelada()) {
            throw new NegocioException("OS cancelada.");
        }
        Parcela p = parcela(os, parcelaId);
        if (!p.isPendente()) {
            throw new NegocioException("Parcela já recebida.");
        }
        String arquivo = armazenamento.salvar(comprovante, "comprovante_os" + osId);
        LocalDate data = dataRecebimento == null ? LocalDate.now(clock) : dataRecebimento;
        p.setStatus(StatusParcela.RECEBIDA);
        p.setDataRecebimento(data);
        p.setComprovante(arquivo);
        os.atualizarSituacao();
        ordens.saveAndFlush(os);
        comissaoService.sincronizar(os);
        auditoria.registrar(osId, usuario, "RECEBIMENTO", "Parcela " + p.getNumero() + " (" + p.getForma() + " "
                + formatarMoeda(p.getValor()) + ") recebida em " + formatarData(data) + " — comprovante anexado"
                + (os.getSituacao() == SituacaoOs.FECHADA ? " — OS fechada" : ""));
        return os;
    }

    @Transactional
    public OrdemServico desfazerRecebimento(Long osId, Long parcelaId, Usuario gestor) {
        OrdemServico os = carregar(osId, gestor);
        Parcela p = parcela(os, parcelaId);
        comissoes.findByParcelaId(parcelaId).ifPresent(c -> {
            if (c.getStatus() == StatusComissao.PAGA) {
                throw new NegocioException("A comissão desta parcela já foi paga; não é possível desfazer.");
            }
        });
        p.setStatus(StatusParcela.PENDENTE);
        p.setDataRecebimento(null);
        os.atualizarSituacao();
        ordens.saveAndFlush(os);
        comissaoService.sincronizar(os);
        auditoria.registrar(osId, gestor, "RECEBIMENTO_DESFEITO", "Recebimento da parcela " + p.getNumero() + " desfeito");
        return os;
    }

    private static Parcela parcela(OrdemServico os, Long parcelaId) {
        return os.getParcelas().stream().filter(x -> x.getId().equals(parcelaId)).findFirst()
                .orElseThrow(() -> new NaoEncontradoException("Parcela não encontrada"));
    }

    // ---------------------------------------------------------------- cancelamento / estorno

    @Transactional
    public OrdemServico cancelar(Long osId, CancelamentoRequest req, MultipartFile comprovanteEstorno, Usuario usuario) {
        OrdemServico os = carregar(osId, usuario);
        if (usuario.getPerfil() == Perfil.PRODUCAO) {
            throw new AccessDeniedException("Perfil sem permissão");
        }
        if (os.isCancelada()) {
            throw new NegocioException("OS já cancelada.");
        }
        if (req.motivo() == null || req.motivo().trim().length() < 5) {
            throw new NegocioException("A justificativa do cancelamento é obrigatória.");
        }
        os.setSituacao(SituacaoOs.CANCELADA);
        os.setCancelMotivo(req.motivo().trim());
        os.setCancelData(LocalDate.now(clock));
        if (req.estorno()) {
            if (req.motivoEstornoId() == null || comprovanteEstorno == null || comprovanteEstorno.isEmpty()) {
                throw new NegocioException("Para estorno, anexe o comprovante e selecione o motivo.");
            }
            MotivoEstorno m = motivos.findById(req.motivoEstornoId()).orElseThrow(() -> new NaoEncontradoException("Motivo não encontrado"));
            os.setEstorno(true);
            os.setEstornoMotivo(m);
            os.setEstornoValor(moeda(req.valorEstorno()));
            os.setEstornoComprovante(armazenamento.salvar(comprovanteEstorno, "estorno_os" + osId));
        }
        os.getParcelas().stream().filter(Parcela::isPendente).forEach(p -> p.setStatus(StatusParcela.CANCELADA));
        ordens.saveAndFlush(os);
        comissaoService.sincronizarMes(YearMonth.from(os.getDataEmissao()));
        auditoria.registrar(osId, usuario, "CANCELAMENTO", "Motivo: " + os.getCancelMotivo()
                + (os.isEstorno() ? " — estorno de " + formatarMoeda(os.getEstornoValor()) + " (" + os.getEstornoMotivo().getNome() + ")" : ""));
        return os;
    }

    // ---------------------------------------------------------------- pós-venda

    @Transactional
    public OrdemServico confirmarEntrega(Long osId, LocalDate data, Usuario usuario) {
        OrdemServico os = carregar(osId, usuario);
        LocalDate d = data == null ? LocalDate.now(clock) : data;
        os.setEntregue(true);
        os.setDataEntregaReal(d);
        String prazo = "";
        if (os.getPrevisaoEntrega() != null) {
            long dias = ChronoUnit.DAYS.between(os.getPrevisaoEntrega(), d);
            prazo = dias > 0 ? " (" + dias + " dia(s) de atraso)" : " (no prazo)";
        }
        auditoria.registrar(osId, usuario, "ENTREGA", "Entregue/retirado em " + formatarData(d) + prazo);
        return os;
    }

    @Transactional
    public Ocorrencia registrarOcorrencia(Long osId, OcorrenciaRequest req, Usuario usuario) {
        OrdemServico os = carregar(osId, usuario);
        Ocorrencia oc = new Ocorrencia();
        oc.setOs(os);
        oc.setTipo(req.tipo());
        oc.setDescricao(req.descricao().trim());
        oc.setUsuario(usuario);
        oc.setCriadoEm(LocalDateTime.now(clock));
        auditoria.registrar(osId, usuario, "POS_VENDA", "Ocorrência registrada: " + req.tipo());
        return ocorrencias.save(oc);
    }

    @Transactional
    public void resolverOcorrencia(Long ocorrenciaId, Usuario usuario) {
        Ocorrencia oc = ocorrencias.findById(ocorrenciaId).orElseThrow(() -> new NaoEncontradoException("Ocorrência não encontrada"));
        carregar(oc.getOs().getId(), usuario);
        oc.setStatus("RESOLVIDA");
        oc.setResolvidoEm(LocalDateTime.now(clock));
        auditoria.registrar(oc.getOs().getId(), usuario, "POS_VENDA", "Ocorrência " + oc.getTipo() + " resolvida");
    }
}
