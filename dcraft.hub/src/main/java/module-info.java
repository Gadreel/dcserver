module dcraft.hub {
  requires java.net.http;
  requires java.xml;
  requires java.sql;
  requires java.sql.rowset;
  requires java.desktop;
  requires java.compiler;
  requires java.management;
  requires dcraft.third;
  requires io.netty.all;
  requires org.bouncycastle.pg;
  requires org.bouncycastle.provider;
  requires org.bouncycastle.pkix;
  requires org.threeten.extra;
  requires org.apache.commons.codec;
  requires org.apache.commons.compress;
  requires org.apache.pdfbox;
  requires jsch;
  requires rocksdbjni;
  requires commons.logging;
  requires org.shredzone.acme4j;
  requires org.shredzone.acme4j.utils;
  requires java.ipv6;
  requires org.slf4j;
  requires activation;
  requires java.mail;
  requires jgit;
  requires jxl;
  requires org.jsoup;
  requires quercus;
  requires org.mariadb.jdbc;

  exports z.tws.php to quercus;
  exports z.tws.web to quercus;
}
