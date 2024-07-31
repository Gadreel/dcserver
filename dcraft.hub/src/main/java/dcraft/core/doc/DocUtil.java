package dcraft.core.doc;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.mail.dcc.HtmlPrinter;
import dcraft.struct.RecordStruct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.OutputWrapper;
import dcraft.xml.XElement;
import dcraft.xml.XmlPrinter;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class DocUtil {
	static public String formatText(RecordStruct proc, XElement body) {
		if (body != null) {
			return StringUtil.stripWhitespace(body.getText());
		}

		return null;
	}

	static public CommonPath folderToVFolder(CommonPath path) {
		return path.getParent().resolve(path.getFileName() + ".v");
	}

	static public CommonPath vFolderToFolder(CommonPath path) {
		String filename = path.getFileName();

		return path.getParent().resolve(filename.substring(0, filename.length() - 2));
	}

	static public boolean isVFolder(CommonPath path) {
		return path.getFileName().endsWith(".v");
	}
}
