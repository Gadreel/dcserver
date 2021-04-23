package dcraft.tenant;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class SiteUtil {
    static public RecordStruct getBaseSiteSchema() throws OperatingContextException {
        RecordStruct schema = RecordStruct.record();

        XElement domainwebconfig = ResourceHub.getResources().getConfig().getTag("Web");

        if (domainwebconfig != null) {
            XElement schemaxml = domainwebconfig.selectFirst("Schema");

            if (schemaxml != null) {
                RecordStruct schemax = Struct.objectToRecord(schemaxml.getText());

                if (schemax != null)
                    schema = schemax;
            }

            if (domainwebconfig.hasNotEmptyAttribute("IndexUrl")) {
                String indexurl = domainwebconfig.getAttribute("IndexUrl");

                if (! schema.hasField("url"))
                    schema.with("url", indexurl);

                if (! schema.hasField("logo"))
                    schema.with("logo", indexurl + "imgs/logo152.png");

                if (! schema.hasField("@id")) {
                    int pos = indexurl.indexOf('/');

                    schema.with("@id", indexurl.substring(0, pos + 2) + "ident." + indexurl.substring(pos + 2));
                }

                if (domainwebconfig.hasNotEmptyAttribute("SiteImage")) {
                    String siteimg = domainwebconfig.getAttribute("SiteImage");

                    schema.with("image", indexurl + (siteimg.startsWith("/") ? siteimg.substring(1) : siteimg));
                }
            }
        }

        if (! schema.hasField("@context"))
            schema.with("@context", "https://schema.org");

        if (! schema.hasField("@type"))
            schema.with("@type", "LocalBusiness");

        if (! schema.hasField("name")) {
            String bname = Struct.objectToString(TaskContext.getOrThrow().queryVariable("OfficialName"));

            if (StringUtil.isEmpty(bname))
                bname = Struct.objectToString(TaskContext.getOrThrow().queryVariable("SiteOwner"));

            if (StringUtil.isNotEmpty(bname))
                schema.with("name", bname);
        }

        if (! schema.hasField("telephone")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("OfficialPhone"));

            if (StringUtil.isNotEmpty(val))
                schema.with("telephone", val);
        }

        // address

        RecordStruct addrec = schema.getFieldAsRecord("address");

        if (addrec == null)
            addrec = RecordStruct.record();

        if (! addrec.hasField("@type"))
            addrec.with("@type", "PostalAddress");

        if (! addrec.hasField("streetAddress")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("PostalStreet"));

            if (StringUtil.isNotEmpty(val))
                addrec.with("streetAddress", val);
        }

        if (! addrec.hasField("addressLocality")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("PostalLocality"));

            if (StringUtil.isNotEmpty(val))
                addrec.with("addressLocality", val);
        }

        if (! addrec.hasField("addressRegion")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("PostalRegion"));

            if (StringUtil.isNotEmpty(val))
                addrec.with("addressRegion", val);
        }

        if (! addrec.hasField("postalCode")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("PostalCode"));

            if (StringUtil.isNotEmpty(val))
                addrec.with("postalCode", val);
        }

        if (! addrec.hasField("addressCountry")) {
            String val = Struct.objectToString(TaskContext.getOrThrow().queryVariable("PostalCountry"));

            if (StringUtil.isEmpty(val))
                val = "US";

            addrec.with("addressCountry", val);
        }

        if (! addrec.isEmpty())
            schema.with("address", addrec);

        // same as

        ListStruct sameas = schema.getFieldAsList("sameAs");

        if (sameas == null)
            sameas = ListStruct.list();

        List<XElement> catalogs = ResourceHub.getResources().getConfig().getTagList("Catalog");

        for (XElement catalog : catalogs) {
            if (catalog.hasNotEmptyAttribute("Id") && catalog.attr("Id").startsWith("Social-")) {
                XElement settings = catalog.selectFirst("Settings");

                if ((settings != null) && settings.hasNotEmptyAttribute("Url"))
                    sameas.with(settings.attr("Url"));
            }
        }

        // sameAs from vars
        {
            ListStruct officialSameAs = Struct.objectToList(TaskContext.getOrThrow().queryVariable("OfficialSameAs"));

            if (officialSameAs != null) {
                for (int i = 0; i < officialSameAs.size(); i++) {
                    sameas.with(officialSameAs.getItemAsString(i));
                }
            }
        }

        if (! sameas.isEmpty())
            schema.with("sameAs", sameas);

        return schema;
    }
}
