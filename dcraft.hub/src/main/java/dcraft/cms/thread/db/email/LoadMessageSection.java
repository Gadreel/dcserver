package dcraft.cms.thread.db.email;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class LoadMessageSection implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);
		RecordStruct data = request.getDataAsRecord();

		String mtype = data.getFieldAsString("Type");
		String section = data.getFieldAsString("Section");

		Path mfile = OperationContext.getOrNull().getSite().getPath().resolve("emails/dcm/threads/" + mtype + ".dcs.xml");

		if (Files.exists(mfile)) {
			XElement mxml = XmlReader.loadFile(mfile, false, true);

			if (mxml != null) {
				for (XElement element : mxml.selectAll("dcs.TranslationTemplate")) {
					if (section.equals(element.attr("Name"))) {
						callback.returnValue(RecordStruct.record()
								.with("Content", element.selectFirst("Tr").getValue())			// TODO support locales someday
						);

						return;
					}
				}
			}
		}

		callback.returnEmpty();
	}
}
