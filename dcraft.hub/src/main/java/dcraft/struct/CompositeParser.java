/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.struct;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import dcraft.log.Logger;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.JsonStreamBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.io.InputWrapper;
import dcraft.util.io.UnicodeReader;
import dcraft.util.json3.JsonParser;
import dcraft.util.json3.JsonToken;
import dcraft.xml.IParseHandler;
import dcraft.xml.XNode;
import dcraft.xml.XmlParser;

public class CompositeParser {
	static public CompositeStruct parseJson(CharSequence data) {
		if (data == null) {
			Logger.error( "Error parsing JSON, no characters");
			return null;
		}
		
		try (JsonParser jp = JsonParser.createParser(data)) {
			return CompositeParser.parseJson(jp);
		}
		catch (Exception x) {
			Logger.error( "Error parsing JSON: " + x);
			
			return null;
		}
	}
	
	static public CompositeStruct parseJson(Memory data) {
		return CompositeParser.parseJson(new InputWrapper(data));
	}
	
	static public CompositeStruct parseJson(InputStream data) {
		try (JsonParser jp = JsonParser.createParser(data)) {
			return CompositeParser.parseJson(jp);
		}
		catch (Exception x) {
			Logger.error( "Error parsing JSON: " + x);
			
			return null;
		}
		finally {
			IOUtil.closeQuietly(data);
		}
	}
	
	static public CompositeStruct parseJson(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			try (JsonParser jp = JsonParser.createParser(in)) {
				return CompositeParser.parseJson(jp);
			}
			catch (Exception x) {
				Logger.error("Error parsing JSON file " + path + ", error: " + x);
				
				return null;
			}
		}
		catch (IOException x) {
			Logger.error("Error reading file " + path + ", error: " + x);
			
			return null;
		}
	}
	
	static public CompositeStruct parseJsonUrl(String url) {
		try {
			return CompositeParser.parseJson(new URL(url));
		} 
		catch (MalformedURLException x) {
			Logger.error("Error opening url " + url + ", error: " + x);
			return null;
		}
	}
	
	static public CompositeStruct parseJson(URL url) {
		return CompositeParser.parseJson(url, null);
	}
	
	static public CompositeStruct parseJson(URL url, RecordStruct hdrs) {
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			 
			con.setUseCaches(false);
			con.setDefaultUseCaches(false);

			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
    	    con.setRequestProperty("Accept", "application/json");

			if (hdrs != null)
				for (FieldStruct fld : hdrs.getFields()) 
					con.setRequestProperty(fld.getName(), fld.getValue().toString());
	 
			if (con.getResponseCode() != 200) {
				Logger.error("Error reading url " + url + ", error code: " + con.getResponseCode());
				return null;
			}
			
			try (InputStream in = con.getInputStream()) {
				try (JsonParser jp = JsonParser.createParser(in)) {
					return CompositeParser.parseJson(jp);
				}
				catch (Exception x) {
					Logger.error("Error parsing JSON url " + url + ", error: " + x);
					
					return null;
				}
			}
			catch (IOException x) {
				Logger.error("Error reading url " + url + ", error: " + x);
				
				return null;
			}
		}
		catch (IOException x) {
			Logger.error("Error opening url " + url + ", error: " + x);
			
			return null;
		}
	}
	
	static public CompositeStruct transactJson(CompositeStruct send, URL url) {
		return CompositeParser.transactJson(send, url, null);
	}
	
	static public CompositeStruct transactJson(CompositeStruct send, URL url, RecordStruct hdrs) {
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			 
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			con.setRequestProperty("Content-Type", "application/json");
    	    con.setRequestProperty("Accept", "application/json");

			if (hdrs != null)
				for (FieldStruct fld : hdrs.getFields()) 
					con.setRequestProperty(fld.getName(), fld.getValue().toString());
			
			con.setDoOutput(true);
			
			PrintStream pwr = new PrintStream(con.getOutputStream());
			
			JsonStreamBuilder sb = new JsonStreamBuilder(pwr);
			send.toBuilder(sb);
			
			pwr.flush();
			pwr.close();
			
			if (con.getResponseCode() != 200) {
				Logger.error("Error reading url " + url + ", error code: " + con.getResponseCode());
				return null;
			}
			
			try (InputStream in = con.getInputStream()) {
				try (JsonParser jp = JsonParser.createParser(in)) {
					return CompositeParser.parseJson(jp);
				}
				catch (Exception x) {
					Logger.error("Error parsing JSON url " + url + ", error: " + x);
					
					return null;
				}
			}
			catch (IOException x) {
				Logger.error("Error reading url " + url + ", error: " + x);
				
				return null;
			}
		}
		catch (BuilderStateException x) {
			Logger.error("Error writing url " + url + ", error: " + x);
			
			return null;
		}
		catch (IOException x) {
			Logger.error("Error opening url " + url + ", error: " + x);
			
			return null;
		}
	}
	
	static public CompositeStruct parseJson(JsonParser jp) throws IOException, BuilderStateException {
		ObjectBuilder eb = new ObjectBuilder();
		
		JsonToken token = jp.nextToken();
		
		while (token != null) {
			if (token == JsonToken.START_ARRAY) 
				eb.startList();
			else if (token == JsonToken.END_ARRAY) 
				eb.endList();
			else if (token == JsonToken.START_OBJECT) 
				eb.startRecord();
			else if (token == JsonToken.END_OBJECT) 
				eb.endRecord();
			else if (token == JsonToken.FIELD_NAME) 
				eb.field(jp.getText());		// TODO check for bad chars (controls chars)
			else if (token == JsonToken.VALUE_NULL) 
				eb.value(null);
			else if (token == JsonToken.VALUE_FALSE) 
				eb.value(false);
			else if (token == JsonToken.VALUE_TRUE) 
				eb.value(true);
			else if (token == JsonToken.VALUE_STRING) 
				eb.value(jp.getText());		// TODO check for bad chars (controls chars)
			else if (token == JsonToken.VALUE_NUMBER_FLOAT) 
				eb.value(jp.getDecimalValue());
			else if (token == JsonToken.VALUE_NUMBER_INT) 
				eb.value(jp.getNumberValue());   //BigIntegerValue());
			
			token = jp.nextToken();
		}
		
		return eb.getRoot();
	}
	
	/*
	static public CompositeStruct parseYaml(CharSequence data) {
		CompositeStruct ret = null;
		
		try {
			ret = CompositeParser.parseYaml(new StringReader(data.toString()));
		}
		catch (Exception x) {
			// TODO logging
			//System.out.println("err: " + x);
		}
		
		return ret;
	}
	
	static public CompositeStruct parseYaml(Memory data) {
		return CompositeParser.parseYaml(new InputWrapper(data));
	}
	
	static public CompositeStruct parseYaml(InputStream data) {
		CompositeStruct ret = null;
		
		try {
			ret = CompositeParser.parseYaml(new UnicodeReader(data));
		}
		catch (Exception x) {
			// TODO logging
			//System.out.println("err: " + x);
		}
		
		return ret;
	}
	
	static public CompositeStruct parseYaml(Reader rdr) throws IOException, BuilderStateException {
		ObjectBuilder eb = new ObjectBuilder();
		
		Yaml y = new Yaml();
		boolean docaslist = false;
		
		for (Event event : y.parse(rdr)) {
			//System.out.println("Event: " + event);
			
			if (event instanceof DocumentStartEvent) {
				if (((DocumentStartEvent)event).getExplicit()) {
					if (eb.getState() == BuilderState.Ready) {
						docaslist = true;
						eb.startList();
					}
				}
			}
			else if (event instanceof DocumentEndEvent) {
			}
			else if (event instanceof SequenceStartEvent) 
				eb.startList();
			else if (event instanceof SequenceEndEvent) 
				eb.endList();
			else if (event instanceof MappingStartEvent) 
				eb.startRecord();
			else if (event instanceof MappingEndEvent) 
				eb.endRecord();
			else if (event instanceof ScalarEvent) {
				ScalarEvent sev  = (ScalarEvent)event;
				
				if (eb.getState() == BuilderState.InRecord)
					eb.field(sev.getValue());
				else {
					//System.out.println(sev.getValue() + " - " + sev.getTag());
					String tag = sev.getTag();
					String val = sev.getValue();
					
					if ("tag:yaml.org,2002:float".equals(tag))
						eb.value(new BigDecimal(val));
					else if ("tag:yaml.org,2002:int".equals(tag))
						eb.value(new Long(val));
					else if ("tag:yaml.org,2002:bool".equals(tag))
						eb.value(new Boolean(val));
					else if ("tag:yaml.org,2002:null".equals(tag))
						eb.value(null);
					else if ("tag:yaml.org,2002:str".equals(tag))
						eb.value(val);
					else if ("tag:yaml.org,2002:timestamp".equals(tag))
						eb.value(TimeUtil.parseDateTime(val));
					else {			// TODO add support for dcSchema types
						eb.value(val);
					}
				}
			}
		}
		
		if (docaslist)
			eb.endList();
		
		return eb.getRoot();
	}
	*/
	
	static public CompositeStruct parseXml(CharSequence data) {
		CompositeStruct ret = null;
		
		try {
			ret = CompositeParser.parseXml(new StringReader(data.toString()));
		}
		catch (Exception x) {
			// TODO logging
			//System.out.println("err: " + x);
		}
		
		return ret;
	}
	
	static public CompositeStruct parseXml(Memory data) {
		return CompositeParser.parseXml(new InputWrapper(data));
	}
	
	static public CompositeStruct parseXml(InputStream data) {
		CompositeStruct ret = null;
		
		try {
			ret = CompositeParser.parseXml(new UnicodeReader(data));
		}
		catch (Exception x) {
			// TODO logging
			//System.out.println("err: " + x);
		}
		
		return ret;
	}
	
	static public CompositeStruct parseXml(Reader rdr) throws IOException, BuilderStateException {
		XmlReader xrdr = new XmlReader(rdr);
		
		try {
			return xrdr.parse();
		}
		catch (Exception x) {
			// TODO logging
			//System.out.println("err: " + x);
		}
		
		return null;
	}
	
	/*
	 * XML
	 * 
	 * <Record Type="nnn">
	 * 		<Field Name="Age" Type="nnn">
	 * 			<Scalar>9</Scalar>
	 * 		</Field>
	 * 		<Field Name="NickName" Value="Freckles" Type="nnn" />
	 * 		<Field Name="Friends">
	 * 			<List Type="nnn">
	 * 				<Scalar>Stacy</Scalar>
	 * 				<Scalar>Lenny</Scalar>
	 * 			</List>
	 * 		</Field>
	 * </Record>
	 * 
	 */
	
	
	static public class XmlReader implements IParseHandler {
		/**
		 * The source of the XML
		 */
		protected Reader input = null;
		
		protected boolean inScalar = false;
		protected String stype = null;
		protected ObjectBuilder builder = new ObjectBuilder();
		protected StringBuilder sb = null;
		
		/**
		 * Set XML source to be a Reader 
		 * 
		 * @param input the XML source to be parsed
		 */
		public XmlReader(Reader input) {
			this.input = input;
		}

		/* TODO wrong comments
		 * Parses the XML and returns the root element.  Comments and PI
		 * will be missing, this is a really basic and lightweight XML utility.
		 * 
		 * @return the root XML element 
		 * @throws XMLParseException
		 *             if an error occurs when parsing the XML 
		 * @throws IOException
		 *             if an error occurs when reading from the input source
		 */
		public CompositeStruct parse() throws Exception {
			XmlParser.parse(this, this.input);
			
			return this.builder.getRoot();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#startDocument()
		 */
		@Override
		public boolean startDocument() {
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#endDocument()
		 */
		@Override
		public boolean endDocument() {
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#element(java.lang.String, java.lang.String, java.util.Map, int, int)
		 */
		@Override
		public boolean element(String tag, Map<String, String> attributes, int line, int col) {
			try {
				if ("Record".equals(tag)) {
					this.builder.startRecord();
					this.builder.endRecord();
				}
				else if ("List".equals(tag)) {
					this.builder.startList();
					this.builder.endList();
				}
				else if ("Field".equals(tag)) {
					this.builder.field(attributes.get("Name"));
					this.checkAttr(attributes);
				}
				else if ("Scalar".equals(tag)) {
					this.checkAttr(attributes);
				}
				else if (this.inScalar) {
					this.sb.append("<" + tag);
	
					// Write the attributes out
					for (Map.Entry<String, String> entry : attributes.entrySet()) {
						this.sb.append(" " + entry.getKey() + "=");
						this.sb.append("\"" + XNode.quote(entry.getValue()) + "\"");
					}
	
					this.sb.append(" />");
				}
				
				return true;
			}
			catch (BuilderStateException x) {
				Logger.errorTr(246, x);
				return false;
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#startElement(java.lang.String, java.lang.String, java.util.Map, int, int)
		 */
		@Override
		public boolean startElement(String tag, Map<String, String> attributes, int line, int col) {
			try {
				if ("Record".equals(tag))
					this.builder.startRecord();
				else if ("List".equals(tag))
					this.builder.startList();
				else if ("Field".equals(tag)) {
					this.builder.field(attributes.get("Name"));
				}
				else if ("Scalar".equals(tag)) {
					this.inScalar = true;
					this.sb = new StringBuilder();
					this.stype = attributes.get("Type");
				}
				else if (this.inScalar) {
					this.sb.append("<" + tag);
	
					// Write the attributes out
					for (Map.Entry<String, String> entry : attributes.entrySet()) {
						this.sb.append(" " + entry.getKey() + "=");
						this.sb.append("\"" + XNode.quote(entry.getValue()) + "\"");
					}
					
					this.sb.append(">");
				}
				return true;
			}
			catch (BuilderStateException x) {
				Logger.errorTr(246, x);
				return false;
			}
		}
		
		private void checkAttr(Map<String, String> attributes) throws BuilderStateException {
			this.stype = attributes.get("Type");
			
			if (attributes.containsKey("Value")) 
				this.scalarFinal(attributes.get("Value"));
		}
		
		private void scalarFinal(String value) throws BuilderStateException {
			try {
				if (value == null)
					this.builder.value(null);
				else {
					if (StringUtil.isEmpty(value))
						this.builder.value(null);
					else if ("Decimal".equals(this.stype))
						this.builder.value(new BigDecimal(value));
					else if ("Integer".equals(this.stype))
						this.builder.value(new Long(value));
					else if ("Boolean".equals(this.stype))
						this.builder.value(new Boolean(value));
					else if ("Null".equals(this.stype))
						this.builder.value(null);
					else if ("String".equals(this.stype))
						this.builder.value(value);
					else if ("DateTime".equals(this.stype))
						this.builder.value(TimeUtil.parseDateTime(value));		// TODO support more types
					else
						this.builder.value(value);
				}
			}
			finally {
				this.inScalar = false;
				this.stype = null;
				this.sb = null;
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#endElement(java.lang.String, java.lang.String)
		 */
		@Override
		public boolean endElement(String tag) {
			try {
				if ("Record".equals(tag))
					this.builder.endRecord();
				else if ("List".equals(tag))
					this.builder.endList();
				else if ("Scalar".equals(tag) && this.inScalar)	{	// treat as null
					this.scalarFinal((this.sb == null) ? null : this.sb.toString());				
				}
				else if (this.inScalar) {
					// Now put the closing tag out
					sb.append("</" + tag+ ">");
				}
				return true;
			}
			catch (BuilderStateException x) {
				Logger.errorTr(246, x);
				return false;
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see IParseHandler#text(java.lang.String, boolean, int, int)
		 */
		@Override
		public boolean text(String str, boolean cdata, int line, int col) {
			if (! this.inScalar)
				return true;
			
			if (cdata) 
				this.sb.append("<![CDATA[" + str + "]]>");
			else 
				this.sb.append(XNode.quote(str));
			
			return true;
		}
		
		@Override
		public boolean comment(String str, int line, int col) {
			/* TODO review
			if (! this.inScalar)
				return true;
			
			this.sb.append();
			*/
			
			return true;
		}
	}
	
}
