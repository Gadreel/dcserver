package dcraft.db.util;

import java.nio.file.Path;

import dcraft.util.cb.CountDownCallback;

public class Export {
	// TODO stream results directly to files - one query for export per table (not lots of queries)
	static public void export(ExportTable table, Path dest, CountDownCallback dcallback) {
		/*
		try {
			PrintStream fos3 = new PrintStream(Files.newOutputStream(dest));
			ICompositeBuilder json = new JsonStreamBuilder(fos3, true);
			json.startList();
			
			final OperationOutcomeEmpty occallback = new OperationOutcomeEmpty() {
				@Override
				public void callback() {
					try {
						json.endList();
					} 
					catch (BuilderStateException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					IOUtil.closeQuietly(fos3);
					
					table.done();
					
					dcallback.countDown();
				}
			};
			
			ISelectField selt = new SelectField().withField("Id");
			ListDirectRequest req = new ListDirectRequest(table.table, selt);
			
			DatabaseHub.defaultDb().submit(req, new StructOutcome() {
				@Override
				public void callback(CompositeStruct result) throws OperatingContextException {
					if (this.hasErrors()) {
						System.out.println(table + " bad load: " + result);
						occallback.returnResult();
					}
					else {
						ListStruct ids = (ListStruct) result;
						
						if (ids.size() == 0) {
							occallback.returnResult();
							return;
						}
						
						final CountDownCallback cdcallback = new CountDownCallback(ids.size(), occallback);
						
						for (Struct itm : ids.items()) {
							String id = itm.toString();
							
							LoadRecordRequest req = new LoadRecordRequest()
								.withTable(table.table)
								.withId(id)
								.withCompact(false);
							
							DatabaseHub.defaultDb().submit(req, new StructOutcome() {
								@Override
								public void callback(CompositeStruct result) throws OperatingContextException {
									System.out.println("loaded: " + table.table + " - " + id);
									
									if (this.hasErrors())
										System.out.println(table + " bad load: " + result + " - " + id);
									else {
										synchronized (json) {
											try {
												RecordStruct rs = (RecordStruct) result;
												
												rs = table.transform(rs);
												
												if (rs != null)
													rs.toBuilder(json);
												//else
												//	System.out
												//			.println("could not find: " + id + " in domain: " + TaskContext.get().getUserContext().getDomainId());
											} 
											catch (BuilderStateException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}										
									}
									
									cdcallback.countDown();
								}
							});
						}
					}
				}
			});
		}
		catch (Exception x) {
			System.out.println(table + " bad load: " + x);
			
			table.done();
			
			dcallback.countDown();
		}
		*/
		
		dcallback.countDown();
	}
}
