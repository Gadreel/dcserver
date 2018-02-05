package dcraft.test.pdf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import dcraft.util.io.ByteBufOutputStream;
import dcraft.util.io.ByteBufWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import dcraft.web.md.ProcessContext;
import dcraft.web.md.Processor;
import dcraft.web.md.process.Block;

public class QuickPDF {
	protected PDDocument doc = null; 
	protected PDPage page = null;
	protected PDPageContentStream stream = null;
    protected float indent = 0;
    protected int pagenum = 0;
    protected int pagetop = 730;
    protected int pagebottom = 70;
    protected int pageleft = 60;
    protected int pageright = 560;
    protected PDFont font = PDType1Font.HELVETICA;
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
    
    public int getPageTop() {
		return this.pagetop;
	}
    
    public void setPageTop(int v) {
		this.pagetop = v;
	}
    
    public int getPageBottom() {
		return this.pagebottom;
	}
    
    public void setPageBottom(int v) {
		this.pagebottom = v;
	}
    
    public int getPageLeft() {
		return this.pageleft;
	}
    
    public void setPageLeft(int v) {
		this.pageleft = v;
	}
    
    public int getPageRight() {
		return this.pageright;
	}
    
    public void setPageRight(int v) {
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
    
    public void closePage() throws IOException {
    	if (this.stream != null) {
    		this.stream.close();
    		this.stream = null;
    	}
    }
    
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
    
    public void save(String filepath) throws IOException {
    	this.closePage();
    	
		if (this.pagelistener != null) {
			for (int i = 0; i < this.doc.getNumberOfPages(); i++) {
				PDPage page = this.doc.getPage(i);
				
				try (PDPageContentStream stream = new PDPageContentStream(this.doc, page, AppendMode.APPEND, true)) {
					this.pagelistener.finishPage(stream, i + 1);
				}
			}
		}
    	
    	this.doc.save(filepath);
    	this.doc.close();
    }
    
    public void saveToMemory(ByteBufWriter out) throws IOException {
    	this.closePage();
    	
		if (this.pagelistener != null) {
			for (int i = 0; i < this.doc.getNumberOfPages(); i++) {
				PDPage page = this.doc.getPage(i);
				
				try (PDPageContentStream stream = new PDPageContentStream(this.doc, page, AppendMode.APPEND, true)) {
					this.pagelistener.finishPage(stream, i + 1);
				}
			}
		}
    	
    	this.doc.save(new ByteBufOutputStream(out));
    	this.doc.close();
    }
    
    public float getLineHeight() {
    	return this.fontsize * 1.2F;
    }
    
    public void checkPage() throws IOException {
		float bottom = this.currpos.getY() - this.getLineHeight();

		if (bottom < this.pagebottom) 
			this.addPage();
    }
	
    public void line(CharSequence line) throws IOException {
    	this.checkPage();
    	
    	this.stream.setFont(this.font, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(line != null ? line.toString() : "");
    	this.stream.endText();

        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }
	
    public void wraplines(CharSequence line) throws IOException {
        Paragraph paragraph = new Paragraph();
        paragraph.setMaxWidth(this.pageright - this.pageleft - this.indent);
        
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
    public void image(String imageName) throws FileNotFoundException, IOException {
    	this.image(ImageIO.read(new File(imageName)));
    }
    
    public void image(BufferedImage img) throws FileNotFoundException, IOException {
        PDImageXObject image = JPEGFactory.createFromImage(this.doc, img);
        
        this.stream.drawImage(image, this.currpos.getX(), this.currpos.getY() - image.getHeight());  
    }
        
    public void image(PDImageXObject img, float x, float y, float width, float height) throws FileNotFoundException, IOException {
        this.stream.drawImage(img, x, y, width, height);  
    }
    
    public void skipline() throws IOException {
    	this.checkPage();
    	
        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }
	
    public void headerline(String line) throws IOException {
    	this.checkPage();
    	
    	this.stream.setFont(this.boldfont, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(line != null ? line : "");
    	this.stream.endText();

        this.currpos = new Position(this.pageleft + this.indent, this.currpos.getY() - this.getLineHeight());
    }
	
    public void fieldline(String label, CharSequence line) throws IOException {
    	this.checkPage();
    	
    	this.stream.setFont(this.boldfont, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX(), this.currpos.getY());
    	this.stream.showText(label);
    	this.stream.endText();
    	
    	//System.out.println("- " +  label + " is " + this.boldfont.getStringWidth(label));
    	
    	this.stream.setFont(this.font, this.fontsize);

    	this.stream.beginText();
    	this.stream.newLineAtOffset(this.currpos.getX() + (this.fontsize * this.boldfont.getStringWidth(label) / 1000) + 10, this.currpos.getY());
    	this.stream.showText(line != null ? line.toString() : "");
    	this.stream.endText();

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
    		
	    	this.stream.beginText();
	    	this.stream.newLineAtOffset(colpos.getX(), colpos.getY());
	    	this.stream.showText(col.title);
	    	this.stream.endText();
	    	
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
}
