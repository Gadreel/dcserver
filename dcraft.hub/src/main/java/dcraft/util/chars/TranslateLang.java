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
package dcraft.util.chars;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Scanner;

import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;
import dcraft.xml.XmlReader;

public class TranslateLang {
	
	static public String translateText(String source, String from, String to) {
		Logger.debug("Translate " + from + " to " + to + ": " + source);
		
		if ("x-pig-latin".equals(to) && "eng".equals(from))
			return PigLatin.translate(source);
		
		return source;
	}

	static private void translateXml(XElement el, String from, String to) {
		for (XNode n : el.getChildren()) {
			if (n instanceof XText) {
				XText t = (XText)n;
				
				t.setValue(TranslateLang.translateText(t.getValue(), from, to));
			}
			else if (n instanceof XElement)
				TranslateLang.translateXml((XElement)n, from, to);
		}
	}

	static public String translateXml(String original, String from, String to) {
		XElement root = XmlReader.parse(original, true, true);
		
		if (root == null)
			return TranslateLang.translateText(original, from, to);
		
		TranslateLang.translateXml(root, from, to);
		
		return root.toString(true);
	}
	
	
	/**
	 * Loop the packages and prompt for translation, if yes then a x-pig-latin and x-rtl-en
	 * variation of the en tokens is generated  
	 * 
	 * @param scan user input scanner
	 */
	public static void translate(Scanner scan) {
		System.out.println();
		System.out.println("Loop packages, type 'yes' for packages you wish to translate. ");
		System.out.println("When 'yes' note that the existing pig-latin an rtl-en files will");
		System.out.println("be overwritten. (skips zipped packages, for dev only)");
		System.out.println();
				
		File pkgs = new File("./packages");
		
		for (File pkg : pkgs.listFiles()) {
			if (!pkg.isDirectory()) 
				continue;
				
			String name = pkg.getName();
			
			System.out.println("Found: " + name);
			System.out.print("[yes/no]: ");
			
			if (!"yes".equals(scan.nextLine().toLowerCase()))
				continue;
			
			for (File safety : pkg.listFiles()) {
				if (!safety.isDirectory()) 
					continue;
				
				File dictdir = new File(safety, "dictionary");
				
				if (!dictdir.exists() || !dictdir.isDirectory()) 
					continue;
				
				File en = new File(dictdir, "en.xml");
				
				if (!en.exists())
					en = new File(dictdir, "dictionary.xml");
				
				if (!en.exists())
					continue;
				
				Path enpath = en.toPath();
				
				TranslateLang.translate(enpath);
			}
		}
	}
	
	public static void translate(Path source) {
		TranslateLang.translate(source, source.resolveSibling("x-pig-latin.xml"), source.resolveSibling("x-rtl-en.xml"));
	}
	
	public static void translate(Path source, Path pigdest, Path rtldest) {
		File en = source.toFile();
		
		if (!en.exists())
			return;
		
		// pig latin translation
		
		File pltr = pigdest.toFile();
		
		XElement trroot = XmlReader.loadFile(en, false, true);
		
		if (trroot == null) {
			Logger.error("Error reading file: " + en.getPath());
			return;
		}
		
		try {
			FileWriter fw = new FileWriter(pltr);
			fw.append("<Dictionary>\n");
			fw.append("<Locale Id=\"x-pig-latin\">\n");

			for (XElement lel : trroot.selectAll("Locale")) {
				String lname = lel.getAttribute("Id");
				
				if (! "eng".equals(lname))
					continue;
				
				for (XElement tel : lel.selectAll("Entry")) {
					String tname = tel.getAttribute("Token");
					
					if (StringUtil.isEmpty(tname) || tname.startsWith("_cldr_"))
						continue;
					
					String v = tel.getAttribute("Value");
					
					if (StringUtil.isEmpty(v))
						v = tel.getText();
					
					if (StringUtil.isEmpty(v))
						continue;
					
					fw.append("\t<Entry Token=\"" + tname + "\" Value=\"" + PigLatin.translate(v) + "\" />\n");
				}
			}
			
			fw.append("</Locale>\n");
			fw.append("</Dictionary>\n");
			
			fw.flush();
			fw.close();
		}
		catch (Exception x) {
			System.out.println("Problem translating file to x-pig-latin: " + en.getAbsolutePath());
			System.out.println("Error: " + x);
		}
		
		// rtl-en translation
		pltr = rtldest.toFile();
		
		try {
			FileWriter fw = new FileWriter(pltr);
			fw.append("<Dictionary>\n");
			fw.append("<Locale Id=\"x-rtl-en\">\n");

			for (XElement lel : trroot.selectAll("Locale")) {
				String lname = lel.getAttribute("Id");
				
				if (! "eng".equals(lname))
					continue;
				
				for (XElement tel : lel.selectAll("Entry")) {
					String tname = tel.getAttribute("Token");
					
					if (StringUtil.isEmpty(tname))
						continue;
					
					String v = tel.getAttribute("Value");
					
					if (StringUtil.isEmpty(v))
						v = tel.getText();
					
					if (StringUtil.isEmpty(v))
						continue;
					
					fw.append("\t<Entry Token=\"" + tname + "\" Value=\"" + RtlEnglish.translate(v) + "\" />\n");
				}
			}
			
			fw.append("</Locale>\n");
			fw.append("</Dictionary>\n");
			
			fw.flush();
			fw.close();
		}
		catch (Exception x) {
			System.out.println("Problem translating file to x-rtl-en: " + en.getAbsolutePath());
			System.out.println("Error: " + x);
		}
	}
}
