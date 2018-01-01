/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.sql;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

// TODO there is much much more to do to make this easier to support many different db engines
public class SqlHub {
	static protected Map<String, SqlDatabase> databases = new HashMap<String, SqlHub.SqlDatabase>();
	
	static public String getNowAsString() {
		return TimeUtil.sqlStampFmt.format(TimeUtil.now());
	}
	
	static public String getDateAsString(ZonedDateTime dt) {
		if (dt == null)
			return null;
		
		return TimeUtil.sqlStampFmt.format(dt.toInstant().atZone(ZoneId.of("UTC")));
	}
	
	static public String getDateAsString(long dt) {
		return TimeUtil.sqlStampFmt.format(Instant.ofEpochMilli(dt).atZone(ZoneId.of("UTC")));
	}
	
	static public SqlDatabase getDatabase(String name) {
		return SqlHub.databases.get(name);
	}
	
	static public boolean init(XElement config) {
		if (config == null)
			return true;
		
		for (XElement del : config.selectAll("Database")) {
			String name = del.getAttribute("Name", "default");
			
			SqlDatabase db = new SqlDatabase();
			db.name = name;
			
			if (! db.init(del))
				return false;
			
			SqlHub.databases.put(name, db);
		}
		
		return true;
	}
	
	static public void stop() {
		for (SqlDatabase db : SqlHub.databases.values())
			db.stop();
	}
	
	static public class SqlDatabase {
		protected String connstring = null;
		protected String name = null;
		
		protected SqlEngine engine = null;
		
		// single connection style engines
		protected Connection conn = null;	
		protected Semaphore lock = new Semaphore(0);
		
		protected boolean poolmode = false;
		protected ConcurrentLinkedQueue<Connection> pool = new ConcurrentLinkedQueue<>();
	
		public boolean init(XElement del) {
			if (del == null) 
				return false;
	
			try {
				String driver = del.getAttribute("Driver");
				
		        Class.forName(driver);
	    		
		        this.connstring = ApplicationHub.getClock().getObfuscator().decryptHexToString(
	    				del.getAttribute("Connection")
	    		);
		        
		        // if null then try unencrypted
		        if (this.connstring == null)
		        	this.connstring = del.getAttribute("Connection");
		        
		        if (this.connstring.startsWith("jdbc:h2:"))
		        	this.engine = SqlEngine.H2;
		        else if (this.connstring.startsWith("jdbc:sqlserver:"))
		        	this.engine = SqlEngine.SqlServer;
		        else if (this.connstring.startsWith("jdbc:mariadb:"))
		        	this.engine = SqlEngine.MariaDb;
		        else if (this.connstring.startsWith("jdbc:mysql:"))
		        	this.engine = SqlEngine.MySQL;
		        else {
					Logger.errorTr(189, this.connstring.substring(0, Math.min(this.connstring.length(), 15)));
					return false;
		        }
		        
		        this.poolmode = "Pooled".equals(del.getAttribute("Mode"));
		        
		        if (this.engine == SqlEngine.SqlServer || this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL) {
		        	Logger.info(0, "Using database " + this.name + " with multiple connections.");
		        }
		        else {
			        this.conn = DriverManager.getConnection(this.connstring);
			        
			        Logger.info(0, "Connected to database " + this.name + " single connection.");
				
			        this.releaseConnection(this.conn);
		        }
		        
		        return true;
			} 
			catch (Exception x) {
				Logger.errorTr(190, this.name, x);
				return false;
			}
		}
		
		public void stop() {
			try {
				if (this.engine == SqlEngine.SqlServer || this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL) {
					if (this.poolmode) {
						Connection conn = this.pool.poll();
						
						while (conn != null) {
							try {
								conn.close();
							} 
							catch (Exception x) {
								// unimportant
							}
							
							conn = this.pool.poll();
						}
					}
				}
				else if (this.engine == SqlEngine.H2) 
					this.conn.close();
			} 
			catch (Exception x) {
				// unimportant
			}
		}
		
		public Connection acquireConnection() {
			CountHub.allocateNumberCounter("dcSqlAcquireConnection").increment();
			
			if (this.engine == SqlEngine.SqlServer  || this.engine == SqlEngine.MariaDb  || this.engine == SqlEngine.MySQL) {
		        try {
					Connection conn = null;

					if (this.poolmode) {
						conn = this.pool.poll();
						
						if ((conn != null) && !conn.isValid(2)) 
							conn = null;
					}
					
					if (conn == null) {
						conn = DriverManager.getConnection(this.connstring);
						CountHub.allocateNumberCounter("dcSqlConnectionCreate").increment();
					}
					
					return conn;
				} 
		        catch (SQLException x) {
				}
	        }
	        else if (this.engine == SqlEngine.H2) {
				try {
					this.lock.acquire();
					return this.conn;
				} 
				catch (InterruptedException e) {
				}
	        }
			
			CountHub.allocateNumberCounter("dcSqlAcquireConnectionFail").increment();
			
			return null;
		}
		
		public void releaseConnection(Connection conn) {
			CountHub.allocateNumberCounter("dcSqlReleaseConnection").increment();
			
			if (this.engine == SqlEngine.SqlServer || this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL) {
				if (this.poolmode) {
					this.pool.add(conn);
					return;
				}
				
		        try {
					conn.close();
				} 
		        catch (SQLException x) {
				}
	        }
	        else if (this.engine == SqlEngine.H2) {
	        	this.lock.release();
	        }
		}

		public SqlEngine getEngine() {
			return this.engine;
		}
		
		public boolean testConnection() {
			Connection conn = this.acquireConnection();
			
			if (conn == null) 
				return false;
		    
	    	this.releaseConnection(conn);
	    	
		    return true;
		}
		
		// warning - may not use same connection between calls
		public String getLastIdSql() {
			if (this.engine == SqlEngine.H2)
				return "SELECT IDENTITY() AS lid";
			
			if (this.engine == SqlEngine.SqlServer)
				return "SELECT @@IDENTITY AS lid";
			
			if (this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL)
				return "SELECT LAST_INSERT_ID() AS lid";
			
			return null;
		}
		
		/*
		 * We should always talk in UTC...
		 *  
		 * @return
		 */
		public String nowFunc() {
			if (this.getEngine() == SqlEngine.SqlServer)
				return "SYSUTCDATETIME()";  // TODO review if this works "GETUTCDATE()";
			
			if (this.getEngine() == SqlEngine.MariaDb || this.getEngine() == SqlEngine.MySQL)
				return "UTC_TIMESTAMP()";

			return "NOW()";
		}
		
		// TODO
		// only support MINUTES at present
		public String timeUnit(TimeUnit unit) {
			if (unit == TimeUnit.MINUTES)
				return "MINUTE";
			
			return null;
		}
		
		public String modNowFunc(TimeUnit unit, int amt) {
			return this.modTimeFunc(this.nowFunc(), unit, amt);
		}
		
		public String modTimeFunc(String time, TimeUnit unit, int amt) {
			String unitname = this.timeUnit(unit);
			
			// h2 syntax
			String expr = "DATEADD('" + unitname + "', " + amt + ", " + time + ") ";
			
			if (this.getEngine() == SqlEngine.SqlServer)
				expr = "DATEADD(" + unitname + ", " + amt + ", " + time + ") ";
			
			if (this.getEngine() == SqlEngine.MariaDb || this.getEngine() == SqlEngine.MySQL)
				expr = "DATE_ADD(" + time + ", INTERVAL " + amt + " " + unitname + ") ";
			
			return expr;
		}
		
		public String formatColumn(String name) {
			if (this.getEngine() == SqlEngine.SqlServer)
				return "[" + name + "]";
			
			if (this.getEngine() == SqlEngine.MariaDb || this.getEngine() == SqlEngine.MySQL)
				return "`" + name + "`";

			// TODO check what H2 uses...
			
			return name;
		}
		
		public void processException(Exception x) {
			if (x instanceof SQLException) {
				SQLException sx = (SQLException)x;
				
				if (this.getEngine() == SqlEngine.MariaDb || this.getEngine() == SqlEngine.MySQL) {
					// duplicate id error - this is not always an error for the caller
					if (sx.getErrorCode() == 1062) {
						Logger.warnTr(194, this.name, x);	
						return;
					}
				}
				else if (this.getEngine() == SqlEngine.SqlServer) {
					// duplicate id error - this is not always an error for the caller
					if (sx.getErrorCode() == 2627) {
						Logger.warnTr(194, this.name, x);		
						return;
					}
				}
				
				// TODO add other databases
				
				Logger.errorTr(195, this.name, ((SQLException) x).getErrorCode(), x);	    	
				return;
			}
			
			Logger.errorTr(186, this.name, x);	    	
		}
		
		public Integer executeFreestyle(String sql, Object... params) {
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
		    
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {
			    if (pstmt == null)
			    	return null;
			
				CountHub.countObjects("dcSqlExecuteCount", pstmt);
				
				return pstmt.executeUpdate();
		    } 
		    catch (SQLException x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlExecuteFail", sql);
				return null;
		    } 
		    finally {
		    	this.releaseConnection(conn);
		    }
		}
		
		// return a list of records where each row is a record in this collection
		// -- NOTE: column names are all lower case
		public ListStruct executeQueryFreestyle(String sql, Object... params) {
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {
			    if (pstmt == null)
			    	return null;
			
				CountHub.countObjects("dcSqlQueryCount", pstmt);
		    	
				// MariaDB hint that this turns on streaming... review TODO
				//pstmt.setFetchSize(Integer.MIN_VALUE);
				
				try (ResultSet rs = pstmt.executeQuery()) {
					ResultSetMetaData md = rs.getMetaData();
				    int columns = md.getColumnCount();
				    
					ListStruct list = new ListStruct();
					
					while (rs.next()) {
						RecordStruct rec = new RecordStruct();
						
						for (int i = 1; i <= columns; i++) 
							rec.with(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
						
						list.withItem(rec);
					}			
					
					return list;
				}
		    } 
		    catch (SQLException x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlQueryFail", sql);
				return null;
		    } 
		    finally {
		    	this.releaseConnection(conn);
		    }
		}
		
		// return a list of records where each row is a record in this collection
		public ListStruct executeQueryPage(SqlSelect[] select, String from, String where, String groupby, String orderby, int offset, int pagesize, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
			// prepare
		    try (PreparedStatement pstmt = this.prepPage(conn, select, from, where, groupby, orderby, offset, pagesize, params)) {
			    if (pstmt == null)
			    	return null;
			    
			    // execute
		    	return this.callAndFormat(select, pstmt);
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a list of records where each row is a record in this collection
		public ListStruct executeQueryLimit(SqlSelect[] select, String from, String where, String groupby, String orderby, int limit, boolean distinct, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
			// prepare
		    try (PreparedStatement pstmt = this.prepLimit(conn, select, from, where, groupby, orderby, limit, distinct, params)) {
			    if (pstmt == null)
			    	return null;
			    
			    // execute
		    	return this.callAndFormat(select, pstmt);
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a list of records where each row is a record in this collection
		public ListStruct executeQuery(SqlSelect[] select, String from, String where, String groupby, String orderby, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
			// prepare
		    try (PreparedStatement pstmt = this.prep(conn, select, from, where, groupby, orderby, params)) {
			    if (pstmt == null)
			    	return null;
			    
			    // execute
		    	return this.callAndFormat(select, pstmt);
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a single value (row/column) from table 
		public Struct executeQueryScalar(SqlSelect select, String from, String where, String orderby, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
			SqlSelect[] selects = new SqlSelect[] { select };
			
			// prepare
		    try (PreparedStatement pstmt = this.prep(conn, selects, from, where, null, orderby, params)) {
			    if (pstmt == null)
			    	return null;
		    	
			    // execute
		    	ListStruct lrs = this.callAndFormat(selects, pstmt);
		    	
		    	if ((lrs != null) && (lrs.size() > 0)) {
		    		RecordStruct rec = lrs.getItemAsRecord(0);
		    		
		    		return rec.getField(select.name);
		    	}
		    	
		    	return null;
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a single String value (row/column) from table 
		public String executeQueryString(String col, String from, String where, String orderby, Object... params) {
			Struct rsres = this.executeQueryScalar(new SqlSelectString(col), from, where, orderby, params);
			
			if (rsres == null) 
				return null;
			
			return Struct.objectToString(rsres);
		}
		
		// return a single Integer value (row/column) from table 
		public Long executeQueryInteger(String col, String from, String where, String orderby, Object... params) {
			Struct rsres = this.executeQueryScalar(new SqlSelectInteger(col), from, where, orderby, params);
			
			if (rsres != null) 
				return null;
			
			return Struct.objectToInteger(rsres);
		}
		
		// return a single Boolean value (row/column) from table 
		public Boolean executeQueryBoolean(String col, String from, String where, String orderby, Object... params) {
			Struct rsres = this.executeQueryScalar(new SqlSelectBoolean(col), from, where, orderby, params);
			
			if (rsres != null) 
				return null;
			
			return Struct.objectToBoolean(rsres);
		}
		
		// return a single row from table 
		public RecordStruct executeQueryRecord(SqlSelect[] selects, String from, String where, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
			// prepare
		    try (PreparedStatement pstmt = this.prep(conn, selects, from, where, null, null, params)) {
			    if (pstmt == null)
			    	return null;
			    
			    // execute
		    	ListStruct lrs = this.callAndFormat(selects, pstmt);
		    	
		    	if ((lrs != null) && (lrs.size() > 0))
		    		return lrs.getItemAsRecord(0);
		    
		    	return null;
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a single row (the first) from table 
		public RecordStruct executeQueryRecordFirst(SqlSelect[] selects, String from, String where, String orderby, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}

			// prepare
		    try (PreparedStatement pstmt = this.prep(conn, selects, from, where, null, orderby, params)) {
			    if (pstmt == null)
			    	return null;
			    
			    // execute
		    	ListStruct lrs = this.callAndFormat(selects, pstmt);
		    	
		    	if ((lrs != null) && (lrs.size() > 0))
		    		return lrs.getItemAsRecord(0);
		    
		    	return null;
			} 
	    	catch (SQLException x) {
			    this.processException(x);
				return null;
			}
	    	finally {
		    	// release
		    	this.releaseConnection(conn);
	    	}
		}
		
		// return a list of records where each row is a record in this collection
		public ListStruct callAndFormat(SqlSelect[] select, PreparedStatement pstmt) {
			CountHub.countObjects("dcSqlQueryCount", pstmt);
			
			// MariaDB hint that this turns on streaming... review TODO
			//pstmt.setFetchSize(Integer.MIN_VALUE);
	    	
		    try (ResultSet rs = pstmt.executeQuery()) {					
				ResultSetMetaData md = rs.getMetaData();
			    int columns = md.getColumnCount();
			    
			    if (columns > select.length) {
					Logger.error(1, "Mismatched column name list");		// TODO code tr
					return null;
			    }
			    
			    ListStruct list = ListStruct.list();
			    
				while (rs.next()) {
					RecordStruct rec = new RecordStruct();
					
					for(int i=1; i<=columns; i++) {
						String name = select[i - 1].name;
						
						rec.with(name, select[i - 1].format(rs.getObject(i)));
					}
					
					list.withItem(rec);
				}
				
				return list;
		    } 
		    catch (Exception x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlQueryFail", pstmt.toString());
				return null;
		    } 
		}

		// caller needs to close statememt
		// will not return open statement and errors
		public PreparedStatement prepStatement(Connection conn, String sql, Object... params) {
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
			
		    PreparedStatement pstmt = null;
		    
		    try {					
		    	pstmt = conn.prepareStatement(sql); 

		    	for (int i = 0; i < params.length; i++) {
		    		Object param = params[i];
		    		
		    		// null params are intentionally not supported - allows us to optionally add params to a complex query
		    		// for NULL support see SqlNull enum
		    		if (param == null)
		    			continue;
		    		
		    		if (param instanceof ZonedDateTime)
		    			param = SqlHub.getDateAsString((ZonedDateTime) param);
		    		
		    		if (param instanceof String) {
		    			if (this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL)
		    				pstmt.setString(i + 1, (String)param);
		    			else if (this.engine == SqlEngine.SqlServer) 
		    				pstmt.setNString(i + 1, (String)param);
		    			else if (this.engine == SqlEngine.H2) 
		    				pstmt.setNString(i + 1, (String)param);
		    			
		    			continue;
		    		}
		    		
		    		if (param instanceof BigDecimal) {
		    			pstmt.setBigDecimal(i + 1, (BigDecimal) param);
		    			continue;
		    		}
		    		
		    		if (param instanceof Double) {
		    			pstmt.setDouble(i + 1, (double) param);
		    			continue;
		    		}
		    		
		    		if (param instanceof Integer) {
		    			pstmt.setInt(i + 1, (int) param);
		    			continue;
		    		}
		    		
		    		if (param instanceof Long) {
		    			pstmt.setLong(i + 1, (long) param);
		    			continue;
		    		}
		    		
		    		if (param instanceof SqlNull) {
		    			if (param == SqlNull.DateTime)
		    				pstmt.setNull(i + 1, Types.DATE);
		    			else if (param == SqlNull.VarChar) {
			    			if (this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL)
			    				pstmt.setNull(i + 1, Types.VARCHAR);
			    			else if (this.engine == SqlEngine.SqlServer) 
			    				pstmt.setNull(i + 1, Types.NVARCHAR);
			    			else if (this.engine == SqlEngine.H2) 
			    				pstmt.setNull(i + 1, Types.NVARCHAR);
		    			}
		    			else if (param == SqlNull.BigDecimal)
		    				pstmt.setNull(i + 1, Types.DECIMAL);
		    			else if (param == SqlNull.Double)
		    				pstmt.setNull(i + 1, Types.FLOAT);
		    			else if (param == SqlNull.Int)
		    				pstmt.setNull(i + 1, Types.INTEGER);
		    			else if (param == SqlNull.Long)
		    				pstmt.setNull(i + 1, Types.BIGINT);
		    			else if (param == SqlNull.Text)
		    				pstmt.setNull(i + 1, Types.CLOB);		// TODO test
		    			
		    			continue;
		    		}
		    	}
				
				return pstmt;
		    } 
		    catch (Exception x) {
		    	this.processException(x);
			
				CountHub.countObjects("dcSqlPrepFail", sql);
		    	
		    	try {
		    		if (pstmt != null)
		    			pstmt.close();
				} 
		    	catch (SQLException x2) {
				}
			    
				return null;
		    } 
		}

		// caller needs to close statememt
		public PreparedStatement prepPage(Connection conn, SqlSelect[] select, String from, String where, String groupby, String orderby, int offset, int pagesize, Object... params) {
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
		    
		    String sql = "SELECT ";
		    
		    for (int i = 0; i < select.length; i++) {
		    	if (i > 0)
		    		sql += ", ";
		    	
		    	sql += select[i].toSql(this);
		    }
		    
		    sql += " FROM " + from;
		    
		    if (StringUtil.isNotEmpty(where))
		    	sql += " WHERE " + where;
		    
		    if (StringUtil.isNotEmpty(groupby))
		    	sql += " GROUP BY " + groupby;
		    
	        if (StringUtil.isEmpty(orderby)) {
	        	Logger.error(1, "Order By required with paging");
				return null;
			}

			if (this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL) {
		        sql = "SELECT * FROM ( " + sql + " ) AS recset ORDER BY " 
		        	+ orderby + " LIMIT " + offset + "," + pagesize + ";";
			}
			else if (this.engine == SqlEngine.SqlServer) {
		        sql = "WITH RecordPager AS ( " 
		        		+ "SELECT *, ROW_NUMBER() OVER (ORDER BY " + orderby + ") AS RowNumber " 
		        		+ "FROM ( " + sql + " ) AS recset " 
		       		+ ") "
		       		+ "SELECT * FROM RecordPager WHERE RowNumber BETWEEN " + (offset + 1) 
		       		+ " AND " + (offset + pagesize);				
			}
			else if (this.engine == SqlEngine.H2) {
				// TODO
			}
		    
			// TODO support for other dbms
			// http://en.wikipedia.org/wiki/Select_(SQL)
			// http://stackoverflow.com/questions/2771439/jdbc-pagination
			// http://stackoverflow.com/questions/1986998/resultset-to-pagination
			// http://stackoverflow.com/questions/971964/limit-10-20-in-sqlserver
			
			return this.prepStatement(conn, sql, params);
		}

		// caller needs to close statememt
		public PreparedStatement prepLimit(Connection conn, SqlSelect[] select, String from, String where, String groupby, String orderby, int limit, boolean distinct, Object... params) {
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
		    
		    String sql = "SELECT ";
		    
		    if (distinct)
		    	sql += "DISTINCT ";
		    
		    for (int i = 0; i < select.length; i++) {
		    	if (i > 0)
		    		sql += ", ";
		    	
		    	sql += select[i].toSql(this);
		    }
		    
		    sql += " FROM " + from;
		    
		    if (StringUtil.isNotEmpty(where))
		    	sql += " WHERE " + where;
		    
		    if (StringUtil.isNotEmpty(groupby))
		    	sql += " GROUP BY " + groupby;
		    
	        if (StringUtil.isEmpty(orderby)) {
	        	Logger.error(1, "Order By required with limit");
				return null;
			}

			if (this.engine == SqlEngine.MariaDb || this.engine == SqlEngine.MySQL) {
		        sql = "SELECT * FROM ( " + sql + " ) AS unset ORDER BY " + orderby + " LIMIT " + limit + ";";
			}
			else if (this.engine == SqlEngine.SqlServer) {
		        sql = "SELECT TOP " + limit + " * FROM ( " + sql + " ) AS unset ORDER BY " + orderby;				
			}
			else if (this.engine == SqlEngine.H2) {
				// TODO
			}
		    
			return this.prepStatement(conn, sql, params);
		}

		// caller needs to close statememt
		public PreparedStatement prep(Connection conn, SqlSelect[] select, String from, String where, String groupby, String orderby, Object... params) {
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return null;
			}
		    
		    String sql = "SELECT ";
		    
		    for (int i = 0; i < select.length; i++) {
		    	if (i > 0)
		    		sql += ", ";
		    	
		    	sql += select[i].toSql(this);
		    }
		    
		    sql += " FROM " + from;
		    
		    if (StringUtil.isNotEmpty(where))
		    	sql += " WHERE " + where;
		    
		    if (StringUtil.isNotEmpty(groupby))
		    	sql += " GROUP BY " + groupby;
		    
		    if (StringUtil.isNotEmpty(orderby))
		    	sql += " ORDER BY " + orderby;
		    
			return this.prepStatement(conn, sql, params);
		}
		
		public Integer executeUpdate(String sql, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return 0;
			}
			
			// prep
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {
			    if (pstmt == null)
			    	return 0;
			
				CountHub.countObjects("dcSqlUpdateCount", pstmt);
		    
				// execute
				return pstmt.executeUpdate(); 
		    } 
		    catch (Exception x) {
		    	this.processException(x);
			
				CountHub.countObjects("dcSqlUpdateFail", sql);
		    	
		    	return 0;
		    } 
		    finally {
		    	// release
		    	this.releaseConnection(conn);
		    }
		}
		
		public Integer executeDelete(String sql, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return 0;
			}
			
			// prep
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {					
			    if (pstmt == null)
			    	return 0;
			
				CountHub.countObjects("dcSqlDeleteCount", pstmt);
		    
				// execute
				return pstmt.executeUpdate(); 
		    } 
		    catch (Exception x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlDeleteFail", sql);
				return 0;
		    } 
		    finally {
		    	// release
		    	this.releaseConnection(conn);
		    }
		}
				
		public Long executeInsertReturnId(String sql, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return 0L;
			}
			
			// prep
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {					
			    if (pstmt == null)
			    	return 0L;
			
				CountHub.countObjects("dcSqlInsertCount", pstmt);
		    	
				// execute
				int cnt = pstmt.executeUpdate(); 

				pstmt.close();
				
				if (cnt == 1) {
					try (PreparedStatement pstmt2 = conn.prepareStatement(this.getLastIdSql())) {
						ResultSet rs = pstmt2.executeQuery();
						
						if (rs.next()) 
							return rs.getLong("lid");
					}
				}
				
				return 0L;
		    } 
		    catch (Exception x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlInsertFail", sql);
				return 0L;
		    } 
		    finally {
		    	// release
		    	this.releaseConnection(conn);
		    }
		}

		// TODO look into "get generated keys"
		public Integer executeInsert(String sql, Object... params) {
			// acquire
			Connection conn = this.acquireConnection();
			
			// if connection is bad/missing then just try again later
			if (conn == null) {
				Logger.errorTr(185, this.name);
				return 0;
			}
			
			// prep
		    try (PreparedStatement pstmt = this.prepStatement(conn, sql, params)) {					
			    if (pstmt == null)
			    	return 0;
			
				CountHub.countObjects("dcSqlInsertCount", pstmt);
		    	
				// execute
				return pstmt.executeUpdate(); 
		    } 
		    catch (Exception x) {
		    	this.processException(x);
				CountHub.countObjects("dcSqlInsertFail", sql);
				return 0;
		    } 
		    finally {
		    	// release
		    	this.releaseConnection(conn);
		    }
		}
	}
}
