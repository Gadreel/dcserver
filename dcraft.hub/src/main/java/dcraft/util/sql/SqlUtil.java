package dcraft.util.sql;

import dcraft.hub.op.FuncResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.temporal.TemporalAccessor;

public class SqlUtil {

    // return a list of records where each row is a record in this collection
    // -- NOTE: column names are all lower case
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
                if (psres.isEmptyResult())
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

                        for(int i=1; i<=columns; i++)
                            rec.with(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));

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

                // null params are intentionally not supported - allows us to optionally add params to a complex query
                // for NULL support see SqlNull enum
                if (param == null)
                    continue;

                if (param instanceof TemporalAccessor)
                    param = TimeUtil.sqlStampFmt.format((TemporalAccessor) param);

                if (param instanceof String) {
                    pstmt.setString(i + 1, (String)param);
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
            // we treat it as an error return code but log it as an Info
            if (sx.getErrorCode() == 1062) {
                // TODO return a code we can check for dup
                Logger.warn("SQL Duplicate Id: " + sx.getErrorCode() + " message: " + x);
                return;
            }

            Logger.error("Error executing SQL. Code: " + sx.getErrorCode() + " message: " + x);
        }
        else {
            Logger.error("Error executing SQL: " + x);
        }
    }
}
