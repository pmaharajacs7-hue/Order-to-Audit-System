package com.orderaudit.sales;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/** Renders a GST tax invoice as a PDF. Amounts use "Rs." because the built-in PDF fonts have no rupee glyph. */
@Service
public class InvoicePdfService {

    private final String companyName;
    private final String companyGstin;
    private final String companyAddress;

    public InvoicePdfService(@Value("${app.company.name:Company}") String companyName,
                             @Value("${app.company.gstin:}") String companyGstin,
                             @Value("${app.company.address:}") String companyAddress) {
        this.companyName = companyName;
        this.companyGstin = companyGstin;
        this.companyAddress = companyAddress;
    }

    public byte[] render(InvoiceService.InvoiceResponse inv) {
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph("TAX INVOICE", title));
            doc.add(new Paragraph(companyName, bold));
            doc.add(new Paragraph(companyAddress + "   GSTIN: " + companyGstin, normal));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Invoice No: " + inv.invoiceNo() + "     Date: " + inv.invoiceDate()
                    + "     Status: " + inv.status(), normal));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Bill to: " + inv.customerName(), bold));
            if (inv.customerAddress() != null) doc.add(new Paragraph(inv.customerAddress(), normal));
            doc.add(new Paragraph("GSTIN: " + (inv.customerGstin() == null ? "-" : inv.customerGstin())
                    + "     State code: " + inv.customerStateCode(), normal));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{4f, 1.6f, 1f, 1.5f, 1.8f, 1f, 1.8f, 1.8f});
            table.setWidthPercentage(100);
            for (String h : new String[]{"Item", "HSN", "Qty", "Rate", "Taxable", "GST%", "Tax", "Amount"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, bold));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }
            for (InvoiceService.InvoiceLine l : inv.items()) {
                BigDecimal tax = l.cgst().add(l.sgst()).add(l.igst());
                table.addCell(new Phrase(l.productName(), normal));
                table.addCell(new Phrase(l.hsnCode(), normal));
                table.addCell(right(String.valueOf(l.quantity()), normal));
                table.addCell(right(l.unitPrice().toPlainString(), normal));
                table.addCell(right(l.taxableValue().toPlainString(), normal));
                table.addCell(right(l.gstRatePct().stripTrailingZeros().toPlainString(), normal));
                table.addCell(right(tax.toPlainString(), normal));
                table.addCell(right(l.taxableValue().add(tax).toPlainString(), normal));
            }
            doc.add(table);
            doc.add(new Paragraph(" "));

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(45);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            row(totals, "Taxable value", inv.subtotal(), normal);
            if (inv.igst().signum() > 0) {
                row(totals, "IGST", inv.igst(), normal);
            } else {
                row(totals, "CGST", inv.cgst(), normal);
                row(totals, "SGST", inv.sgst(), normal);
            }
            row(totals, "Total", inv.total(), bold);
            row(totals, "Paid", inv.paid(), normal);
            row(totals, "Balance due", inv.balance(), bold);
            doc.add(totals);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("This is a computer-generated invoice.", normal));
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build invoice PDF", e);
        }
        return out.toByteArray();
    }

    private static PdfPCell right(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static void row(PdfPTable t, String label, BigDecimal amount, Font font) {
        t.addCell(new Phrase(label, font));
        t.addCell(right("Rs. " + amount.toPlainString(), font));
    }
}
