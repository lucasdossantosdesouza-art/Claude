package br.com.comissao.importacao;

import br.com.comissao.config.AppProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Leitura da OS por IA (Claude): entende PDFs e fotos em qualquer layout e devolve os dados
 * no formato estruturado {@link OsExtraida} (saída validada por JSON Schema).
 */
@Component
public class LeitorIa {

    static final String PROMPT = """
            Você recebe uma Ordem de Serviço (OS) de uma empresa de confecção/comunicação visual
            (pode ser a OS impressa do sistema SAFI, uma foto dela ou outro formato).
            Extraia os dados para preencher o cadastro da OS:
            - cabeçalho (código, situação, data de emissão, hora, previsão de entrega, vendedor);
            - dados do cliente (conta, nome fantasia, razão social, CNPJ/CPF, código, telefone, endereço completo);
            - "Serviços a executar" como texto livre;
            - cada item da tabela de produtos/serviços (tipo PRODUTO ou SERVICO, código, descrição, unidade,
              quantidade, valor unitário, desconto, valor total);
            - totais (produtos, serviços, desconto geral, total do pedido);
            - prazo de pagamento em dias e cada parcela (forma, valor, vencimento);
            - observações gerais.
            Datas no formato AAAA-MM-DD; valores numéricos sem "R$" (ponto como separador decimal).
            Deixe nulo o que não estiver no documento — nunca invente valores.""";

    private final AppProperties props;
    private volatile AnthropicClient client;

    public LeitorIa(AppProperties props) {
        this.props = props;
    }

    public boolean disponivel() {
        return props.ia() != null && props.ia().habilitada();
    }

    public Optional<OsExtraida> ler(byte[] conteudo, String mime) {
        ContentBlockParam bloco = "application/pdf".equals(mime)
                ? ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(conteudo)).build())
                .build())
                : ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(conteudo))
                        .mediaType(Base64ImageSource.MediaType.of(mime))
                        .build())
                .build());

        StructuredMessageCreateParams<OsExtraida> params = MessageCreateParams.builder()
                .model(props.ia().modelo())
                .maxTokens(16000L)
                .outputConfig(OsExtraida.class)
                .addUserMessageOfContentBlockParams(List.of(bloco,
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())))
                .build();

        StructuredMessage<OsExtraida> resposta = cliente().messages().create(params);
        if (resposta.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
            return Optional.empty();
        }
        return resposta.content().stream()
                .flatMap(b -> b.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst();
    }

    private AnthropicClient cliente() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(props.ia().apiKey()).build();
                }
            }
        }
        return client;
    }
}
