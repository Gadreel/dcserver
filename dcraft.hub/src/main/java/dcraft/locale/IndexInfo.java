package dcraft.locale;

public class IndexInfo {
	static public IndexInfo of(String token, int start, int end, int score) {
		IndexInfo info = new IndexInfo();

		info.token = token;
		info.start = start;
		info.end = end;
		info.score = score;

		return info;
	}

	public String token = null;
	public int start = -1;
	public int end = -1;
	public int score = 0;
}
