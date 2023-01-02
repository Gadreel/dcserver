package dcraft.sql;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.FuncResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
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
    protected boolean errored = false;

    // try not to use connection directly so that we can detect sql errors and drop bad connections
    public Connection getConn() {
        return this.conn;
    }

    public String getName() {
        return this.name;
    }

    public boolean isErrored() {
        return this.errored;
    }

    public void setErrored(boolean v) {
        this.errored = v;
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
        try (OperationMarker om = OperationMarker.create()) {
            BaseStruct var = SqlUtil.getVar(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return var;
        }
    }

    public String getVarString(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            String str = SqlUtil.getVarString(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return str;
        }
    }

    public Long getVarInteger(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            Long num = SqlUtil.getVarInteger(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return num;
        }
    }

    public Boolean getVarBoolean(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            Boolean bool = SqlUtil.getVarBoolean(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return bool;
        }
    }

    public RecordStruct getRow(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            RecordStruct row = SqlUtil.getRow(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return row;
        }
    }

    public ListStruct getResults(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            ListStruct rows = SqlUtil.getResults(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return rows;
        }
    }

    // return a list of records where each row is a record in this collection
    public FuncResult<ListStruct> executeQueryFreestyle(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            FuncResult<ListStruct> result = SqlUtil.executeQueryFreestyle(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }

    // return count of records updated
    public FuncResult<Long> executeUpdateFreestyle(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            //System.out.println("params: " + params);

            FuncResult<Long> result = SqlUtil.executeUpdateFreestyle(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }

    // return count of records updated
    public FuncResult<Long> executeDelete(SqlWriter writer) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            FuncResult<Long> result = SqlUtil.executeDelete(this.conn, writer);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }

    // return count of records updated
    public FuncResult<Long> executeWrite(SqlWriter writer) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            FuncResult<Long> result = SqlUtil.executeWrite(this.conn, writer);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }

    // return id of record added
    public FuncResult<Long> executeInsertReturnId(SqlWriter writer) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            FuncResult<Long> result = SqlUtil.executeInsertReturnId(this.conn, writer);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }

    // caller needs to close statememt
    // will not return open statement and errors
    public FuncResult<PreparedStatement> prepStatement(String sql, Object... params) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            FuncResult<PreparedStatement> result = SqlUtil.prepStatement(this.conn, sql, params);

            if (om.hasCode(195))
                this.errored = true;

            return result;
        }
    }
}
