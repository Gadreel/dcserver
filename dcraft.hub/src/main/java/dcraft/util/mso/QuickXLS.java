package dcraft.util.mso;

import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.csv.CSVWriter;
import dcraft.util.io.OutputWrapper;
import dcraft.xml.XElement;
import jxl.Workbook;
import jxl.write.*;
import jxl.write.Number;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class QuickXLS extends RecordStruct {
	static public WritableCellFormat FMT_FLOAT_ACCOUNTING = new WritableCellFormat(NumberFormats.ACCOUNTING_FLOAT);
	static public WritableCellFormat FMT_FLOAT = new WritableCellFormat(NumberFormats.FLOAT);
	static public WritableCellFormat FMT_INT = new WritableCellFormat(NumberFormats.INTEGER);
	static public WritableCellFormat FMT_STR_BOLD = new WritableCellFormat(new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE, WritableFont.BOLD));
	static public WritableCellFormat FMT_STR = new WritableCellFormat(new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE, WritableFont.NO_BOLD));

	protected WritableWorkbook workbook = null;
	protected WritableSheet currntsheet = null;

	protected int currentrow = 0;
	protected int currentcol = 0;

	protected Memory memory = null;

    public WritableWorkbook getWorkbook() {
		return this.workbook;
	}

	public WritableSheet getCurrntsheet() {
		return this.currntsheet;
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

		this.workbook = Workbook.createWorkbook(fpath.toFile());
    }

    public void init(OutputStream out) throws IOException {
		this.workbook = Workbook.createWorkbook(out);
    }

    public void init(Memory out) throws IOException {
    	this.memory = out;
		this.workbook = Workbook.createWorkbook(new OutputWrapper(out));
    }

    public void addSheet(String name) {
		this.currntsheet = this.workbook.createSheet(name, this.workbook.getNumberOfSheets());

		this.currentrow = 0;
		this.currentcol = 0;
	}

	public void closeDocument() throws IOException {
		this.workbook.write();

		try {
			this.workbook.close();
		}
		catch (WriteException x) {
			throw new IOException("Unable to close workbook.", x);
		}

		if (this.memory != null)
			this.memory.setPosition(0);
	}

    public void newline() throws IOException {
		this.currentrow++;
		this.currentcol = 0;
    }

    public void addString(CharSequence value, WritableCellFormat fmt) throws IOException {
    	try {
    		if (StringUtil.isNotEmpty(value))
				this.currntsheet.addCell(new Label(this.currentcol, this.currentrow, value.toString(), fmt));

			this.currentcol++;
		}
		catch (WriteException x) {
			throw new IOException("Unable to add string.", x);
		}
    }

    public void addNumber(java.lang.Number value, WritableCellFormat fmt) throws IOException {
    	try {
    		if (value != null)
				this.currntsheet.addCell(new Number(this.currentcol, this.currentrow, value.doubleValue(), fmt));

			this.currentcol++;
		}
		catch (WriteException x) {
			throw new IOException("Unable to add number.", x);
		}
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

		if ("AddSheet".equals(code.getName())) {
			String name = StackUtil.resolveValueToString(state, code.getAttribute( "Name", "default"), true);
			this.addSheet(name);

			return ReturnOption.CONTINUE;
		}

		if ("Field".equals(code.getName())) {
			try {
				String fmt = StackUtil.resolveValueToString(state, code.getAttribute( "Format", "stdString"), true);

				if ("stdString".equals(fmt)) {
					String line = Struct.objectToString(StackUtil.resolveReference(state, code.getAttribute( "Value"), true));
					this.addString(line, QuickXLS.FMT_STR);
				}
				else if ("stdBold".equals(fmt)) {
					String line = Struct.objectToString(StackUtil.resolveReference(state, code.getAttribute( "Value"), true));
					this.addString(line, QuickXLS.FMT_STR_BOLD);
				}
				else if ("stdInteger".equals(fmt)) {
					java.lang.Number ref = Struct.objectToNumber(StackUtil.resolveReference(state, code.getAttribute( "Value"), true));
					this.addNumber(ref, QuickXLS.FMT_INT);
				}
				else if ("stdDecimal".equals(fmt)) {
					java.lang.Number ref = Struct.objectToNumber(StackUtil.resolveReference(state, code.getAttribute( "Value"), true));
					this.addNumber(ref, QuickXLS.FMT_FLOAT);
				}
				else if ("stdMoney".equals(fmt)) {
					java.lang.Number ref = Struct.objectToNumber(StackUtil.resolveReference(state, code.getAttribute( "Value"), true));
					this.addNumber(ref, QuickXLS.FMT_FLOAT_ACCOUNTING);
				}
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
