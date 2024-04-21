module dcraft.third {
  requires java.xml;
  requires java.desktop;
  requires org.apache.commons.codec;
  requires org.apache.commons.compress;

  exports org.apache.lucene.util;
  exports org.apache.lucene.analysis;
  exports org.apache.lucene.analysis.standard;
  exports org.apache.lucene.analysis.snowball;
  exports org.apache.lucene.analysis.tokenattributes;
  exports org.apache.lucene.analysis.miscellaneous;
  exports org.apache.lucene.analysis.en;
  exports org.apache.lucene.analysis.es;
  exports org.mindrot;
  exports com.pnuema.java.barcode;
  exports dcraft.daemon;
  exports com.samstevens.totp.util;
  exports com.samstevens.totp.exceptions;
}
