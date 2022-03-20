package dcraft.sql;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import z.tws.batch.Frequent;
import z.tws.batch.Nightly;
import z.tws.services.BatchScript;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class SqlPool {
	static public SqlPool of(String name, String connstr) {
		SqlPool pool = new SqlPool();
		pool.connstring = connstr;
		pool.name = name;
		return pool;
	}

	protected String connstring = null;
	protected String name = null;

	protected ConcurrentLinkedQueue<SqlConnection> pool = new ConcurrentLinkedQueue<>();
	protected AtomicLong conncount = new AtomicLong();

	public SqlPool withConnString(String v) {
		this.connstring = v;
		return this;
	}

	public void stop() {
		try {
			SqlConnection conn = this.pool.poll();

			while (conn != null) {
				try {
					conn.stop();
				}
				catch (Exception x) {
					// unimportant
				}

				conn = this.pool.poll();
			}
		}
		catch (Exception x) {
			// unimportant
		}
	}

	public SqlConnection getSqlConnection() throws SQLException {
		SqlConnection conn = this.pool.poll();

		try {
			if ((conn == null) || !conn.conn.isValid(2)) {
				conn = SqlConnection.of(this.name, DriverManager.getConnection(this.connstring));
				this.conncount.incrementAndGet();

				// TODO remove this after tests
				System.out.println("db: " + this.name + " count: " + this.conncount.get());
			}

			return conn;
		}
		catch (SQLException x) {
			Logger.error("Unable to connect to database: " + this.name);
			throw x;
		}
	}

	public void releaseConnection(SqlConnection conn) {
		if (conn != null)
			this.pool.add(conn);
	}

	public boolean testConnection() {
		try {
			SqlConnection conn = this.getSqlConnection();

			if (conn == null)
				return false;

			this.releaseConnection(conn);

			return true;
		}
		catch (SQLException x) {
			return false;
		}
	}
}
