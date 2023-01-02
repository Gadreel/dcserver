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

public class SqlUtil {
    static public SqlConnection getConnection(String dbname) throws SQLException {
        return ResourceHub.getTopResources().getSqlDatabases().getSqlConnection(dbname);
    }

    static public BaseStruct getVar(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<ListStruct> result = SqlUtil.executeQueryFreestyle(conn, sql, params);

        if (result.isNotEmptyResult()) {
            RecordStruct rec = result.getResult().getItemAsRecord(0);

            if (! rec.isEmpty()) {
                for (FieldStruct fld : rec.getFields()) {
                    return fld.getValue();
                }
            }
        }

        return null;
    }

    static public String getVarString(Connection conn, String sql, Object... params) throws OperatingContextException {
        return Struct.objectToString(getVar(conn, sql, params));
    }

    static public Long getVarInteger(Connection conn, String sql, Object... params) throws OperatingContextException {
        return Struct.objectToInteger(getVar(conn, sql, params));
    }

    static public Boolean getVarBoolean(Connection conn, String sql, Object... params) throws OperatingContextException {
        return Struct.objectToBoolean(getVar(conn, sql, params));
    }

    static public RecordStruct getRow(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<ListStruct> result = SqlUtil.executeQueryFreestyle(conn, sql, params);

        if (result.isNotEmptyResult())
            return result.getResult().getItemAsRecord(0);

        return null;
    }

    static public ListStruct getResults(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<ListStruct> result = SqlUtil.executeQueryFreestyle(conn, sql, params);

        return result.getResult();
    }

    // return a list of records where each row is a record in this collection
    static public FuncResult<ListStruct> executeQueryFreestyle(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<ListStruct> res = new FuncResult<>();
        ListStruct list = ListStruct.list();

        res.setResult(list);

        // if connection is bad/missing then just try again later
        if (conn == null) {
            Logger.error("Missing sql connection");
            return res;
        }

        try {
            FuncResult<PreparedStatement> psres = SqlUtil.prepStatement(conn, sql, params);

            if (res.hasErrors()) {
                if (psres.isNotEmptyResult())
                    psres.getResult().close();

                return res;
            }

            try (PreparedStatement pstmt = psres.getResult()) {
                // MariaDB hint that this turns on streaming... review TODO
                //pstmt.setFetchSize(Integer.MIN_VALUE);

                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();

                    while (rs.next()) {
                        RecordStruct rec = RecordStruct.record();

                        for(int i=1; i<=columns; i++) {
                            //if ("Tags".equals(md.getColumnLabel(i)))
                            //   System.out.println("columnlabel: " + md.getColumnLabel(i) + " : " + md.getColumnTypeName(i) + " : " + md.getColumnType(i));

                            if ("JSON".equals(md.getColumnTypeName(i)) && (rs.getObject(i) instanceof CharSequence)) {
                                //CompositeParser.parseJson(this.value)
                                //System.out.println("parse it");
                                rec.with(md.getColumnLabel(i), CompositeParser.parseJson((CharSequence) rs.getObject(i)));
                            }
                            else {
                                rec.with(md.getColumnLabel(i), rs.getObject(i));
                            }
                        }

                        list.with(rec);
                    }
                }
            }
        }
        catch (Exception x) {
            SqlUtil.processException(x, res);
        }

        return res;
    }

    // return count of records updated
    static public FuncResult<Long> executeUpdateFreestyle(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<Long> res = new FuncResult<>();

        res.setResult(0L);

        // if connection is bad/missing then just try again later
        if (conn == null) {
            Logger.error("Missing sql connection");
            return res;
        }

        try {
            FuncResult<PreparedStatement> psres = SqlUtil.prepStatement(conn, sql, params);

            if (res.hasErrors()) {
                if (psres.isNotEmptyResult())
                    psres.getResult().close();

                return res;
            }

            try (PreparedStatement pstmt = psres.getResult()) {
                long count = pstmt.executeUpdate();

                res.setResult(count);
            }
        }
        catch (Exception x) {
            SqlUtil.processException(x, res);
        }

        return res;
    }

    // return count of records updated
    static public FuncResult<Long> executeWrite(Connection conn, SqlWriter writer) throws OperatingContextException {
        if (writer.getFieldCount() == 0) {
            FuncResult<Long> res = new FuncResult<>();
            res.setResult(0L);
            return res;
        }

        return executeUpdateFreestyle(conn, writer.toSql(), writer.toParams());
    }

    // return count of records updated
    static public FuncResult<Long> executeDelete(Connection conn, SqlWriter writer) throws OperatingContextException {
        return executeUpdateFreestyle(conn, writer.toSql(), writer.toParams());
    }

    // return id of record added
    static public FuncResult<Long> executeInsertReturnId(Connection conn, SqlWriter writer) throws OperatingContextException {
        FuncResult<Long> res = new FuncResult<>();

        // if connection is bad/missing then just try again later
        if (conn == null) {
            Logger.error("Missing sql connection");
            return res;
        }

        // this is fine, no error
        if (writer.getFieldCount() == 0) {
            return res;
        }

        if (writer.op != SqlWriter.SqlUpdateType.Insert) {
            Logger.error("Incorrect sql command - expected insert");
            return res;
        }

        try {
            FuncResult<PreparedStatement> psres = SqlUtil.prepStatement(conn, writer.toSql(), writer.toParams());

            if (res.hasErrors()) {
                if (psres.isNotEmptyResult())
                    psres.getResult().close();

                return res;
            }

            try (PreparedStatement pstmt = psres.getResult()) {
                int count = pstmt.executeUpdate();

                if (count == 1) {
                    try (PreparedStatement pstmt2 = conn.prepareStatement("SELECT LAST_INSERT_ID() AS lid")){
                        ResultSet rs = pstmt2.executeQuery();

                        if (rs.next())
                            res.setResult(rs.getLong("lid"));
                    }
                }
            }
        }
        catch (Exception x) {
            SqlUtil.processException(x, res);
        }

        return res;
    }

    // caller needs to close statememt
    // will not return open statement and errors
    static public FuncResult<PreparedStatement> prepStatement(Connection conn, String sql, Object... params) throws OperatingContextException {
        FuncResult<PreparedStatement> res = new FuncResult<>();

        // if connection is bad/missing then just try again later
        if (conn == null) {
            Logger.error("Missing SQL Connection");
            return res;
        }

        PreparedStatement pstmt = null;

        try {
            pstmt = conn.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                Object param = params[i];

                if (param instanceof ScalarStruct)
                    param = ((ScalarStruct) param).getGenericValue();

                if (param instanceof CompositeStruct)
                    param = ((CompositeStruct) param).toString();

                // null params are intentionally not supported - allows us to optionally add params to a complex query
                // for NULL support see SqlNull enum
                if (param == null)
                    continue;

                if (param instanceof LocalDate)
                    param = ((LocalDate) param).toString();     // just the yyyy-mm-dd format

                if (param instanceof TemporalAccessor)
                    param = TimeUtil.sqlStampFmt.format((TemporalAccessor) param);  // full date and time

                if (param instanceof CharSequence) {
                    pstmt.setString(i + 1, ((CharSequence)param).toString());
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
                        pstmt.setNull(i + 1, Types.VARCHAR);
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

            res.setResult(pstmt);
        }
        catch (Exception x) {
            SqlUtil.processException(x, res);

            try {
                if (pstmt != null)
                    pstmt.close();
            }
            catch (SQLException x2) {
            }
        }

        return res;
    }

    static public void processException(Exception x, OperationMarker or) {
        if (x instanceof SQLException) {
            SQLException sx = (SQLException)x;

            // duplicate id error - this is not always an error for the caller
            // we treat it as an error return code but log it as an Warn
            if (sx.getErrorCode() == 1062) {
                // TODO return a code we can check for dup
                Logger.warn(194,"SQL Duplicate Id: " + sx.getErrorCode() + " message: " + x);
                return;
            }

            Logger.error(195,"Error executing SQL. Code: " + sx.getErrorCode() + " message: " + x);
        }
        else {
            Logger.error("Error executing SQL: " + x);
        }
    }

    /*
    Use with this:

    PreparedStatement pstmt = con.prepareStatement("SELECT * FROM analysis WHERE notes LIKE ? ESCAPE '!'");
    pstmt.setString(1, notes + "%");
     */
    static public String escLike(String term) {
        return term
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                .replace("[", "![");
    }
}
