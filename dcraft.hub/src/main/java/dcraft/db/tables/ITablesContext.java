package dcraft.db.tables;

import dcraft.db.DatabaseAdapter;
import dcraft.hub.op.OperatingContextException;

import java.math.BigDecimal;

public interface ITablesContext {
	DatabaseAdapter getInterface();
	String getTenant() throws OperatingContextException;
	BigDecimal getStamp();
}
