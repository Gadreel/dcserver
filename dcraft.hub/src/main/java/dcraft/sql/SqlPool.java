package dcraft.sql;

import dcraft.log.Logger;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
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

				this.conncount.decrementAndGet();

				conn = this.pool.poll();
			}
		}
		catch (Exception x) {
			// unimportant
		}
	}

	public SqlConnection getSqlConnection() throws SQLException {
		//System.out.println("get conn a: " + this.pool.size() + " / " + this.conncount);

		SqlConnection conn = this.pool.poll();

		try {
			if ((conn == null) || !conn.conn.isValid(2)) {
				conn = SqlConnection.of(this.name, DriverManager.getConnection(this.connstring));
				this.conncount.incrementAndGet();

				System.out.println("opened conn: " + System.identityHashCode(conn));

				// TODO remove this after tests
				System.out.println("db: " + this.name + " count: " + this.conncount.get());
			}

			//System.out.println("get conn b: " + this.pool.size() + " / " + this.conncount);

			//System.out.println("got conn: " + System.identityHashCode(conn));

			return conn;
		}
		catch (SQLException x) {
			Logger.error("Unable to connect to database: " + this.name);
			throw x;
		}
	}

	public void releaseConnection(SqlConnection conn) {
		//System.out.println("release conn a: " + this.pool.size() + " / " + this.conncount);

		if (conn != null) {
			if (conn.isErrored()) {
				try {
					conn.stop();
				}
				catch (Exception x) {
					// unimportant
				}

				this.conncount.decrementAndGet();

				System.out.println("closed conn: " + System.identityHashCode(conn));

				System.out.println("db: " + this.name + " count: " + this.conncount.get());
			}
			else {
				this.pool.add(conn);

				//System.out.println("returned conn: " + System.identityHashCode(conn));
			}
		}

		//System.out.println("release conn b: " + this.pool.size() + " / " + this.conncount);
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
