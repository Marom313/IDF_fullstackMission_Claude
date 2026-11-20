package il.ipp.permits.adapters.out.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.GrayColor;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitPorts.DocumentRenderer;
import il.ipp.permits.domain.PermitPorts.PersonRef;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SPECIMEN permit PDF with a signed QR. Fixed diagonal watermark "SPECIMEN / דוגמה – אינו בתוקף" on every page.
 * No official emblems (GAPS G-01). Font: DejaVu Sans (installed in the runtime image, offline).
 */
@Component
class SpecimenPdfRenderer implements DocumentRenderer {
  private static final String FONT = "/usr/share/fonts/dejavu/DejaVuSans.ttf";
  private static final Color NAVY = new Color(0x1f, 0x3a, 0x5f);

  @Override
  public byte[] permitPdf(Permit p, PersonRef person, String qr) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 48, 48, 56, 56);
      PdfWriter writer = PdfWriter.getInstance(doc, out);
      doc.open();
      BaseFont bf = Files.exists(Path.of(FONT))
          ? BaseFont.createFont(FONT, BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
          : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
      Font title = new Font(bf, 20, Font.BOLD, NAVY);
      Font label = new Font(bf, 10, Font.NORMAL, Color.DARK_GRAY);
      Font value = new Font(bf, 12, Font.BOLD, Color.BLACK);

      Paragraph h = new Paragraph("IPP – Israel Permit Program (DEMO)", title);
      h.setAlignment(Element.ALIGN_LEFT);
      doc.add(h);
      PdfPTable heb = new PdfPTable(1);
      heb.setWidthPercentage(100);
      heb.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
      PdfPCell hc = new PdfPCell(new Phrase("היתר " + hebrewType(p.type().name()) + " – מסמך לדוגמה", title));
      hc.setBorder(Rectangle.NO_BORDER);
      hc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
      heb.addCell(hc);
      doc.add(heb);
      doc.add(new Paragraph(" "));

      PdfPTable t = new PdfPTable(new float[] {1f, 2f});
      t.setWidthPercentage(100);
      Map<String, String> rows = new java.util.LinkedHashMap<>();
      rows.put("Permit ID", p.id().toString());
      rows.put("Type", p.type().name());
      rows.put("Status", p.status().name());
      rows.put("Holder", person.fullName());
      rows.put("ID number", p.nationalId());
      rows.put("Region", String.valueOf(p.region()));
      rows.put("Valid from", String.valueOf(p.validFrom()));
      rows.put("Valid to", String.valueOf(p.validTo()));
      rows.put("Policy version", String.valueOf(p.policyVersion()));
      for (var r : rows.entrySet()) {
        PdfPCell k = new PdfPCell(new Phrase(r.getKey(), label));
        PdfPCell v = new PdfPCell(new Phrase(r.getValue(), value));
        v.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        k.setPadding(6);
        v.setPadding(6);
        k.setBorderColor(Color.LIGHT_GRAY);
        v.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(k);
        t.addCell(v);
      }
      doc.add(t);
      doc.add(new Paragraph(" "));
      doc.add(new Paragraph("Scan at the crossing terminal. The QR is signed with Ed25519 and verified offline.", label));

      drawQr(writer.getDirectContent(), qr, 48, 120, 180);
      watermark(writer.getDirectContentUnder(), bf, doc.getPageSize());
      doc.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("PDF rendering failed", e);
    }
  }

  private static String hebrewType(String type) {
    return switch (type) {
      case "WORK_ENTRY" -> "כניסה לעבודה";
      case "DRIVER" -> "נהג הובלה";
      case "MEDICAL" -> "רפואי";
      default -> type;
    };
  }

  /** QR drawn as vector squares – no AWT/ImageIO needed in the slim runtime image. */
  private static void drawQr(PdfContentByte cb, String content, float x, float y, float size) throws Exception {
    BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
        Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
    float cell = size / m.getWidth();
    cb.saveState();
    cb.setColorFill(Color.BLACK);
    for (int r = 0; r < m.getHeight(); r++) {
      for (int c = 0; c < m.getWidth(); c++) {
        if (m.get(c, r)) {
          cb.rectangle(x + c * cell, y + size - (r + 1) * cell, cell, cell);
        }
      }
    }
    cb.fill();
    cb.restoreState();
  }

  private static void watermark(PdfContentByte under, BaseFont bf, Rectangle page) {
    PdfGState gs = new PdfGState();
    gs.setFillOpacity(0.18f);
    under.saveState();
    under.setGState(gs);
    Font wm = new Font(bf, 42, Font.BOLD, new GrayColor(0.35f));
    float cx = page.getWidth() / 2;
    float cy = page.getHeight() / 2;
    ColumnText.showTextAligned(under, Element.ALIGN_CENTER, new Phrase("SPECIMEN", wm), cx, cy + 60, 45);
    ColumnText.showTextAligned(under, Element.ALIGN_CENTER, new Phrase("דוגמה – אינו בתוקף", wm), cx - 20, cy - 20, 45,
        PdfWriter.RUN_DIRECTION_RTL, 0);
    under.restoreState();
  }
}
