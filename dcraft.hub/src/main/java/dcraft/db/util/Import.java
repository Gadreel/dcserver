package dcraft.db.util;

import java.nio.file.Path;

import dcraft.hub.op.OperatingContextException;
import dcraft.util.cb.CountDownCallback;

public class Import {
	public static void importData(String table, Path source, CountDownCallback mcallback) throws OperatingContextException {
		/* TODO
		CompositeStruct res = CompositeParser.parseJson(source);
		
		if (res == null) {
			Logger.error(table + " bad file: " + source);
			mcallback.countDown();
			return;
		}
		
		ListStruct records = (ListStruct) res;
		
		if ((records == null) || (records.size() == 0)) {
			Logger.warn("No records to import: " + source);
			mcallback.countDown();
			return;
		}
		
		CountDownCallback cdcallback = new CountDownCallback(records.size(), new OperationOutcomeEmpty() {
			@Override
			public void callback() {
				mcallback.countDown();
			}
		});
		
		for (Struct itm : records.items()) {
			DatabaseHub.defaultDb().submit(
				new ImportRecordRequest(table, (RecordStruct)itm),
				new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						* TODO review and restore
						System.out.println("imported: " + table + " - " + req.getId());
						
						if (res.hasErrors())
							System.out.println(table + " bad import: " + res + " - " + req.getId());
						*
						
						cdcallback.countDown();
					}
				});
		}
		*/
	}
}
