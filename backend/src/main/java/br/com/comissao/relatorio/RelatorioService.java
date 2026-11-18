package br.com.comissao.relatorio;

import br.com.comissao.comissao.ComissaoConsultaService;
import br.com.comissao.comissao.ComissaoConsultaService.ComissaoDto;
import br.com.comissao.comissao.ComissaoConsultaService.ResumoVendedor;
import br.com.comissao.financeiro.FinanceiroService;
import br.com.comissao.financeiro.FinanceiroService.ParcelaReceber;
import br.com.comissao.usuario.Usuario;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static br.com.comissao.shared.Valores.*;

/** Exportação dos fechamentos mensais (comissão e contas a receber) em Excel e PDF. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final ComissaoConsultaService comissoes;
    private final FinanceiroService financeiro;
    private final Clock clock;

    // ------------------------------------------------------------ Excel

    public byte[] comissoesExcel(YearMonth mes, Usuario gestor) {
        var dados = comissoes.doMes(mes, gestor, null, null);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            Sheet s = wb.createSheet("Resumo");
            Row t = s.createRow(0);
            t.createCell(0).setCellValue("Fechamento de comissões — mês de referência " + mes + (dados.fechado() ? " (fechado)" : " (aberto)"));
            t.getCell(0).setCellStyle(e.titulo);
            s.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
            cabecalho(s, 2, e, "Vendedor", "Total vendido", "Faixa %", "Travada", "Fixo", "Comissão autorizada",
                    "Comissão provisória", "Comissão paga", "Pendente receb.", "Bônus meta semanal");
            int r = 3;
            for (ResumoVendedor v : dados.resumo()) {
                Row row = s.createRow(r++);
                texto(row, 0, v.vendedor());
                moeda(row, 1, v.totalVendido(), e);
                pct(row, 2, v.percentual(), e);
                texto(row, 3, v.travado() ? "Sim" : "Não");
                moeda(row, 4, v.salarioFixo(), e);
                moeda(row, 5, v.autorizada(), e);
                moeda(row, 6, v.provisoria(), e);
                moeda(row, 7, v.paga(), e);
                moeda(row, 8, v.pendente(), e);
                moeda(row, 9, v.bonusSemanal(), e);
            }
            larguras(s, 26, 16, 10, 10, 12, 20, 20, 16, 16, 18);

            Sheet d = wb.createSheet("Detalhe por parcela");
            cabecalho(d, 0, e, "Vendedor", "OS", "Cliente", "Parcela", "Forma", "Vencimento", "Recebida em", "Base", "%",
                    "Meia margem", "Comissão", "Status");
            r = 1;
            for (ComissaoDto c : dados.detalhe()) {
                Row row = d.createRow(r++);
                texto(row, 0, c.vendedor());
                texto(row, 1, c.osCodigo() == null ? String.valueOf(c.osId()) : c.osCodigo());
                texto(row, 2, c.cliente());
                row.createCell(3).setCellValue(c.parcela());
                texto(row, 4, c.forma());
                texto(row, 5, formatarData(c.vencimento()));
                texto(row, 6, formatarData(c.dataRecebimento()));
                moeda(row, 7, c.base(), e);
                pct(row, 8, c.percentual(), e);
                texto(row, 9, c.meiaMargem() ? "Sim" : "Não");
                moeda(row, 10, c.valor(), e);
                texto(row, 11, c.status().getDescricao());
            }
            larguras(d, 22, 10, 30, 8, 14, 12, 12, 14, 8, 11, 14, 24);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public byte[] contasReceberExcel(LocalDate inicio, LocalDate fim) {
        List<ParcelaReceber> linhas = financeiro.contasAReceber(inicio, fim, null, null);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            Sheet s = wb.createSheet("Contas a receber");
            cabecalho(s, 0, e, "Vencimento", "Cliente", "OS", "Parcela", "Forma", "Vendedor", "Valor", "Atrasada");
            int r = 1;
            for (ParcelaReceber p : linhas) {
                Row row = s.createRow(r++);
                texto(row, 0, formatarData(p.vencimento()));
                texto(row, 1, p.cliente());
                texto(row, 2, p.osCodigo() == null ? String.valueOf(p.osId()) : p.osCodigo());
                row.createCell(3).setCellValue(p.numero());
                texto(row, 4, p.forma());
                texto(row, 5, p.vendedor());
                moeda(row, 6, p.valor(), e);
                texto(row, 7, p.atrasada() ? "Sim (" + p.diasAtraso() + " dias)" : "");
            }
            Row tot = s.createRow(r + 1);
            texto(tot, 5, "TOTAL");
            Cell c = tot.createCell(6);
            c.setCellFormula("SUM(G2:G" + Math.max(2, r) + ")");
            c.setCellStyle(e.moeda);
            larguras(s, 12, 32, 10, 8, 14, 22, 14, 14);

            Sheet m = wb.createSheet("Por mês");
            cabecalho(m, 0, e, "Mês", "Total a receber");
            r = 1;
            for (var en : porMes(linhas).entrySet()) {
                Row row = m.createRow(r++);
                texto(row, 0, en.getKey());
                moeda(row, 1, en.getValue(), e);
            }
            larguras(m, 12, 18);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Map<String, BigDecimal> porMes(List<ParcelaReceber> l) {
        Map<String, BigDecimal> m = new TreeMap<>();
        l.forEach(p -> m.merge(YearMonth.from(p.vencimento()).toString(), p.valor(), BigDecimal::add));
        return m;
    }

    private static final class Estilos {
        final CellStyle cabecalho, moeda, pct, titulo;

        Estilos(Workbook wb) {
            Font branca = wb.createFont();
            branca.setBold(true);
            branca.setColor(IndexedColors.WHITE.getIndex());
            cabecalho = wb.createCellStyle();
            cabecalho.setFont(branca);
            cabecalho.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            DataFormat df = wb.createDataFormat();
            moeda = wb.createCellStyle();
            moeda.setDataFormat(df.getFormat("\"R$\" #,##0.00"));
            pct = wb.createCellStyle();
            pct.setDataFormat(df.getFormat("0.00%"));
            Font grande = wb.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo = wb.createCellStyle();
            titulo.setFont(grande);
        }
    }

    private static void cabecalho(Sheet s, int linha, Estilos e, String... titulos) {
        Row r = s.createRow(linha);
        for (int i = 0; i < titulos.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(titulos[i]);
            c.setCellStyle(e.cabecalho);
        }
    }

    private static void texto(Row r, int col, String v) {
        r.createCell(col).setCellValue(v == null ? "" : v);
    }

    private static void moeda(Row r, int col, BigDecimal v, Estilos e) {
        Cell c = r.createCell(col);
        c.setCellValue(nz(v).doubleValue());
        c.setCellStyle(e.moeda);
    }

    private static void pct(Row r, int col, BigDecimal v, Estilos e) {
        Cell c = r.createCell(col);
        c.setCellValue(nz(v).doubleValue() / 100);
        c.setCellStyle(e.pct);
    }

    private static void larguras(Sheet s, int... w) {
        for (int i = 0; i < w.length; i++) {
            s.setColumnWidth(i, w[i] * 256);
        }
    }

    // ------------------------------------------------------------ PDF

    public byte[] comissoesPdf(YearMonth mes, Usuario gestor) {
        var dados = comissoes.doMes(mes, gestor, null, null);
        PdfPTable resumo = tabela("Vendedor", "Total vendido", "Faixa", "Travada", "Fixo", "Autorizada", "Provisória",
                "Paga", "Pendente", "Bônus semanal");
        for (ResumoVendedor v : dados.resumo()) {
            linha(resumo, v.vendedor(), formatarMoeda(v.totalVendido()), formatarPercentual(v.percentual()),
                    v.travado() ? "Sim" : "Não", formatarMoeda(v.salarioFixo()), formatarMoeda(v.autorizada()),
                    formatarMoeda(v.provisoria()), formatarMoeda(v.paga()), formatarMoeda(v.pendente()),
                    formatarMoeda(v.bonusSemanal()));
        }
        PdfPTable detalhe = tabela("Vendedor", "OS", "Cliente", "Parc.", "Forma", "Receb.", "Base", "%", "Comissão", "Status");
        for (ComissaoDto c : dados.detalhe()) {
            linha(detalhe, c.vendedor(), c.osCodigo() == null ? String.valueOf(c.osId()) : c.osCodigo(), c.cliente(),
                    String.valueOf(c.parcela()), c.forma(), formatarData(c.dataRecebimento()), formatarMoeda(c.base()),
                    formatarPercentual(c.percentual()), formatarMoeda(c.valor()), c.status().getDescricao());
        }
        return pdf("Fechamento de comissões — " + mes, new Object[]{"Resumo por vendedor", resumo, "Detalhe por parcela", detalhe});
    }

    public byte[] contasReceberPdf(LocalDate inicio, LocalDate fim) {
        List<ParcelaReceber> linhas = financeiro.contasAReceber(inicio, fim, null, null);
        PdfPTable t = tabela("Vencimento", "Cliente", "OS", "Parc.", "Forma", "Vendedor", "Valor");
        BigDecimal total = BigDecimal.ZERO;
        for (ParcelaReceber p : linhas) {
            linha(t, formatarData(p.vencimento()) + (p.atrasada() ? " (atrasada)" : ""), p.cliente(),
                    p.osCodigo() == null ? String.valueOf(p.osId()) : p.osCodigo(), String.valueOf(p.numero()), p.forma(),
                    p.vendedor(), formatarMoeda(p.valor()));
            total = total.add(p.valor());
        }
        linha(t, "", "", "", "", "", "TOTAL", formatarMoeda(total));
        return pdf("Contas a receber", new Object[]{"Parcelas pendentes", t});
    }

    private static PdfPTable tabela(String... titulos) {
        PdfPTable t = new PdfPTable(titulos.length);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        com.lowagie.text.Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String s : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(s, f));
            c.setBackgroundColor(new Color(0x1F, 0x4E, 0x79));
            c.setPadding(4);
            t.addCell(c);
        }
        return t;
    }

    private static void linha(PdfPTable t, String... valores) {
        com.lowagie.text.Font f = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (String v : valores) {
            PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, f));
            c.setPadding(3);
            t.addCell(c);
        }
    }

    private byte[] pdf(String titulo, Object[] blocos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        doc.add(new Paragraph("Gerado em " + formatarData(LocalDate.now(clock)), FontFactory.getFont(FontFactory.HELVETICA, 9)));
        for (int i = 0; i < blocos.length; i += 2) {
            Paragraph sub = new Paragraph((String) blocos[i], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            sub.setSpacingBefore(12);
            sub.setSpacingAfter(6);
            doc.add(sub);
            doc.add((PdfPTable) blocos[i + 1]);
        }
        doc.close();
        return out.toByteArray();
    }
}
