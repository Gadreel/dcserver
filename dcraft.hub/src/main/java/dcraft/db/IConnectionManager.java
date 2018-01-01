package dcraft.db;

import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 */
public interface IConnectionManager {
	void load(XElement config);
	void stop();
	void backup();
	long lastBackup();
	Path getDatabasePath();
	Path getBackupPath();
	DatabaseAdapter allocateAdapter();
	BigDecimal allocateStamp(int offset);
}
