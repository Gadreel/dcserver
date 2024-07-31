package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryImageConsumer;
import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.DecimalStruct;
import dcraft.util.*;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.*;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlToJson;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SliderWidget extends Base implements ICMSAware {
    static public SliderWidget tag() {
        SliderWidget el = new SliderWidget();
        el.setName("dcm.SliderWidget");
        return el;
    }

    @Override
    public XElement newNode() {
        return SliderWidget.tag();
    }

    @Override
    public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
        String target = StackUtil.stringFromSource(state, "SyncTo");

        if (StringUtil.isNotEmpty(target)) {
            XElement sync = this.getRoot(state).findId(target);

            if (sync != null) {
                this
                    .attr("Path", sync.attr("Path"))   // let it expand normally - StackUtil.stringFromElement(state, gallery, "Path");
                    .attr("data-target", target)
                    .withAll(sync.selectAll("Image"));
            }
        }

        String gallery = StackUtil.stringFromSource(state,"Path");
        boolean useseopath = StackUtil.boolFromSource(state,"SEOPath", false);

        if (this.hasNotEmptyAttribute("Centering"))
            this.withAttribute("data-dcm-centering", StackUtil.stringFromSource(state, "Centering"));

        String vari = StackUtil.stringFromSource(state,"Variant", "full");

        boolean preloadenabled = this.hasNotEmptyAttribute("Preload")
                ? "true".equals(StackUtil.stringFromSource(state,"Preload").toLowerCase())
                : false;

        String ext = StackUtil.stringFromSource(state, "Extension", "jpg");

        // TODO check meta for ext vari maybe?

        this.attr("data-dc-ext", ext);
        this.attr("data-dc-variant", vari);

        String imgcache = StackUtil.stringFromSource(state, "ImageCache", "Max").toLowerCase();

        XElement ariatemplate = this.selectFirst("AriaTemplate");

        this.remove(ariatemplate);

        W3 arialist = (W3) W3.tag("div").withClass("dc-element-hidden")
                .attr("role", "list").attr("aria-label", "banner images");

        AtomicBoolean ariatemplateused = new AtomicBoolean(false);

        List<XElement> images = this.selectAll("Image");

        // clear so not in html output
        for (XElement img : images)
            this.remove(img);

        //System.out.println("using show: " + alias);

        this
                .withClass("dc-no-select")
                .withAttribute("data-dcm-period", StackUtil.stringFromSource(state,"Period"))
                .withAttribute("data-dcm-gallery", gallery);

        RecordStruct meta = GalleryUtil.getMeta(gallery);

        RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

        String display = this.getAttribute("Display", "None").toLowerCase();

        Base viewer = W3Closed.tag("img");

        viewer
                .withClass("dcm-widget-slider-img", "autoplay")
                .attr("alt","")
                .attr("aria-hidden","true");

        Base fader = W3Closed.tag("img");

        fader
                .withClass("dcm-widget-slider-fader")
                .attr("alt","")
                .attr("aria-hidden","true");

        Base list = W3.tag("div").withClass("dcm-widget-slider-list");

        list.attr("role", "list")
                .attr("aria-hidden", "true");

        if (display.contains("lrcontrol")) {
            this.with(W3.tag("div")
                    .withClass("dcm-widget-slider-ctrl dcm-widget-slider-ctrl-left")
                    .attr("aria-hidden", "true")
                    .with(Link.tag()
                            .attr("aria-label", "previous item")
                            .with(StackedIcon.tag().with(
                                    XElement.tag("Icon").attr("class", "fa5-stack-2x").attr("Path", "fas/circle"),
                                    XElement.tag("Icon").attr("class", "fa5-stack-1x fa5-inverse").attr("Path", "fas/chevron-left")
                                )
                            )
                    )
            );
        }

        this.with(fader).with(viewer).with(list);

        if (display.contains("lrcontrol")) {
            this.with(W3.tag("div")
                    .withClass("dcm-widget-slider-ctrl dcm-widget-slider-ctrl-right")
                    .attr("aria-hidden", "true")
                    .with(Link.tag()
                            .attr("aria-label", "next item")
                            .with(StackedIcon.tag().with(
                                    XElement.tag("Icon").attr("class", "fa5-stack-2x").attr("Path", "fas/circle"),
                                    XElement.tag("Icon").attr("class", "fa5-stack-1x fa5-inverse").attr("Path", "fas/chevron-right")
                                )
                            )
                    )
            );
        }

        AtomicLong currimg = new AtomicLong();

        StackUtil.addVariable(state, "_Gallery", meta);
        StackUtil.addVariable(state, "_Variant", vdata);

        state.getStore().with("SourceVariant", vdata);

        GalleryImageConsumer galleryImageConsumer = new GalleryImageConsumer() {
            @Override
            public void accept(RecordStruct meta, RecordStruct showrec, RecordStruct img) throws OperatingContextException {
                long cidx = currimg.incrementAndGet();

                StackUtil.addVariable(state, "_Show", showrec);		// unnecessarily repeats, but easier than rewriting the consumer class

                String cpath = img.getFieldAsString("Path");

                String ext = (meta != null) ? meta.getFieldAsString("Extension", "jpg") : "jpg";

                // TODO support alt ext (from the gallery meta.json)

                String lpath = cpath + ".v/" + vari + "." + ext;
                String seopath = cpath + "." + ext;

                Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
                        OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

                try {
                    if (imgpath != null) {
                        if ("max".equals(imgcache)) {
                            FileTime fileTime = Files.getLastModifiedTime(imgpath);

                            img.with("Path", "/galleries" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
                            img.with("SEOPath", "/galleries" + seopath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))) + "&dc-variant=" + vari);
                        }
                        else {
                            img.with("Path", "/galleries" + lpath);
                            img.with("SEOPath", "/galleries" + seopath);
                        }
                    }
                }
                catch (IOException | NullPointerException x) {
                    Logger.warn("Problem finding image file: " + lpath);
                    img.with("Path", "/galleries" + lpath);
                    img.with("SEOPath", "/galleries" + seopath);
                }

                img.with("Position", cidx);
                img.with("BasePath", cpath);

                RecordStruct data = GalleryUtil.getImageMeta(cpath);

                if (img.isNotFieldEmpty("Element")) {
                    // override center hint
                    String centerhint = img.selectAsString("Element.attributes.CenterHint");

                    if (StringUtil.isNotEmpty(centerhint))
                        data.with("CenterHint", centerhint);

                    String centerAlign = img.selectAsString("Element.attributes.CenterAlign");

                    if (StringUtil.isNotEmpty(centerAlign))
                        data.with("CenterAlign", centerAlign);
                }

                img.with("Data", data);

                // TODO support a separate preload image, that is not a variation but its own thing
                // such as checkered logos in background

                if (cidx == 1) {
                    state.getStore().with("FirstImageData", data);

                    if (preloadenabled) {
                        Path preload = OperationContext.getOrThrow().getSite().findSectionFile("galleries", cpath + ".v/preload.jpg",
                                OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

                        if (preload != null) {
                            Memory mem = IOUtil.readEntireFileToMemory(preload);

                            String idata = Base64.encodeToString(mem.toArray(), false);

                            viewer.withAttribute("src", "data:image/jpeg;base64," + idata);
                        }
                    }
                    else {
                        viewer.withAttribute("src", useseopath ? img.getFieldAsString("SEOPath") : img.getFieldAsString("Path"));
                    }

                    fader.attr("src", viewer.attr("src"));
                }

                // TODO use aria templates

                Base iel = W3Closed.tag("img");

                iel
                        .withAttribute("role", "listitem")
                        .withAttribute("src", useseopath ? img.getFieldAsString("SEOPath") : img.getFieldAsString("Path"))
                        .withAttribute("alt", data.selectAsString("Description"))
                        .withAttribute("data-dc-image-alias", img.getFieldAsString("Alias"))
                        .withAttribute("data-dc-image-pos", img.getFieldAsString("Position"))
                        .withAttribute("data-dc-image-base-path", img.getFieldAsString("BasePath"))
                        .withAttribute("data-dc-image-element", img.isNotFieldEmpty("Element") ? img.getFieldAsString("Element").toString() : "")
                        .withAttribute("data-dc-center-hint", data.getFieldAsString("CenterHint"))
                        .withAttribute("data-dc-center-align", data.getFieldAsString("CenterAlign"))
                        .withAttribute("data-dc-image-data", data.toString());

                list.with(iel);

                try {
                    // setup image for expand
                    StackUtil.addVariable(state, "image-" + cidx, img);

                    // switch images during expand
                    XElement setvar = Var.tag()
                            .withAttribute("Name", "Image")
                            .withAttribute("SetTo", "$image-" + cidx);

                    arialist.with(setvar);

                    XElement setvarnew = Var.tag()
                            .withAttribute("Name", "_Image")
                            .withAttribute("SetTo", "$image-" + cidx);

                    arialist.with(setvarnew);

                    if (ariatemplate != null) {
                        // add nodes using the new variable
                        XElement entry = ariatemplate.deepCopy();

                        for (XNode node : entry.getChildren())
                            arialist.with(node);

                        ariatemplateused.set(true);
                    }
                }
                catch (OperatingContextException x) {
                    Logger.warn("Could not reference image data: " + x);
                }
            }
        };

        // TODO add a randomize option

        RecordStruct showrec = RecordStruct.record()
                .with("Title", "Default")
                .with("Alias", "default")
                .with("Variation", vari);

        for (XElement img : images) {
            galleryImageConsumer.accept(meta, showrec, RecordStruct.record()
                    .with("Alias", img.getAttribute("Alias"))
                    .with("Path", gallery + "/" + img.getAttribute("Alias"))
                    .with("Element", XmlToJson.convertXml(img,true, true))
            );
        }

        if (ariatemplateused.get())
            this.with(arialist);

        this
                .withAttribute("data-variant", vari)
                .withAttribute("data-ext", ext)
                .withAttribute("data-path", gallery);

        if (images.size() > 0)
            this.withClass("single");

        UIUtil.markIfEditable(state, this, "widget");
    }

    @Override
    public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
        String display = StackUtil.stringFromSource(state,"Display", "None").toLowerCase();

        // TODO edit is conditional to user
        this
                .withClass("dc-widget", "dcm-widget-slider", "dc-widget-image")
                .withClass("dc-media-box", "dc-media-image")
                .withAttribute("data-dc-enhance", "true")
                .withAttribute("data-dc-tag", this.getName())
                .withAttribute("data-dc-display", display)
                .withAttribute("data-property-editor", StackUtil.stringFromSource(state,"PropertyEditor"));

        if (display.contains("lrcontrol")) {
            this.withClass("dcm-widget-slider-lr");
        }

        if (display.contains("banner")) {
            this.withClass("dcm-widget-slider-banner");

            // if using css centering
            if (this.hasNotEmptyAttribute("Centering") && "css".equals(StackUtil.stringFromSource(state, "Centering").toLowerCase())) {
                String heightv = StackUtil.stringFromSource(state, "BannerHeight");      // in VW units
                String maxheightv = StackUtil.stringFromSource(state, "BannerMaxHeight");
                String minheightv = StackUtil.stringFromSource(state, "BannerMinHeight");
                String defAlign = StackUtil.stringFromSource(state, "CenterAlign");
                Long defHint = StackUtil.intFromSource(state, "CenterHint");

                BigDecimal height = Struct.objectToDecimal(heightv);
                Long maxheight = Struct.objectToInteger(maxheightv);
                Long minheight = Struct.objectToInteger(minheightv);

                if ((height != null) && (maxheight != null) && (minheight != null)) {
                    RecordStruct variant = state.getStore().getFieldAsRecord("SourceVariant");

                    if (variant != null) {
                        Long exactWidth = variant.getFieldAsInteger("ExactWidth");

                        if (exactWidth != null) {
                            BigDecimal centerConst = new BigDecimal(minheight)
                                .divide(new BigDecimal(maxheight), 6, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(exactWidth));

                            RecordStruct firstdata = state.getStore().getFieldAsRecord("FirstImageData");

                            String centerAlign = firstdata.getFieldAsString("CenterAlign");

                            Long centerHint = firstdata.getFieldAsInteger("CenterHint");

                            String currAlign = null;
                            Long currHint = null;

                            // defaults only apply if NO local set
                            if (StringUtil.isNotEmpty(centerAlign)) {
                                currAlign = centerAlign;
                                currHint = centerHint;
                            }
                            else {
                                currAlign = defAlign;
                                currHint = defHint;
                            }

                            StringBuilder customVars = new StringBuilder();

                            customVars.append("--bannerHeight: " + heightv + "vw; --bannerMaxHeight: " + maxheightv
                                    + "px; --bannerMinHeight: " + minheightv + "px; --centerConst: " + Struct.objectToInteger(centerConst) + "px; "
                                    + "height: var(--bannerHeight); max-height: var(--bannerMaxHeight); min-height: var(--bannerMinHeight); ");

                            // align: Left = dc-center-left
                            // align: Left w/ Hint = dc-center-left-hint
                            // align: Center = dc-center-default
                            // align: Right w/ Hint = dc-center-right-hint
                            // align: Right = dc-center-right

                            if (currHint != null)
                                customVars.append("--centerHint: " + currHint + "px; ");

                            if (StringUtil.isNotEmpty(currAlign)) {
                                customVars.append("--centerAlign: '" + currAlign + "'; ");

                                customVars.append("--centerLeftHintCalc: calc(min((var(--centerConst) - 100vw) / 2, var(--centerHint))); ");
                                customVars.append("--centerLeftCalc: calc((var(--centerConst) - 100vw) / 2); ");
                                customVars.append("--centerRightHintCalc: calc(0px - min((var(--centerConst) - 100vw) / 2, var(--centerHint))); ");
                                customVars.append("--centerRightCalc: calc(0px - ((var(--centerConst) - 100vw) / 2)); ");

                                if ("Left".equals(currAlign) && (currHint != null)) {
                                    this.withClass("dc-center-left-hint");
                                }
                                else if ("Left".equals(currAlign)) {
                                    this.withClass("dc-center-left");
                                }
                                else if ("Right".equals(currAlign) && (currHint != null)) {
                                    this.withClass("dc-center-right-hint");
                                }
                                else if ("Right".equals(currAlign)) {
                                    this.withClass("dc-center-right");
                                }
                                else {
                                    this.withClass("dc-center-default");
                                }
                            }
                            else {
                                this.withClass("dc-center-default");
                            }

                            /* example CSS  - original
                            @media all and (max-width: 1081px) {
                                #dcuiMain.general #bannerTop.dc-center-left {
                                    margin-left: calc((1081px - 100vw) / 2);
                                }
                                #dcuiMain.general #bannerTop.dc-center-left-hint {
                                    margin-left: calc(min((1081px - 100vw) / 2, 250px * .54));
                                }
                                #dcuiMain.general #bannerTop.dc-center-right-hint {
                                 margin-left: calc(0px - min((1081px - 100vw) / 2, 250px * .54));
                                }
                                #dcuiMain.general #bannerTop.dc-center-right {
                                    margin-left: calc(0px - (1081px - 100vw) / 2);
                                }
                            }
                             */

                            /* example CSS  - vars
                            @media all and (max-width: 1081px) {
                                #dcuiMain.general #bannerTop.dc-center-left {
                                    margin-left: var(--centerLeftCalc);
                                }
                                #dcuiMain.general #bannerTop.dc-center-left-hint {
                                    margin-left: var(--centerLeftHintCalc);
                                }
                                #dcuiMain.general #bannerTop.dc-center-right-hint {
                                    margin-left: var(--centerRightHintCalc);
                                }
                                #dcuiMain.general #bannerTop.dc-center-right {
                                    margin-left: var(--centerRightCalc);
                                }
                            }

                            test with
                            window.getComputedStyle(temp1).getPropertyValue('--bannerHeight');
                             */


                            this.attr("style", customVars.toString());
                        }
                    }
                }
            }
        }
        else {
            this.withClass("dcm-widget-slider-inline");
        }

        this.setName("div");
    }


    @Override
    public void canonicalize() throws OperatingContextException {
        XElement template = this.selectFirst("AriaTemplate");

        if (template == null) {
            // set default
            this.with(Base.tag("AriaTemplate").with(
                    W3.tag("div")
                            .withAttribute("role", "listitem")
                            .withText("{$Image.Element.@Title}")
            ));
        }
    }

    @Override
    public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
        XElement part = this;

        String cmd = command.getFieldAsString("Command");

        if ("Reorder".equals(cmd)) {
            ListStruct neworder = command.selectAsList("Params.Order");

            if (neworder == null) {
                Logger.error("New order is missing");
                return true;		// command was handled
            }

            List<XElement> children = this.selectAll("Image");

            // remove all images
            for (XElement el : children)
                this.remove(el);

            // add images back in new order
            for (int i = 0; i < neworder.size(); i++) {
                int pos = neworder.getItemAsInteger(i).intValue();

                if (pos >= children.size()) {
                    Logger.warn("bad gallery positions");
                    break;
                }

                part.with(children.get(pos));
            }

            return true;		// command was handled
        }

        if ("UpdatePart".equals(cmd)) {
            // TODO check that the changes made are allowed - e.g. on TextWidget
            RecordStruct params = command.getFieldAsRecord("Params");
            String area = params.selectAsString("Area");

            if ("Props".equals(area)) {
                RecordStruct props = params.getFieldAsRecord("Properties");

                if (props != null) {
                    for (FieldStruct fld : props.getFields()) {
                        if (fld.getValue() != null)
                            this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
                        else
                            this.removeAttribute(fld.getName());
                    }
                }

                return true;
            }

            if ("SetImage".equals(area)) {
                String alias = params.getFieldAsString("Alias");
                XElement fnd = null;

                for (XElement xel : this.selectAll("Image")) {
                    if (alias.equals(xel.getAttribute("Alias"))) {
                        fnd = xel;
                        break;
                    }
                }

                if (fnd == null) {
                    fnd = XElement.tag("Image");

                    if (! params.getFieldAsBooleanOrFalse("AddTop"))
                        this.with(fnd);
                    else
                        this.add(0, fnd);
                }

                fnd.clearAttributes();

                // rebuild attrs
                fnd.attr("Alias", alias);

                RecordStruct props = params.getFieldAsRecord("Properties");

                if (props != null) {
                    for (FieldStruct fld : props.getFields()) {
                        if (fld.getValue() != null)
                            fnd.attr(fld.getName(), Struct.objectToString(fld.getValue()));
                        else
                            fnd.removeAttribute(fld.getName());
                    }
                }

                return true;
            }

            if ("RemoveImage".equals(area)) {
                String alias = params.getFieldAsString("Alias");
                XElement fnd = null;

                for (XElement xel : this.selectAll("Image")) {
                    if (alias.equals(xel.getAttribute("Alias"))) {
                        fnd = xel;
                        break;
                    }
                }

                if (fnd != null) {
                    this.remove(fnd);
                }

                return true;
            }

            if ("AriaTemplate".equals(area)) {
                this.canonicalize();    // so all Tr's have a Locale

                String targetcontent = params.getFieldAsString("AriaTemplate");

                String template = "<AriaTemplate>" + targetcontent + "</AriaTemplate>";

                try (OperationMarker om = OperationMarker.clearErrors()) {
                    XElement txml = ScriptHub.parseInstructions(template);

                    if (!om.hasErrors() && (txml != null)) {
                        XElement oldtemp = this.selectFirst("AriaTemplate");

                        if (oldtemp != null)
                            this.remove(oldtemp);

                        this.with(txml);
                    } else {
                        Logger.warn("Keeping old template, new one is not valid.");
                    }
                }
                catch (Exception x) {
                }

                return true;
            }
        }

        return false;
    }

    /*
    @Override
    public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
        String mypath = StackUtil.stringFromSource(state, "Path");
        String target = StackUtil.stringFromSource(state, "SyncTo");

        if (StringUtil.isNotEmpty(target) && StringUtil.isEmpty(mypath)) {
            // TODO always hide this since it should have a sync element and the reader / AT should get info from there
            // not necessarily the case for
            // this.attr("aria-hidden", "true");

            XElement gallery = this.getRoot(state).findId(target);

            if (gallery != null) {
                String path = gallery.attr("Path");   // let it expand normally - StackUtil.stringFromElement(state, gallery, "Path");

                if (StringUtil.isNotEmpty(path)) {
                    XElement image = gallery.selectFirst("Image");

                    if (image != null) {
                        String alias = image.attr("Alias");

                        if (StringUtil.isNotEmpty(alias)) {
                            this.attr("Path", path + "/" + alias);
                        }
                    }

                    this.attr("data-target", target);
                }
            }
        }

        super.renderBeforeChildren(state);
    }

    @Override
    public void canonicalize() throws OperatingContextException {
        XElement template = this.selectFirst("Template");

        if (template == null) {
            String display = this.getAttribute("Display", "None").toLowerCase();

            template = Base.tag("Template");

            if ("lrcontrol".equals(display)) {
                template.with(W3.tag("div")
                        .withClass("dcm-widget-slider-ctrl dcm-widget-slider-ctrl-left")
                        .attr("aria-hidden", "true")
                        .with(Link.tag()
                                .attr("aria-label", "previous item")
                                .with(StackedIcon.tag().with(
                                        XElement.tag("Icon").attr("class", "fa5-stack-2x").attr("Path", "fas/circle"),
                                        XElement.tag("Icon").attr("class", "fa5-stack-1x fa5-inverse").attr("Path", "fas/chevron-left")
                                    )
                                )
                        )
                );
            }

            template.with(
                    W3.tag("img")
                            .withClass("pure-img-inline", "autoplay")
                            .withAttribute("alt", "{$Image.Description}")
                            .withAttribute("src", "{$Image.Path}")
                            .withAttribute("srcset", "{$Image.SourceSet|ifempty:}")
                            .withAttribute("sizes", "{$Image.Sizes|ifempty:}")
            );

            if ("lrcontrol".equals(display)) {
                template.with(W3.tag("div")
                        .withClass("dcm-widget-slider-ctrl dcm-widget-slider-ctrl-right")
                        .attr("aria-hidden", "true")
                        .with(Link.tag()
                                .attr("aria-label", "next item")
                                .with(StackedIcon.tag().with(
                                        XElement.tag("Icon").attr("class", "fa5-stack-2x").attr("Path", "fas/circle"),
                                        XElement.tag("Icon").attr("class", "fa5-stack-1x fa5-inverse").attr("Path", "fas/chevron-right")
                                    )
                                )
                        )
                );
            }

                // set default
            this.with(template);
        }
    }

    @Override
    public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
        super.renderAfterChildren(state);

        this.withClass("dcm-widget-slider");

        String display = StackUtil.stringFromSource(state,"Display", "None").toLowerCase();

        if ("lrcontrol".equals(display)) {
            this.withClass("dcm-widget-slider-lr");
        }
    }

     */
}
