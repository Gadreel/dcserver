package dcraft.sql;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.FuncResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;

public class SqlConnection implements AutoCloseable {
    static public SqlConnection of(String name, Connection conn) {
        SqlConnection sqlConnection = new SqlConnection();
        sqlConnection.conn = conn;
        sqlConnection.name = name;
        return sqlConnection;
    }

    protected Connection conn = null;
    protected String name = null;

    public Connection getConn() {
        return this.conn;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public void close() throws Exception {
        SqlPool pool = ResourceHub.getResources().getSqlDatabases().getSqlDatabase(this.name);

        if (pool != null)
            pool.releaseConnection(this);       // put back in pool when closed
    }

    synchronized public void stop() {
        Connection connection = this.conn;

        this.conn = null;

        try {
            connection.close();
        }
        catch (SQLException x) {
            Logger.error("Error closing sql connection: " + x);
        }
    }

    public BaseStruct getVar(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getVar(this.conn, sql, params);
    }

    public String getVarString(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getVarString(this.conn, sql, params);
    }

    public Long getVarInteger(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getVarInteger(this.conn, sql, params);
    }

    public Boolean getVarBoolean(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getVarBoolean(this.conn, sql, params);
    }

    public RecordStruct getRow(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getRow(this.conn, sql, params);
    }

    public ListStruct getResults(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.getResults(this.conn, sql, params);
    }

    // return a list of records where each row is a record in this collection
    public FuncResult<ListStruct> executeQueryFreestyle(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.executeQueryFreestyle(this.conn, sql, params);
    }

    // return count of records updated
    public FuncResult<Long> executeUpdateFreestyle(String sql, Object... params) throws OperatingContextException {
        //System.out.println("params: " + params);

        return SqlUtil.executeUpdateFreestyle(this.conn, sql, params);
    }

    // return count of records updated
    public FuncResult<Long> executeWrite(SqlWriter writer) throws OperatingContextException {
        return SqlUtil.executeWrite(this.conn, writer);
    }

    // return id of record added
    public FuncResult<Long> executeInsertReturnId(SqlWriter writer) throws OperatingContextException {
        return SqlUtil.executeInsertReturnId(this.conn, writer);
    }

    // caller needs to close statememt
    // will not return open statement and errors
    public FuncResult<PreparedStatement> prepStatement(String sql, Object... params) throws OperatingContextException {
        return SqlUtil.prepStatement(this.conn, sql, params);
    }
}
