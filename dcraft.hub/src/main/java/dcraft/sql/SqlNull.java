package dcraft.sql;

public enum SqlNull {
    DateTime,
    VarChar,
    Long,
    Int,
    Double,
    BigDecimal,
    Text;

    static public Object orValue(Object value, SqlNull alt) {
        if (value != null)
            return value;

        return alt;
    }
}
