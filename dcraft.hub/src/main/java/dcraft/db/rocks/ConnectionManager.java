package dcraft.db.rocks;

import dcraft.db.DatabaseAudit;
import dcraft.db.IConnectionManager;
import dcraft.db.util.ByteUtil;
import static dcraft.db.Constants.*;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;
import dcraft.struct.Struct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.SysUtil;
import dcraft.xml.XElement;
import org.rocksdb.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public class ConnectionManager implements IConnectionManager {
	protected AtomicLong nextseq = new AtomicLong();
	protected XElement config = null;
	protected DatabaseAudit auditlevel = DatabaseAudit.Stamps;
	protected boolean replicate = false;
	
	protected RocksDB db = null;
	protected Options options = null;
	protected BackupEngine backupEngine = null;
	
	protected Path databasePath = null;
	protected Path backupPath = null;
	
	/*
	TODO Find ways to collect an monitor memory use - https://github.com/facebook/rocksdb/issues/3216
	
	see part about getting properties
	 */
	
	@Override
	public Path getDatabasePath() {
		return this.databasePath;
	}
	
	@Override
	public Path getBackupPath() {
		return this.backupPath;
	}
	
	@SuppressWarnings("resource")
	@Override
	public void load(XElement config) {
		Logger.trace("dcDatabase Initializing");
		
		if (config == null) {
			Logger.errorTr(210);
			return;
		}
		
		this.config = config;
		
		// TODO load audit and replication settings
		
		RocksDB.loadLibrary();
		
		this.options = new Options().setCreateIfMissing(true)
				//.createStatistics()
				//.setWriteBufferSize(8 * SizeUnit.KB)
				//.setMaxWriteBufferNumber(3)
				.setCompressionType(SysUtil.isWindows() ? CompressionType.NO_COMPRESSION : CompressionType.SNAPPY_COMPRESSION)
				.setMaxBackgroundCompactions(8)
				.setCompactionStyle(CompactionStyle.UNIVERSAL);

				/* TODO enable merge operator for inc support, see inc in this class below
				 *
				 * start with - https://github.com/facebook/rocksdb/blob/master/java/src/main/java/org/rocksdb/CassandraValueMergeOperator.java
				 *
				 * will work like uint64add, builtin to Rocks - we need to add C code for this
				 *
				.setMergeOperator(new MergeOperator() {
					@Override
					protected void disposeInternal(long l) {

					}
				});
				*/

		this.db = null;
		
		String dbpath = config.getAttribute("Path",
				ApplicationHub.getDeploymentNodePath().resolve("database/" + config.getAttribute("Name", "default")).toString());
		
		String dbbakpath = config.getAttribute("BackupPath",
				ApplicationHub.getDeploymentNodePath().resolve("database-bak/" + config.getAttribute("Name", "default")).toString());
		
		this.databasePath = Paths.get(dbpath);
		this.backupPath = Paths.get(dbbakpath);
		
		try {
			Files.createDirectories(this.databasePath);
			Files.createDirectories(this.backupPath);
			
			this.db = RocksDB.open(this.options, dbpath);
			
			// TODO be sure compacting is working
			
			// make sure we always have an alpha and an omega present
			byte[] x = this.db.get(DB_OMEGA_MARKER_ARRAY);
			
			if (x == null) {
				Adapter dbconn = this.allocateAdapter();
				
				dbconn.put(DB_ALPHA_MARKER_ARRAY, DB_ALPHA_MARKER_ARRAY);
				dbconn.put(DB_OMEGA_MARKER_ARRAY, DB_OMEGA_MARKER_ARRAY);
			}
			
			BackupableDBOptions bdb = new BackupableDBOptions(dbbakpath);
			
			this.backupEngine = BackupEngine.open(Env.getDefault(), bdb);
		}
		catch (Exception x) {
			Logger.error("dcDatabase error: " + x);
		}
		
		Logger.info( "dcDatabase Started");
	}
	
	@Override
	public void stop() {
		if (this.backupEngine != null)
			this.backupEngine.close();
		
		// TODO need a way to wait for all existing requests to complete while also not allowing new
		// requests in - perhaps a reworking of isOffline into isAvailable
		
		if (this.db != null)
			this.db.close();
		
		if (this.options != null)
			this.options.close();
		
		Logger.info("dcDatabase Stopped");
	}
	
	@Override
	public Adapter allocateAdapter() {
		return Adapter.of(this);
	}
	
	public RocksDB dbDirect() {
		return this.db;
	}
	
	public BackupEngine dbBackup() {
		return this.backupEngine;
	}
	
	@Override
	public long lastBackup() {
		if (this.backupEngine == null)
			return -1;
		
		int size = this.backupEngine.getBackupInfo().size();
		
		if (size == 0)
			return 0;
		
		return this.backupEngine.getBackupInfo().get(size - 1).timestamp();
	}
	
	@Override
	public void backup()  {
		try {
			Logger.info("Start database backup at " + (System.currentTimeMillis() / 1000));
			
			this.backupEngine.createNewBackup(db);
			
			this.backupEngine.purgeOldBackups(14);        // keep up to 2 weeks of backups, TODO configure
			
			Logger.info("End database backup at  " + (System.currentTimeMillis() / 1000));
			
			for (BackupInfo info : this.backupEngine.getBackupInfo())
				Logger.info("Database backup history: " + info.backupId() + " -  " + info.timestamp());
		}
		catch (RocksDBException x) {
			Logger.error("Error backing up the database: " + x);
			
			// TODO alert system admin
		}
	}
	
	public boolean isAuditDisabled() {
		return (this.auditlevel == DatabaseAudit.None);
	}
	
	public String allocateSubkey() {
		return RndUtil.nextUUId();
	}
	
	/**
	 * @param offset in seconds from now
	 * @return a valid timestamp for use in dcDb auditing
	 */
	@Override
	synchronized public BigDecimal allocateStamp(int offset) {
		if (this.auditlevel == DatabaseAudit.None)
			return BigDecimal.ZERO;
		
		long ns = this.nextseq.getAndIncrement();
		
		if (ns > 9999) {
			this.nextseq.set(0);
			ns = 0;
		}
		
		BigDecimal ret = new BigDecimal("-" + ZonedDateTime.now(ZoneId.of("UTC")).plusSeconds(offset).toInstant().toEpochMilli() + "." +
				StringUtil.leftPad(ns + "", 4, "0") + ApplicationHub.getNodeId());
		
		return ret;
	}
	
	synchronized public Long inc(byte[] key, int i) throws RocksDBException {
		//this.db.merge(writeOpts, key, value);
		// TODO eventually replace this with
		//MergeOperator mo = new generic
		
		Long id = Struct.objectToInteger(ByteUtil.extractValue(this.db.get(key)));
		
		id = (id == null) ? i : id + i;
		
		this.db.put(key, ByteUtil.buildValue(id));
		
		return id;
	}
}
