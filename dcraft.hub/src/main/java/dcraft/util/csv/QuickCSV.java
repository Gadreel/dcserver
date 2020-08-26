package dcraft.util.csv;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.PathPart;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.DecimalStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.Base64;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.img.ImageUtil;
import dcraft.util.io.ByteBufOutputStream;
import dcraft.util.io.ByteBufWriter;
import dcraft.util.io.OutputWrapper;
import dcraft.util.pdf.*;
import dcraft.web.md.ProcessContext;
import dcraft.web.md.Processor;
import dcraft.web.md.process.Block;
import dcraft.xml.XElement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class QuickCSV extends RecordStruct {
	protected CSVWriter writer = null;
	protected Memory memory = null;

    public CSVWriter getWriter() {
		return this.writer;
	}

	/* TODO track the last cell we wrote to - col and row
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 1) {
			PathPart part = path[0];

			if (part.isField()) {
				if ("PageNumber".equals(part.getField())) {
					return IntegerStruct.of(this.pagenum);
				}
				else if ("PageTop".equals(part.getField())) {
					return IntegerStruct.of(this.pagetop);
				}
				else if ("PageLeft".equals(part.getField())) {
					return IntegerStruct.of(this.pageleft);
				}
				else if ("PageRight".equals(part.getField())) {
					return IntegerStruct.of(this.pageright);
				}
				else if ("PageBottom".equals(part.getField())) {
					return IntegerStruct.of(this.pagebottom);
				}
				else if ("Indent".equals(part.getField())) {
					return DecimalStruct.of(this.indent);
				}
			}
		}

		return super.select(path);
	}
    */

    public void init(String filepath) throws IOException {
		Path fpath = Paths.get(filepath);

		Files.createDirectories(fpath.getParent());

		this.writer = new CSVWriter(Files.newBufferedWriter(fpath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
    }

    public void init(OutputStream out) throws IOException {
		this.writer = new CSVWriter(new OutputStreamWriter(out));
    }

    public void init(Memory out) throws IOException {
    	this.memory = out;
		this.writer = new CSVWriter(new OutputStreamWriter(new OutputWrapper(out)));
    }

    public void closeDocument() throws IOException {
		this.writer.close();

		if (this.memory != null)
			this.memory.setPosition(0);
	}

    public void newline() throws IOException {
    	this.writer.newLine();
    }

    public void field(CharSequence line) throws IOException {
    	this.writer.writeField(line.toString());
    }

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {

		if ("Init".equals(code.getName())) {
			try {
				if (code.hasNotEmptyAttribute("Path")) {
					this.init(StackUtil.stringFromElement(state, code, "Path"));
				}
				else if (code.hasNotEmptyAttribute("Result")) {
					String name = StackUtil.stringFromElement(state, code, "Result");

					Memory mem = new Memory();
					this.init(mem);

					StackUtil.addVariable(state, name, BinaryStruct.of(mem));
				}
				else if (code.hasNotEmptyAttribute("MemoryFile")) {
					String name = StackUtil.stringFromElement(state, code, "MemoryFile");

					Memory mem = new Memory();
					this.init(mem);

					StackUtil.addVariable(state, name, MemoryStoreFile.of(CommonPath.from(name)).with(mem));
				}
			}
			catch (IOException x) {
				Logger.error("Error initializing PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Field".equals(code.getName())) {
			try {
				String line = StackUtil.resolveValueToString(state, code.getAttribute( "Value"), true);
				this.field(line);
			}
			catch (IOException x) {
				Logger.error("Error writing field in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("NewLine".equals(code.getName())) {
			try {
				this.newline();
			}
			catch (IOException x) {
				Logger.error("Error skipping line in PDF: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("Save".equals(code.getName())) {
			try {
				this.closeDocument();
			}
			catch (IOException x) {
				Logger.error("Error saving CSV: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}
