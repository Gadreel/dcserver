package dcraft.util.pdf;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.pnuema.java.barcode.Barcode;
import com.pnuema.java.barcode.EncodingType;
import dcraft.db.Constants;
import dcraft.db.DbServiceRequest;
import dcraft.db.request.schema.Load;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.PathPart;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.*;
import dcraft.util.ArrayUtil;
import dcraft.util.Base64;
import dcraft.util.Base64Alt;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.img.ImageUtil;
import dcraft.util.io.ByteBufOutputStream;
import dcraft.util.io.ByteBufWriter;
import dcraft.util.io.OutputWrapper;
import dcraft.xml.XElement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDFourColours;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import dcraft.web.md.ProcessContext;
import dcraft.web.md.Processor;
import dcraft.web.md.process.Block;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

public class QuickPDF extends RecordStruct {
	protected PDDocument doc = null; 
	protected PDPage page = null;
	protected PDPageContentStream stream = null;
    protected float indent = 0;
    protected int pagenum = 0;
    protected float pagetop = 730;
    protected float pagebottom = 70;
    protected float pageleft = 60;
    protected float pageright = 560;
    protected PDFont font = PDType1Font.HELVETICA;
    protected PDFont stdfont = PDType1Font.HELVETICA;
    protected PDFont boldfont = PDType1Font.HELVETICA_BOLD;
    protected PDFont italicfont = PDType1Font.HELVETICA_OBLIQUE;
    protected PDFont bolditalicfont = PDType1Font.HELVETICA_BOLD_OBLIQUE;
    protected float fontsize = 14;
    protected Position currpos = null;
    protected Table table = null;
    protected PageListener pagelistener = null;
    
    public PDDocument getDoc() {
		return this.doc;
	}
    
    public Position getPosition() {
		return this.currpos;
	}
    
    public void setPosition(Position v) {
    	this.currpos = v;
    }
    
    public float getPageTop() {
		return this.pagetop;
	}
    
    public void setPageTop(float v) {
		this.pagetop = v;
	}
    
    public float getPageBottom() {
		return this.pagebottom;
	}
    
    public void setPageBottom(float v) {
		this.pagebottom = v;
	}
    
    public float getPageLeft() {
		return this.pageleft;
	}
    
    public void setPageLeft(float v) {
		this.pageleft = v;
	}
    
    public float getPageRight() {
		return this.pageright;
	}
    
    public void setPageRight(float v) {
		this.pageright = v;
	}
    
    public void setIndent(float v) {
		this.indent = v;
		
		this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY());
	}
    
    public void setFont(PDFont v) {
		this.font = v;
	}
    
    public PDFont getFont() {
		return this.font;
	}
    
    public void setBoldFont(PDFont v) {
		this.boldfont = v;
	}
    
    public void setFontSize(float v) {
		this.fontsize = v;
	}
    
    public float getFontSize() {
		return this.fontsize;
	}
    
    public void setPageListener(PageListener v) {
		this.pagelistener = v;
	}

	public int getPageNumber() {
		return this.pagenum;
	}

	@Override
	public BaseStruct select(PathPart... path) {
		if (path.length == 1) {
			PathPart part = path[0];

			if (part.isField()) {
				if ("PageNumber".equals(part.getField())) {
					return IntegerStruct.of(this.pagenum);
				}
				else if ("PageTop".equals(part.getField())) {
					return DecimalStruct.of(this.pagetop);
				}
				else if ("PageLeft".equals(part.getField())) {
					return DecimalStruct.of(this.pageleft);
				}
				else if ("PageRight".equals(part.getField())) {
					return DecimalStruct.of(this.pageright);
				}
				else if ("PageBottom".equals(part.getField())) {
					return DecimalStruct.of(this.pagebottom);
				}
				else if ("Indent".equals(part.getField())) {
					return DecimalStruct.of(this.indent);
				}
			}
		}

		return super.select(path);
	}

    public void closePage() throws IOException {
    	if (this.stream != null) {
    		this.stream.close();
    		this.stream = null;
    	}
    }
    
    // TODO enahnce so page can be added from another PDF
	// note that must read the template fresh each time as the addPage code will change the page root
	
    public void addPage() throws IOException {
    	this.closePage();
    	
		this.page = new PDPage();
		this.doc.addPage(this.page);
		this.stream = new PDPageContentStream(doc, page);
		this.pagenum++;
		this.currpos = new Position(this.pageleft + this.indent, this.pagetop);
		
		// reprint table header if in table
		if (this.table != null)
			this.starttable();
    }
    
    public void init() throws IOException {
		this.doc = new PDDocument();
		
		this.addPage();
    }

    public void initEmpty() throws IOException {
		this.doc = new PDDocument();
    }

	public void load(String filepath) throws IOException, OperatingContextException {
    	Path src = OperationContext.getOrThrow().getSite().resolvePath(filepath);

		this.doc = PDDocument.load(src.toFile());

		this.switchPage(1);
	}

	public void switchPage(int i) throws IOException {
		this.closePage();

		this.page = this.doc.getPage(i - 1);
		this.stream = new PDPageContentStream(doc, page, AppendMode.APPEND, true, false);
		this.pagenum = i;
		this.currpos = new Position(this.pageleft + this.indent, this.pagetop);
	}

    public void save(String filepath) throws IOException {
		this.closeDocument();
	
		Path fpath = Paths.get(filepath);

		Files.createDirectories(fpath.getParent());
    	
    	this.doc.save(fpath.toFile());
    	this.doc.close();
    }
    
    public void saveToMemory(ByteBufWriter out) throws IOException {
    	this.saveToMemory(new ByteBufOutputStream(out));
    }
    
    public void saveToMemory(Memory out) throws IOException {
    	this.saveToMemory(new OutputWrapper(out));
    	
    	out.setPosition(0);
    }
    
    public void saveToMemory(OutputStream out) throws IOException {
    	this.closeDocument();
    	
    	this.doc.save(out);
    	this.doc.close();
    }
    
    public void closeDocument() throws IOException {
		this.closePage();
	
		if (this.pagelistener != null) {
			for (int i = 0; i < this.doc.getNumberOfPages(); i++) {
				PDPage page = this.doc.getPage(i);
			
				try (PDPageContentStream stream = new PDPageContentStream(this.doc, page, AppendMode.APPEND, true)) {
					this.pagelistener.finishPage(stream, i + 1);
				}
			}
		}
	}
    
    public float getLineHeight() {
    	return this.fontsize * 1.2F;
    }
    
    public boolean checkPage() throws IOException {
		return this.checkPage(null);
    }

	public boolean checkPage(Float height) throws IOException {
		float bottom = this.currpos.getY() - (height != null ? height : this.getLineHeight());

		if (bottom < this.pagebottom) {
			this.addPage();
			return true;
		}

		return false;
	}

    public void line(CharSequence line) throws IOException {
    	this.checkPage();

		line = PdfUtil.stripAllRestrictedPDFChars(line);

		this.stream.setFont(this.font, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(line != null ? line.toString() : "");
    	this.stream.endText();

        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
	}

	public void wraplines(CharSequence line) throws IOException {
    	this.wraplines(line, null);
    }
	
    public void wraplines(CharSequence line, Float maxwidth) throws IOException {
        Paragraph paragraph = new Paragraph();
        paragraph.setMaxWidth(maxwidth != null ? maxwidth : this.pageright - this.pageleft - this.indent);

		line = PdfUtil.stripAllRestrictedPDFChars(line);

        if (line != null)
        	paragraph.addText(line.toString(), this.fontsize, this.font);
        
        paragraph.setApplyLineSpacingToFirstLine(false);
        
        paragraph.drawText(
        	new LineListener() {
				@Override
				public LLResult prep(Object drawnObject, Position upperLeft, float height) {
					try {
						QuickPDF.this.currpos = upperLeft;
						QuickPDF.this.checkPage();
						
						LLResult res = new LLResult();
						res.pos = QuickPDF.this.currpos;
						res.stream = QuickPDF.this.stream;
						
						return res;
					}
					catch (Exception x) {
						System.out.println("new page issue: " + x);
						return null;
					}
				}
			}, 
        	this.currpos, 
        	Alignment.Left, 
        	null);
        
        this.skipline();		// to end of current line really
        this.skipline();		// to end of current line really
    }

	public float wrapheight(CharSequence line) throws IOException {
		return this.wrapheight(line, null);
	}

	public float wrapheight(CharSequence line, Float maxwidth) throws IOException {
		Paragraph paragraph = new Paragraph();
		paragraph.setMaxWidth(maxwidth != null ? maxwidth : this.pageright - this.pageleft - this.indent);

		line = PdfUtil.stripAllRestrictedPDFChars(line);

		if (line != null)
			paragraph.addText(line.toString(), this.fontsize, this.font);

		paragraph.setApplyLineSpacingToFirstLine(false);

		return TextSequenceUtil.getHeight(paragraph, paragraph.getMaxWidth(), paragraph.getLineSpacing(), paragraph.isApplyLineSpacingToFirstLine());
	}

    public Paragraph prepParagraph() {
        Paragraph paragraph = new Paragraph();
        paragraph.setMaxWidth(this.pageright - this.pageleft - this.indent);
        return paragraph;
    }
	
    public void wraplines(Paragraph paragraph) throws IOException {
        paragraph.drawText(
        	new LineListener() {
				@Override
				public LLResult prep(Object drawnObject, Position upperLeft, float height) {
					try {
						QuickPDF.this.currpos = upperLeft;
						QuickPDF.this.checkPage();
						
						LLResult res = new LLResult();
						res.pos = QuickPDF.this.currpos;
						res.stream = QuickPDF.this.stream;
						
						return res;
					}
					catch (Exception x) {
						System.out.println("new page issue: " + x);
						return null;
					}
				}
			}, 
        	this.currpos, 
        	Alignment.Left, 
        	null);
        
        this.skipline();		// to end of current line really
        this.skipline();		// to end of current line really
    }
	
    public void markdown(ProcessContext ctx, String md) throws IOException {
    	//this.wraplines(md);
    	
    	if (md == null)
    		return;

    	MarkdownEmitter emitter = new MarkdownEmitter(ctx, this);
    	
    	Block block = Processor.parseToBlock(ctx, md);

        while (block != null) {
            emitter.emit(this, block);
            block = block.next;
        }
    }
	
    // upper left
//    public void image(String imageName) throws FileNotFoundException, IOException {
//    	this.image(ImageIO.read(new File(imageName)));
//    }
//
    public void image(File image) throws FileNotFoundException, IOException {
    	this.image(ImageIO.read(image));
    }

    public void image(BufferedImage img) throws IOException {
        PDImageXObject image = JPEGFactory.createFromImage(this.doc, img);

        this.stream.drawImage(image, this.currpos.getX(), this.currpos.getY() - image.getHeight());
    }

    public void image(BufferedImage img, float width, float height) throws IOException {
		PDImageXObject image = JPEGFactory.createFromImage(this.doc, img);

		if (width == -1)
			width = image.getWidth();

		if (height == -1)
			height = image.getHeight();

		this.stream.drawImage(image, this.currpos.getX(), this.currpos.getY() - height, width, height);
	}

    public void image(BufferedImage img, float x, float y, float width, float height) throws IOException {
        PDImageXObject image = JPEGFactory.createFromImage(this.doc, img);

		if (width == -1)
			width = image.getWidth();

		if (height == -1)
			height = image.getHeight();

		if (x == -1)
			x = this.currpos.getX();

		if (y == -1)
			y = this.currpos.getY() - height;

        this.stream.drawImage(image, x, y, width, height);
    }

    public void image(PDImageXObject image, float x, float y, float width, float height) throws IOException {
		if (width == -1)
			width = image.getWidth();

		if (height == -1)
			height = image.getHeight();

		if (x == -1)
			x = this.currpos.getX();

		if (y == -1)
			y = this.currpos.getY() - height;

        this.stream.drawImage(image, x, y, width, height);
	}

	public void barCode(String code, EncodingType barcodeType, float width, float height) throws IOException {
		// TODO check for -1 dimensions and default in best calc

		this.barCode(code, barcodeType, this.currpos.getX(), this.currpos.getY(), width, height);
    }

	public void barCode(String code, EncodingType barcodeType, float x, float y, float width, float height) throws IOException {
		Barcode barcode = new Barcode();

		BufferedImage img = barcode.encode(barcodeType, code);

		PDImageXObject image = JPEGFactory.createFromImage(this.doc, img);

		this.stream.drawImage(image, x, y, width, height);
	}

	public void setStrokeColor(String rgb) throws IOException {
    	String[] cparts = rgb.split(",");

    	if (cparts.length == 3) {
			this.stream.setStrokingColor(
					(int) StringUtil.parseInt(cparts[0],  0),
					(int) StringUtil.parseInt(cparts[1], 0),
					(int) StringUtil.parseInt(cparts[2], 0)
			);
		}
	}

	public void setColor(String rgb) throws IOException {
    	String[] cparts = rgb.split(",");

    	if (cparts.length == 3) {
			this.stream.setNonStrokingColor(
					(int) StringUtil.parseInt(cparts[0],  0),
					(int) StringUtil.parseInt(cparts[1], 0),
					(int) StringUtil.parseInt(cparts[2], 0)
			);
		}
	}

	public void setStrokeWidth(Float width) throws IOException {
		this.stream.setLineWidth(width != null ? width : 2);
	}

    public void moveTo(Float x1, Float y1) throws IOException {
        this.stream.moveTo(x1 != null ? x1 : this.currpos.getX(), y1 != null ? y1 : this.currpos.getY());
    }

    public void moveToYOff(Float x1, Float y1) throws IOException {
        this.stream.moveTo(x1 != null ? x1 : this.currpos.getX(), y1 != null ? this.currpos.getY() - y1 : this.currpos.getY());
    }

    public void lineTo(Float x1, Float y1) throws IOException {
        this.stream.lineTo(x1 != null ? x1 : this.currpos.getX(), y1 != null ? y1 : this.currpos.getY());
    }

    public void lineToYOff(Float x1, Float y1) throws IOException {
        this.stream.lineTo(x1 != null ? x1 : this.currpos.getX(), y1 != null ? this.currpos.getY() - y1 : this.currpos.getY());
    }

    public void draw() throws IOException {
    	//this.stream.setLineWidth(12);
    	//this.stream.setStrokingColor(Color.BLACK);
    	//this.stream.drawLine(100, 600, 400, 600);
        this.stream.stroke();
        //this.stream.close();

		//this.stream.setNonStrokingColor(Color.GRAY);
		//this.stream.fillRect(100, 600, 400, 580);
    }

    public void skipline() throws IOException {
    	this.checkPage();
    	
        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }

    public void uplines(int count) throws IOException {
    	this.currpos = new Position(this.pageleft + this.indent, this.getPosition().getY() + (this.getLineHeight() * count));
	}

    public void headerline(String line) throws IOException {
    	this.checkPage();

    	if (line == null)
    		line = "";

		line = PdfUtil.stripAllRestrictedPDFChars(line).toString();

    	this.stream.setFont(this.boldfont, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(line != null ? line : "");
    	this.stream.endText();

        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }

	public void out(CharSequence line) throws IOException {
		this.checkPage();

		if (line == null)
			line = "";

		line = PdfUtil.stripAllRestrictedPDFChars(line);

		this.stream.setFont(this.font, this.fontsize);

		this.stream.beginText();
		this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
		this.stream.showText(line.toString());
		this.stream.endText();

		float newx = this.currpos.getX() + (this.fontsize * this.font.getStringWidth(line.toString()) / 1000);

		this.currpos = new Position(newx, this.currpos.getY());
	}

	public float outWidth(CharSequence line) throws IOException {
		if (line == null)
			line = "";

		line = PdfUtil.stripAllRestrictedPDFChars(line);

		TextFlow textFlow = new TextFlow();

		textFlow.addText(line.toString(), this.fontsize, this.font);

		return textFlow.getWidth();
	}

	public void startField(CharSequence line) throws IOException {
		this.checkPage();
		
		if (line == null)
			line = "";
		
		line = PdfUtil.stripAllRestrictedPDFChars(line);
		
		this.stream.setFont(this.boldfont, this.fontsize);
		
		this.stream.beginText();
		this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
		this.stream.showText(line.toString());
		this.stream.endText();
		
		float newx = this.currpos.getX() + (this.fontsize * this.boldfont.getStringWidth(line.toString()) / 1000);
		
		this.currpos = new Position(newx, this.currpos.getY());
	}

    public void field(String label, CharSequence line) throws IOException {
    	this.checkPage();

		line = PdfUtil.stripAllRestrictedPDFChars(line);

    	this.stream.setFont(this.boldfont, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(label);
    	this.stream.endText();
    	
    	//System.out.println("- " +  label + " is " + this.boldfont.getStringWidth(label));
    	
    	this.stream.setFont(this.font, this.fontsize);

    	float newx = this.currpos.getX() + (this.fontsize * this.boldfont.getStringWidth(label) / 1000) + 10;

    	if (line == null)
    		line = "";

    	this.stream.beginText();
    	this.stream.newLineAtOffset(newx, this.currpos.getY());
    	this.stream.showText(line.toString());
    	this.stream.endText();

    	newx += (this.fontsize * this.font.getStringWidth(line.toString()) / 1000);

        this.currpos = new Position(newx, this.currpos.getY());
    }

	public void fieldline(String label, CharSequence line) throws IOException {
    	this.field(label, line);

		this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
	}

    public Table inittable() {
    	this.table = new Table();
    	
    	return this.table;
    }    
	
    public void starttable() throws IOException {
    	this.stream.setFont(this.boldfont, this.fontsize);
    	
    	Position colpos = this.currpos;

    	for (int c = 0; c < this.table.columns.size(); c++) {
    		TableColumn col = this.table.columns.get(c);
    		
    		if (StringUtil.isNotEmpty(col.title)) {
				this.stream.beginText();
				this.stream.newLineAtOffset(colpos.getX(), colpos.getY());
				this.stream.showText(col.title);
				this.stream.endText();
			}
	    	
	    	colpos = colpos.add(col.width, 0);
    	}
    	
        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }    
    
    public void rowline(String... cells) throws IOException {
    	this.checkPage();
    	
    	this.stream.setFont(this.font, this.fontsize);
    	
    	Position colpos = this.currpos;

    	for (int c = 0; c < this.table.columns.size(); c++) {
    		if (c >= cells.length)
    			break;
    		
    		TableColumn col = this.table.columns.get(c);
    		
    		String val = cells[c];
    		
    		if (val != null) {
				val = PdfUtil.stripAllRestrictedPDFChars(val).toString();

	    		if (val.length() > col.chars)
	    			val = val.substring(0, col.chars - 3) + "...";
	    		
		    	this.stream.beginText();
		    	this.stream.newLineAtOffset(colpos.getX(), colpos.getY());
		    	this.stream.showText(val);
		    	this.stream.endText();
    		}
    		
	    	colpos = colpos.add(col.width, 0);
    	}
    	
        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }
	
    public void endtable() {
    	this.table = null;
    }    

	public class Table {
		public List<TableColumn> columns = new ArrayList<>();
		
		public Table withColumn(String title, int width, int chars) {
			TableColumn col = new TableColumn();
			col.title = title;
			col.width = width;
			col.chars = chars;
			
			this.columns.add(col);
			
			return this;
		}
	}
	
	public class TableColumn {
		public String title = null;
		public int width = 0;
		public int chars = 0;
	}

	public void setField(String name, String value) throws IOException {
		PDDocumentCatalog docCatalog = this.doc.getDocumentCatalog();
		PDAcroForm acroForm = docCatalog.getAcroForm();
		PDField field = acroForm.getField( name );

		if (StringUtil.isNotEmpty(value))
			value = PdfUtil.stripAllRestrictedPDFChars(value).toString();

		if( field != null ) {
			field.setValue(value);
		}
		else {
			System.err.println( "No field found with name:" + name );
		}
	}


	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {

		if ("Init".equals(code.getName())) {
			try {
				this.init();
				
				if ((code.selectFirst("Header") != null) || (code.selectFirst("Footer") != null)) {
					this.setPageListener(new PageListener() {
						@Override
						public void finishPage(PDPageContentStream stream, int pagenumber) throws IOException {
							try {
								// TODO more flexibility in future
								stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
								
								StackUtil.addVariable(state, "_PdfPageNumber", IntegerStruct.of(pagenumber));
								StackUtil.addVariable(state, "_PdfPageCount", IntegerStruct.of(QuickPDF.this.getDoc().getNumberOfPages()));

								DecimalStruct pageLeft = DecimalStruct.of(QuickPDF.this.getPageLeft());

								for (XElement el : code.selectAll("Header")) {
									BigDecimal x = pageLeft.getValue();

									if (el.hasNotEmptyAttribute("X"))
										x = Struct.objectToDecimal(StackUtil.refFromElement(state, el, "X", true));

									stream.beginText();
									stream.newLineAtOffset(x.floatValue(), 760);
									stream.showText(StackUtil.stringFromElement(state, el, "Value"));
									stream.endText();
								}
								
								for (XElement el : code.selectAll("Footer")) {
									BigDecimal x = pageLeft.getValue();

									if (el.hasNotEmptyAttribute("X"))
										x = Struct.objectToDecimal(StackUtil.refFromElement(state, el, "X", true));

									stream.beginText();
									stream.newLineAtOffset(x.floatValue(), 20);
									stream.showText(StackUtil.stringFromElement(state, el, "Value"));
									stream.endText();
								}
							}
							catch (OperatingContextException x) {
								Logger.error("Missing context in PDF page handler");
							}
						}
					});
				}
			}
			catch (IOException x) {
				Logger.error("Error initializing PDF object: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Font".equals(code.getName())) {
			if (code.hasNotEmptyAttribute("Size"))
				this.setFontSize(Float.valueOf(StackUtil.stringFromElement(state, code, "Size")));

			boolean bold = StackUtil.boolFromElement(state, code, "Bold", false);
			boolean italic = StackUtil.boolFromElement(state, code, "Italic", false);

			if (bold && italic) {
				this.font = this.bolditalicfont;
			}
			else if (bold) {
				this.font = this.boldfont;   // PDType1Font.HELVETICA_BOLD;
			}
			else if (italic) {
				this.font = this.italicfont;
			}
			else {
				this.font = this.stdfont;
			}

			String color = StackUtil.stringFromElement(state, code, "Color");
			
			try {
				if (StringUtil.isNotEmpty(color))
					this.setColor(color);
			}
			catch (IOException x3) {
				Logger.error("Error color in PDF: " + x3);
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("Indent".equals(code.getName())) {
			if (code.hasNotEmptyAttribute("X"))
				this.setIndent(Float.valueOf(StackUtil.stringFromElement(state, code, "X")));
			else
				this.setIndent(this.currpos.getX() - this.pageleft);

			return ReturnOption.CONTINUE;
		}

		if ("PageSettings".equals(code.getName())) {
			if (code.hasNotEmptyAttribute("Top"))
				this.setPageTop(Float.valueOf(StackUtil.stringFromElement(state, code, "Top")));

			if (code.hasNotEmptyAttribute("Bottom"))
				this.setPageBottom(Float.valueOf(StackUtil.stringFromElement(state, code, "Bottom")));

			if (code.hasNotEmptyAttribute("Left"))
				this.setPageLeft(Float.valueOf(StackUtil.stringFromElement(state, code, "Left")));

			if (code.hasNotEmptyAttribute("Right"))
				this.setPageRight(Float.valueOf(StackUtil.stringFromElement(state, code, "Right")));

			return ReturnOption.CONTINUE;
		}

		if ("Line".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				this.line(line);
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("HeaderLine".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				this.headerline(line);
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Markdown".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				this.markdown(ProcessContext.of(ResourceHub.getResources().getMarkdown().getSafeConfig()), line);
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("WrapLines".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				this.wraplines(line);
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("WrapHeight".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				float h = this.wrapheight(line);

				String name = StackUtil.stringFromElement(state, code, "Result");

				if (StringUtil.isNotEmpty(name)) {
					StackUtil.addVariable(state, name, DecimalStruct.of(h));
				}
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Out".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				this.out(line);
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("CalcOut".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getText(), true);

				if (StringUtil.isEmpty(line))
					line = StackUtil.resolveValueToString(state, code.getAttribute("Value"), true);

				float outWidth = this.outWidth(line);

				String name = StackUtil.stringFromElement(state, code, "Result");

				if (StringUtil.isNotEmpty(name)) {
					StackUtil.addVariable(state, name, DecimalStruct.of(outWidth));
				}
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Space".equals(code.getName())) {
			try {
				long spaces = StackUtil.intFromElement(state, code, "Count", 5);

				this.out(StringUtil.repeat(' ', (int)spaces));
			}
			catch (IOException x) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Field".equals(code.getName())) {
			try {
				String label = StackUtil.resolveValueToString(state, code.getAttribute("Label"), true);
				String line = StackUtil.resolveValueToString(state, code.getAttribute( "Value"), true);
				this.field(label, line);
			}
			catch (IOException x) {
				Logger.error("Error writing field in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("StartField".equals(code.getName())) {
			try {
				String label = StackUtil.resolveValueToString(state, code.getAttribute("Label"), true);
				this.startField(label);
			}
			catch (IOException x) {
				Logger.error("Error writing field in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("FieldLine".equals(code.getName())) {
			try {
				String label = StackUtil.resolveValueToString(state, code.getAttribute( "Label"), true);
				String line = StackUtil.resolveValueToString(state, code.getAttribute( "Value"), true);
				this.fieldline(label, line);
			}
			catch (IOException x) {
				Logger.error("Error writing field in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("SkipLine".equals(code.getName())) {
			try {
				this.skipline();
				
				if (code.hasNotEmptyAttribute("X"))
					this.setIndent(Float.valueOf(StackUtil.stringFromElement(state, code, "X")));
			}
			catch (IOException x) {
				Logger.error("Error skipping line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("StartTable".equals(code.getName())) {
			try {
				Table table = this.inittable();

				for (XElement cols : code.selectAll("Column")) {
					table.withColumn(
							StackUtil.stringFromElement(state, cols, "Title"),
							(int)StackUtil.intFromElement(state, cols, "Width", 0),
							(int)StackUtil.intFromElement(state, cols, "Chars", 0)
					);
				}

				this.starttable();
			}
			catch (IOException x) {
				Logger.error("Error skipping line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("TableRow".equals(code.getName())) {
			try {
				List<String> cells = new ArrayList<>();

				for (XElement cols : code.selectAll("Column")) {
					cells.add(StackUtil.resolveValueToString(state, cols.getAttribute("Value"), true));
				}

				this.rowline(cells.toArray(new String[cells.size()]));
			}
			catch (IOException x) {
				Logger.error("Error skipping line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("EndTable".equals(code.getName())) {
			this.endtable();

			return ReturnOption.CONTINUE;
		}
		
		if ("Image".equals(code.getName())) {
			BigDecimal posx = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "X", true));
			BigDecimal posy = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Y", true));
			BigDecimal w = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Width", true));
			BigDecimal h = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Height", true));

			String base64 = StackUtil.stringFromElement(state, code, "Base64");
			
			if (StringUtil.isEmpty(base64)) {
				base64 = StackUtil.stringFromElement(state, code, "DataURL");
				
				if (StringUtil.isNotEmpty(base64) && (base64.contains(",")))
					base64 = base64.substring(base64.indexOf(',') + 1);
			}

			BufferedImage img = null;

			try {
				if (StringUtil.isNotEmpty(base64)) {
					byte[] bin64 = Base64.decode(base64);

					try (InputStream is = new ByteArrayInputStream(bin64)) {
						img = ImageIO.read(is);

						this.image(img, (posx != null) ? posx.floatValue() : -1, (posy != null) ? posy.floatValue() : -1, (w != null) ? w.floatValue() : -1, (h != null) ? h.floatValue() : -1);

					}
				}
				else {
					FileStoreFile file = (FileStoreFile) StackUtil.refFromElement(state, code, "File");

					// TODO support other file systems
					if (file instanceof LocalStoreFile) {
						LocalStoreFile lfile = (LocalStoreFile) file;

						Memory mem = IOUtil.readEntireFileToMemory(lfile.getLocalPath());

						try (InputStream is = new ByteArrayInputStream(mem.toArray())) {
							img = ImageIO.read(is);
						}
					}
					else {
						BinaryStruct bin = (BinaryStruct) StackUtil.refFromElement(state, code, "Memory");

						// TODO support other file systems
						if (bin != null) {
							Memory mem = bin.getValue();

							try (InputStream is = new ByteArrayInputStream(mem.toArray())) {
								img = ImageIO.read(is);
							}
						}
					}
				}

				this.image(img, (posx != null) ? posx.floatValue() : -1, (posy != null) ? posy.floatValue() : -1, (w != null) ? w.floatValue() : -1, (h != null) ? h.floatValue() : -1);
			}
			catch (IOException x) {
				Logger.error("Error writing image in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Barcode".equals(code.getName())) {
			String barcode = StackUtil.stringFromElementClean(state, code, "Code");
			String bartype = StackUtil.stringFromElementClean(state, code, "Type", "CODE128B");

			BigDecimal x = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "X", true));
			BigDecimal y = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Y", true));
			BigDecimal w = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Width", true));
			BigDecimal h = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Height", true));

			EncodingType btype = EncodingType.valueOf(bartype);

			try {
				this.barCode(barcode, btype, x.floatValue(), y.floatValue(), w.floatValue(), h.floatValue());
			}
			catch (IOException x3) {
				Logger.error("Error moving PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("SwitchPage".equals(code.getName())) {
			long page = StackUtil.intFromElement(state, code, "Number", 0);

			try {
				this.switchPage((int) page);
			}
			catch (IOException x3) {
				Logger.error("Error moving PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("SetPos".equals(code.getName())) {
			String x = StackUtil.stringFromElement(state, code, "X");
			String y = StackUtil.stringFromElement(state, code, "Y");
			String yoff = StackUtil.stringFromElement(state, code, "YOffset");

			try {
				if (StringUtil.isNotEmpty(yoff))
					this.currpos = new Position(Float.valueOf(x), this.currpos.getY() - Float.valueOf(yoff));
				else
					this.currpos = new Position(Float.valueOf(x), Float.valueOf(y));
			}
			catch (NumberFormatException x2) {
				Logger.warn("Bad coords: " + x + "," + y);
			}

			return ReturnOption.CONTINUE;
		}

		if ("MoveTo".equals(code.getName())) {
			String x = StackUtil.stringFromElement(state, code, "X");
			String y = StackUtil.stringFromElement(state, code, "Y");
			String yoff = StackUtil.stringFromElement(state, code, "YOffset");

			try {
				if (StringUtil.isNotEmpty(yoff))
					this.moveToYOff(x != null ? Float.valueOf(x) : null, Float.valueOf(yoff));
				else
					this.moveTo(x != null ? Float.valueOf(x) : null, y != null ? Float.valueOf(y) : null);
			}
			catch (NumberFormatException x2) {
				Logger.warn("Bad coords: " + x + "," + y);
			}
			catch (IOException x3) {
				Logger.error("Error moving PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("LineTo".equals(code.getName())) {
			String x = StackUtil.stringFromElement(state, code, "X");
			String y = StackUtil.stringFromElement(state, code, "Y");
			String yoff = StackUtil.stringFromElement(state, code, "YOffset");

			try {
				if (StringUtil.isNotEmpty(yoff))
					this.lineToYOff(x != null ? Float.valueOf(x) : null, Float.valueOf(yoff));
				else
					this.lineTo(x != null ? Float.valueOf(x) : null, y != null ? Float.valueOf(y) : null);
			}
			catch (NumberFormatException x2) {
				Logger.warn("Bad coords: " + x + "," + y);
			}
			catch (IOException x3) {
				Logger.error("Error writing line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Draw".equals(code.getName())) {
			try {
				this.draw();
			}
			catch (IOException x3) {
				Logger.error("Error writing drawing in PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Ensure".equals(code.getName())) {
			String h = StackUtil.stringFromElement(state, code, "Height");
			String name = StackUtil.stringFromElement(state, code, "Result");

			try {
				boolean res = this.checkPage(h != null ? Float.valueOf(h) : null);

				if (StringUtil.isNotEmpty(name)) {
					StackUtil.addVariable(state, name, BooleanStruct.of(res));
				}
			}
			catch (NumberFormatException x2) {
				Logger.warn("Bad height: " + h);
			}
			catch (IOException x3) {
				Logger.error("Error ensuring height in PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Stroke".equals(code.getName())) {
			String color = StackUtil.stringFromElement(state, code, "Color");
			String width = StackUtil.stringFromElement(state, code, "Width");

			try {
				if (StringUtil.isNotEmpty(color))
					this.setStrokeColor(color);

				if (StringUtil.isNotEmpty(width))
					this.setStrokeWidth(Float.valueOf(width));
			}
			catch (NumberFormatException x2) {
				Logger.warn("Bad width: " + width);
			}
			catch (IOException x3) {
				Logger.error("Error writing drawing in PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("SetField".equals(code.getName())) {
			String name = StackUtil.resolveValueToString(state, StackUtil.stringFromElement(state, code, "Name"), true);
			String value = StackUtil.resolveValueToString(state, StackUtil.stringFromElement(state, code, "Value"), true);

			try {
				setField(name, value);
			}
			catch (IOException x3) {
				Logger.error("Error writing drawing in PDF: " + x3);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Save".equals(code.getName())) {
			try {
				if (code.hasNotEmptyAttribute("Path")) {
					this.save(StackUtil.stringFromElement(state, code, "Path"));
				}
				else if (code.hasNotEmptyAttribute("Result")) {
					String name = StackUtil.stringFromElement(state, code, "Result");
					
					Memory mem = new Memory();
					this.saveToMemory(mem);
					
					StackUtil.addVariable(state, name, BinaryStruct.of(mem));
				}
				else if (code.hasNotEmptyAttribute("MemoryFile")) {
					String name = StackUtil.stringFromElement(state, code, "MemoryFile");

					Memory mem = new Memory();
					this.saveToMemory(mem);

					StackUtil.addVariable(state, name, MemoryStoreFile.of(CommonPath.from(name)).with(mem));
				}
			}
			catch (IOException x) {
				Logger.error("Error saving PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Load".equals(code.getName())) {
			try {
				if (code.hasNotEmptyAttribute("Path")) {
					this.load(StackUtil.resolveValueToString(state, code.attr("Path"), true));
				}
			}
			catch (IOException x) {
				Logger.error("Error loading PDF: " + x);
			}


			return ReturnOption.CONTINUE;
		}

		if ("NewPage".equals(code.getName())) {
			try {
				this.addPage();
			}
			catch (IOException x) {
				Logger.error("unable to add page");
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}
