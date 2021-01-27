package dcraft.cms.thread.db.email;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.IOUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateMessageSection implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		RecordStruct data = request.getDataAsRecord();

		String mtype = data.getFieldAsString("Type");
		String section = data.getFieldAsString("Section");
		String content = data.getFieldAsString("Content");

		Path mfile = OperationContext.getOrNull().getSite().getPath().resolve("emails/dcm/threads/" + mtype + ".dcs.xml");

		if (Files.exists(mfile)) {
			XElement mxml = XmlReader.loadFile(mfile, false, true);

			if (mxml != null) {
				for (XElement element : mxml.selectAll("dcs.TranslationTemplate")) {
					if (section.equals(element.attr("Name"))) {
						element.clearChildren();

						XElement tr = XElement
								.tag("Tr");

						tr.setValue(content);

						element.with(tr);

						IOUtil.saveEntireFile(mfile, mxml.toPrettyString());

						break;
					}
				}
			}
		}

		callback.returnEmpty();
	}
}
