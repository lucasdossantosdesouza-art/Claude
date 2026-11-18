package br.com.comissao;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo pela API REST, com tokens JWT simulados do Keycloak. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RelogioTeste.class)
class ApiIntegracaoTest {

    @Autowired MockMvc mvc;
    final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();

    static RequestPostProcessor usuario(String login, String papel) {
        return jwt().jwt(j -> j.subject("kc-" + login).claim("preferred_username", login).claim("name", "Usuário " + login))
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    static final RequestPostProcessor VENDEDOR = usuario("carla", "VENDEDOR");
    static final RequestPostProcessor OUTRO_VENDEDOR = usuario("diego", "VENDEDOR");
    static final RequestPostProcessor GESTOR = usuario("gestora", "GESTOR");
    static final RequestPostProcessor PRODUCAO = usuario("chefe", "PRODUCAO");

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/os")).andExpect(status().isUnauthorized());
    }

    @Test
    void erroDeValidacaoRetorna4xxENao403EBooleanoOmitidoEhAceito() throws Exception {
        // corpo inválido deve responder 400 (antes o /error bloqueado transformava em 403)
        mvc.perform(post("/api/os").with(VENDEDOR).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // "urgente" omitido (boolean primitivo) não pode quebrar a desserialização
        mvc.perform(post("/api/avisos").with(PRODUCAO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"GERAL\",\"texto\":\"Teste de aviso\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/os/999999").with(GESTOR)).andExpect(status().isNotFound());
    }

    @Test
    void fluxoImportarSalvarReceberECancelar() throws Exception {
        // 1. vendedor importa o PDF do SAFI
        byte[] pdf = getClass().getResourceAsStream("/os_exemplo_safi.pdf").readAllBytes();
        String imp = mvc.perform(multipart("/api/os/importacao")
                        .file(new MockMultipartFile("arquivo", "os.pdf", "application/pdf", pdf)).with(VENDEDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("PDF_TEXTO"))
                .andExpect(jsonPath("$.dados.cliente.nome").value("Pesca & Cia Esportes"))
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(imp);
        JsonNode d = r.get("dados");

        // 2. salva a OS com os dados lidos (meia margem)
        var corpo = json.createObjectNode();
        corpo.put("codigo", d.get("codigo").asText());
        corpo.put("dataEmissao", d.get("dataEmissao").asText());
        corpo.put("previsaoEntrega", d.get("previsaoEntrega").asText());
        corpo.put("total", d.get("total").decimalValue());
        corpo.put("desconto", d.get("desconto").decimalValue());
        corpo.put("meiaMargem", true);
        corpo.put("documentoOrigem", r.get("documentoOrigem").asText());
        corpo.set("cliente", d.get("cliente"));
        corpo.set("itens", d.get("itens"));
        corpo.set("parcelas", d.get("parcelas"));
        String criada = mvc.perform(post("/api/os").with(VENDEDOR).contentType(MediaType.APPLICATION_JSON).content(corpo.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelas.length()").value(3))
                .andExpect(jsonPath("$.meiaMargem").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode os = json.readTree(criada);
        long id = os.get("id").asLong();
        long parcela1 = os.get("parcelas").get(0).get("id").asLong();

        // 3. outro vendedor não acessa; produção não cria OS; vendedor não vê o financeiro
        mvc.perform(get("/api/os/" + id).with(OUTRO_VENDEDOR)).andExpect(status().isForbidden());
        mvc.perform(post("/api/os").with(PRODUCAO).contentType(MediaType.APPLICATION_JSON).content(corpo.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/financeiro/painel").with(VENDEDOR)).andExpect(status().isForbidden());

        // 4. recebimento da 1ª parcela com comprovante gera comissão provisória (mês aberto)
        mvc.perform(multipart("/api/os/" + id + "/parcelas/" + parcela1 + "/recebimento")
                        .file(new MockMultipartFile("comprovante", "pix.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}))
                        .param("dataRecebimento", "2026-11-05").with(VENDEDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelas[0].status").value("RECEBIDA"))
                .andExpect(jsonPath("$.parcelas[0].statusComissao").value("PROVISORIA"));

        // 5. telas de consulta respondem para cada perfil
        for (String url : new String[]{"/api/me", "/api/painel/vendedor", "/api/os", "/api/comissoes", "/api/metas-semanais",
                "/api/financeiro/contas-receber", "/api/calendario", "/api/posvenda", "/api/estornos", "/api/avisos",
                "/api/parametros", "/api/busca?q=Pesca", "/api/os/importacao/capacidades"}) {
            mvc.perform(get(url).with(VENDEDOR)).andExpect(status().isOk());
        }
        for (String url : new String[]{"/api/financeiro/painel", "/api/fechamentos", "/api/comprovantes", "/api/auditoria",
                "/api/vendedores", "/api/relatorios/comissoes?formato=XLSX", "/api/relatorios/comissoes?formato=PDF",
                "/api/relatorios/contas-receber?formato=XLSX", "/api/relatorios/contas-receber?formato=PDF",
                "/api/painel/vendedor"}) {
            mvc.perform(get(url).with(GESTOR)).andExpect(status().isOk());
        }
        String painel = mvc.perform(get("/api/financeiro/painel").with(GESTOR)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(painel).get("totalGeral").decimalValue()).isPositive();

        // 6. produção bloqueia a data de entrega; disponibilidade reflete
        mvc.perform(put("/api/calendario/2026-11-20").with(PRODUCAO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoIds\":[1,2],\"observacao\":\"Manutenção\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/calendario/disponibilidade?data=2026-11-20").with(VENDEDOR))
                .andExpect(jsonPath("$.bloqueios.length()").value(2));
        mvc.perform(get("/api/os/" + id).with(VENDEDOR)).andExpect(jsonPath("$.bloqueiosNaEntrega.length()").value(2));
        mvc.perform(put("/api/calendario/2026-11-20").with(VENDEDOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoIds\":[]}")).andExpect(status().isForbidden());

        // 7. parametrização só para gestor
        mvc.perform(put("/api/parametros/gerais").with(VENDEDOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"SALARIO_FIXO\": 2000}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametros/gerais").with(GESTOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"SALARIO_FIXO\": 1600}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.gerais[0].valor").value(1600));

        // 8. cancelamento exige justificativa; com justificativa cancela e aparece nos estornos
        mvc.perform(multipart("/api/os/" + id + "/cancelamento").file(new MockMultipartFile("dados", "", "application/json",
                "{\"motivo\":\"\",\"estorno\":false}".getBytes())).with(VENDEDOR)).andExpect(status().is4xxClientError());
        mvc.perform(multipart("/api/os/" + id + "/cancelamento").file(new MockMultipartFile("dados", "", "application/json",
                        "{\"motivo\":\"Cliente desistiu do pedido\",\"estorno\":false}".getBytes())).with(VENDEDOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CANCELADA"));
        mvc.perform(get("/api/estornos").with(GESTOR)).andExpect(jsonPath("$.indicador[0].quantidade").value(1));
    }
}
