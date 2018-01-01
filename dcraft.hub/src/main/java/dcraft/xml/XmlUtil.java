package dcraft.xml;

import dcraft.log.Logger;
import dcraft.xml.XElement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XmlUtil {
	static public boolean delete(XElement doc, String id) {
		XElement parent = doc.findParentOfId(id);
		
		if (parent == null) {
			Logger.warn("Could not find element: " + id);
			return false;
		}
		
		parent.remove(parent.findId(id));
		return true;
	}
	
	static public boolean insertAbove(XElement doc, String id, XElement element) {
		XElement parent = doc.findParentOfId(id);
		
		if (parent == null) {
			Logger.error("Could not find element: " + id);
			return false;
		}
		
		XElement el = parent.findId(id);
		
		int pos = parent.findIndex(el);
		parent.add(pos, element);
		
		return true;
	}
	
	static public boolean insertBelow(XElement doc, String id, XElement element) {
		XElement parent = doc.findParentOfId(id);
		
		if (parent == null) {
			Logger.error("Could not find element: " + id);
			return false;
		}
		
		XElement el = parent.findId(id);
		
		int pos = parent.findIndex(el);
		parent.add(pos + 1, element);
		
		return true;
	}
	
	static public boolean moveUp(XElement doc, String id) {
		XElement parent = doc.findParentOfId(id);
		
		if (parent == null) {
			Logger.error("Could not find element: " + id);
			return false;
		}
		
		XElement el = parent.findId(id);
		
		int pos = parent.findIndex(el);
		
		parent.remove(el);
		
		parent.add(pos - 1, el);
		
		return true;
	}
	
	static public boolean moveDown(XElement doc, String id) {
		XElement parent = doc.findParentOfId(id);
		
		if (parent == null) {
			Logger.error("Could not find element: " + id);
			return false;
		}
		
		XElement el = parent.findId(id);
		
		int pos = parent.findIndex(el);
		
		parent.remove(el);
		
		parent.add(pos + 1, el);
		
		return true;
	}
	
	static public List<XNode> deepCopyChildren(List<XNode> children) {
		if (children != null) {
			List<XNode> copychildren = new CopyOnWriteArrayList<>();
			
			for (XNode entry : children)
				copychildren.add(entry.deepCopy());
			
			return copychildren;
		}
		
		return null;
	}
}
