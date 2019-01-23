package dcraft.util.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.IOException;
import java.nio.file.Path;

public class PdfForm {
	protected PDDocument _pdfDocument = null;

	public int load(Path originalPdf) throws IOException {
		_pdfDocument = PDDocument.load(originalPdf.toFile());

		return _pdfDocument.getNumberOfPages();
	}

	public void save(Path targetPdf) throws IOException {
		_pdfDocument.save(targetPdf.toFile());
		_pdfDocument.close();
	}

	public void setField(String name, String value) throws IOException {
		PDDocumentCatalog docCatalog = _pdfDocument.getDocumentCatalog();
		PDAcroForm acroForm = docCatalog.getAcroForm();
		PDField field = acroForm.getField( name );

		if( field != null ) {
			field.setValue(value);
		}
		else {
			System.err.println( "No field found with name:" + name );
		}
	}

	public void printFields() throws IOException {
		PDDocumentCatalog docCatalog = _pdfDocument.getDocumentCatalog();
		PDAcroForm acroForm = docCatalog.getAcroForm();

		for (PDField field : acroForm.getFields()) {
			//processField(field, "|--", field.getPartialName());
			
			String outputString = field.getPartialName() + ",  type=" + field.getClass().getSimpleName();
			System.out.println(outputString);
			
		}
	}

	protected void processField(PDField field, String sLevel, String sParent) throws IOException {
		/*
		List kids = field..getKids();

		if(kids != null) {
			Iterator kidsIter = kids.iterator();
			if(!sParent.equals(field.getPartialName())) {
				sParent = sParent + "." + field.getPartialName();
			}

			System.out.println(sLevel + sParent);

			while(kidsIter.hasNext()) {
				Object pdfObj = kidsIter.next();
				if(pdfObj instanceof PDField) {
					PDField kid = (PDField)pdfObj;
					processField(kid, "|  " + sLevel, sParent);
				}
			}
		}
		else {
			String outputString = sLevel + sParent + "." + field.getPartialName() + ",  type=" + field.getClass().getName();
			System.out.println(outputString);
		}
		*/

		String outputString = sLevel + sParent + "." + field.getPartialName() + ",  type=" + field.getClass().getName();
		System.out.println(outputString);
	}
}
