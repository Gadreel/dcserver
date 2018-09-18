package dcraft.xml;

public class XRawText extends XText {
	static public XText of(String value) {
		XRawText text = new XRawText();
		text.setValue(value);
		return  text;
	}
	
	@Override
	public void append(char c) {
		if (this.content == null)
			this.content = "" + c;
		else
			this.content += c;
	}
	
	@Override
	public void append(String s) {
		if (this.content == null)
			this.content = s;
		else
			this.content += s;
	}
	
	@Override
	public void setValue(CharSequence value) {
		super.setRawValue(value);
	}
	
	@Override
	public void setValue(CharSequence value, boolean cdata) {
		super.setRawValue(value, cdata);
	}
	
	@Override
	public XText withValue(CharSequence value) {
		return super.withRawValue(value);
	}
}
