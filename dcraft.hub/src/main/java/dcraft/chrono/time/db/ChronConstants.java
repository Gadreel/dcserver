package dcraft.chrono.time.db;

public class ChronConstants {
	final static public long CHRON_CONFIDENCE_FACT = 10;
	final static public long CHRON_CONFIDENCE_STRONG = 20;
	final static public long CHRON_CONFIDENCE_MEDIUM = 30;
	final static public long CHRON_CONFIDENCE_SPECULATIVE = 50;
	final static public long CHRON_CONFIDENCE_FICTION = 70;
	final static public long CHRON_CONFIDENCE_NONE = 100;
	
	final static public long CHRON_ENTITY_STATE_IGNORE = 0;
	final static public long CHRON_ENTITY_STATE_FOUNDATION = 1;   // entity is enabled by this dataset because foundational properties are present
	final static public long CHRON_ENTITY_STATE_PRESENT = 2;   // entity is present but not enabled by this dataset
	
	final static public long CHRON_VER_OP_REVOKE = 0;
	final static public long CHRON_VER_OP_ADMIT = 1;
	
	final static public String CHRON_GLOBAL_ENTITIES = "dccEntities";
	final static public String CHRON_GLOBAL_PROPS = "dccProps";
	final static public String CHRON_GLOBAL_LINKS = "dccLinks";
	final static public String CHRON_GLOBAL_LINK_INDEX = "dccLinkIndex";
	
}
