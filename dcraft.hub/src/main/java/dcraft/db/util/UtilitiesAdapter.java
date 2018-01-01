package dcraft.db.util;

public class UtilitiesAdapter {
	/* TODO rethink
	protected DatabaseManager db = null;
	protected DatabaseInterface conn = null;
	protected DatabaseTask task = null;
	// TODO protected TenantManager dm = null;		replace this with a very simple concept of TM, used in indexing only
	protected TablesAdapter tables = null;
	
	// don't call for general code...
	public UtilitiesAdapter(DatabaseManager db) {
		this.db = db;
		this.conn = db.allocateAdapter();
		
		RecordStruct req = new RecordStruct();
		
		req.withField("Replicate", false);		// means this should replicate, where as Replicating means we are doing replication currently
		req.withField("Name", "dcRebuildIndexes");
		req.withField("Stamp", this.db.allocateStamp(0));
		req.withField("Tenant", DB_GLOBAL_ROOT_TENANT);
		
		this.task = new DatabaseTask();
		this.task.setRequest(req);
		
		this.tables = new TablesAdapter(conn, task);
	}
	
	public void rebuildIndexes() {
		TablesAdapter ta = new TablesAdapter(conn, task); 
		BigDateTime when = BigDateTime.nowDateTime();
		
		ta.traverseSubIds(DB_GLOBAL_TENANT_DB, DB_GLOBAL_ROOT_TENANT, Constants.DB_GLOBAL_TENANT_IDX_DB, when, false, new Function<Object,Boolean>() {				
			@Override
			public Boolean apply(Object t) {
				String did = t.toString();
				
				System.out.println("Indexing domain: " + did);
				
				task.pushTenant(did);
				
				try {
					// see if there is even such a table in the schema
					tables.rebuildIndexes(TenantHub.resolveTenant(did), when);
					
					return true;
				}
				catch (Exception x) {
					System.out.println("dcRebuildIndexes: Unable to index: " + did);
					Logger.error("rebuildTenantIndexes error: " + x);
				}
				finally {
					task.popTenant();
				}
				
				return false;
			}
		});
	}
	*/
}
