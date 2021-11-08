package dcraft.util.sql;

import java.util.ArrayList;
import java.util.List;

public class SqlWriter {
    static public SqlWriter insert(String table) {
        SqlWriter writer = new SqlWriter();
        writer.table = table;
        writer.op = SqlUpdateType.Insert;
        return writer;
    }

    static public SqlWriter update(String table, Object id) {
        SqlWriter writer = new SqlWriter();
        writer.table = table;
        writer.id = id;
        writer.op = SqlUpdateType.Update;
        return writer;
    }

    static public SqlWriter delete(String table, Object id) {
        SqlWriter writer = new SqlWriter();
        writer.table = table;
        writer.id = id;
        writer.op = SqlUpdateType.Delete;
        return writer;
    }

    protected String table = null;
    //protected String idfield = "Id";
    protected Object id = null;
    protected SqlUpdateType op = SqlUpdateType.Update;
    protected List<SqlField> fields = new ArrayList<>();

    /*
    public SqlWriter withIdField(String name) {
        this.idfield = name;

        return this;
    }

     */

    public SqlWriter with(String name, Object value) {
        SqlField fld = new SqlField();
        fld.name = name;
        fld.value = value;

        this.fields.add(fld);

        return this;
    }

    public SqlWriter withConditional(String name, Object value) {
        if (value != null) {
            SqlField fld = new SqlField();
            fld.name = name;
            fld.value = value;

            this.fields.add(fld);
        }

        return this;
    }

    // currently only supports integer ids
    public String toSql() {
        StringBuilder sb = new StringBuilder();

        if (this.op == SqlUpdateType.Update) {
            sb.append("UPDATE `" + this.table + "` SET ");

            for (int i = 0; i < this.fields.size(); i++) {
                if (i > 0)
                    sb.append(", ");

                sb.append("`" + this.fields.get(i).name + "` = ?");
            }

            sb.append(" WHERE ID = " + this.id);
        }
        else if (this.op == SqlUpdateType.Insert) {
            sb.append("INSERT INTO `" + this.table + "` (");

            for (int i = 0; i < this.fields.size(); i++) {
                if (i > 0)
                    sb.append(", ");

                sb.append("`" + this.fields.get(i).name + "`");
            }

            sb.append(") VALUES (");

            for (int i = 0; i < this.fields.size(); i++) {
                if (i > 0)
                    sb.append(", ");

                sb.append("?");
            }

            sb.append(")");
        }
        else if (this.op == SqlUpdateType.Delete) {
            sb.append("DELETE FROM `" + this.table + "` WHERE ID = " + this.id);
        }

        return sb.toString();
    }

    public Object[] toParams() {
        Object[] params = new Object[this.fields.size()];

        for (int i = 0; i < this.fields.size(); i++) {
            params[i] = fields.get(i).value;
        }

        return params;
    }

    static public class SqlField {
        public String name = null;
        public Object value = null;
    }

    public enum SqlUpdateType {
        Insert,
        Update,
        Delete
    }
}
