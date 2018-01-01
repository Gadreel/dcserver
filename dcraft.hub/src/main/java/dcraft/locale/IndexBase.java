package dcraft.locale;

import java.util.List;

abstract public class IndexBase {
	abstract public List<IndexInfo> full(String value);
	abstract public List<IndexInfo> simple(String value);
}
