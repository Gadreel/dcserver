package dcraft.cms.meta;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaResource;
import dcraft.struct.*;
import dcraft.struct.scalar.DateStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import io.netty.util.internal.ObjectUtil;
import org.threeten.extra.PeriodDuration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

abstract public class BasicCustomIterator implements ICustomIterator {
    protected RecordStruct index = null;
    protected RecordStruct params = null;
    protected CustomIndexAdapter adapter = null;
    protected FileIndexAdapter fileAdapter = null;
    protected IFilter recordFilter = null;
    protected IVariableAware scope = null;

    protected LocaleResource locale = null;

    protected IndexKeyInfo first = null;
    protected IndexKeyInfo second = null;

    protected List<String> vaultsAllowed = new ArrayList<>();
    protected List<String> vaultsExcluded = new ArrayList<>();

    protected List<String> feedsAllowed = new ArrayList<>();
    protected List<String> feedsExcluded = new ArrayList<>();

    @Override
    public void init(RecordStruct index, CustomIndexAdapter adapter, RecordStruct params, IFilter recordFilter, IVariableAware scope) throws OperatingContextException {
        this.index = index;
        this.adapter = adapter;
        this.params = params;
        this.recordFilter = recordFilter;
        this.scope = scope;

        // meta indexing is not meant for locale specific data/fields, always use site default locale
        this.locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();

        RecordStruct config = index.getFieldAsRecord("IndexHandlerConfig");

        if (config == null) {
            Logger.error("Single Key custom index missing handler config");
            return;
        }

        SchemaResource schemares = ResourceHub.getResources().getSchema();

        if (config.isNotFieldEmpty("FirstKey")) {
            this.first = new IndexKeyInfo();
            this.first.keyName = config.getFieldAsString("FirstKey");
            this.first.dataType = schemares.getType(config.getFieldAsString("FirstType", "dcMetaString"));
        }

        if (config.isNotFieldEmpty("SecondKey")) {
            this.second = new IndexKeyInfo();
            this.second.keyName = config.getFieldAsString("SecondKey");
            this.second.dataType = schemares.getType(config.getFieldAsString("SecondType", "dcMetaString"));
        }

        if (params != null) {
            ListStruct vaultsAllowed = params.getFieldAsList("VaultsAllowed");

            if (vaultsAllowed != null)
                this.vaultsAllowed = vaultsAllowed.toStringList();

            ListStruct vaultsExcluded = params.getFieldAsList("VaultsExcluded");

            if (vaultsExcluded != null)
                this.vaultsExcluded = vaultsExcluded.toStringList();

            ListStruct feedsAllowed = params.getFieldAsList("FeedsAllowed");

            if (feedsAllowed != null)
                this.feedsAllowed = feedsAllowed.toStringList();

            ListStruct feedsExcluded = params.getFieldAsList("FeedsExcluded");

            if (feedsExcluded != null)
                this.feedsExcluded = feedsExcluded.toStringList();

            if (params.isNotFieldEmpty("SearchKeys")) {
                ListStruct searchKeys = params.getFieldAsList("SearchKeys");

                for (int i = 0; i < searchKeys.size(); i++) {
                    RecordStruct searchKey = searchKeys.getItemAsRecord(i);

                    String keyName = searchKey.getFieldAsString("KeyName");

                    if (StringUtil.isNotEmpty(keyName)) {
                        if ((this.first != null) && keyName.equals(this.first.keyName)) {
                            if (searchKey.isNotFieldEmpty("Values")) {
                                this.first.srcValues = searchKey.getFieldAsList("Values");
                            }
                            else {
                                this.first.srcFrom = searchKey.getFieldAsScalar("From");
                                this.first.srcTo = searchKey.getFieldAsScalar("To");
                            }
                        }
                        else if ((this.second != null) && keyName.equals(this.second.keyName)) {
                            if (searchKey.isNotFieldEmpty("Values")) {
                                this.second.srcValues = searchKey.getFieldAsList("Values");
                            }
                            else {
                                this.second.srcFrom = searchKey.getFieldAsScalar("From");
                                this.second.srcTo = searchKey.getFieldAsScalar("To");
                            }
                        }
                    }
                }
            }
        }

        if (this.first != null)
            this.first.prepareInternal(this.locale.getDefaultLocale());

        if (this.second != null)
            this.second.prepareInternal(this.locale.getDefaultLocale());

        this.fileAdapter = FileIndexAdapter.of(adapter.request);
    }

    @Override
    public String getIndexAlias() {
        return this.index.getFieldAsString("Alias");
    }

    public ExpressionResult searchLevel(List<Object> indexkeys, List<IndexKeyInfo> levels) throws OperatingContextException {
        System.out.println("Search level a");

        if (levels.get(0).values != null)
            return this.searchValuesLevel(indexkeys, levels);
        else
            return this.searchFromToLevel(indexkeys, levels);
    }

    public ExpressionResult searchFromToLevel(List<Object> indexkeys, List<IndexKeyInfo> levels) throws OperatingContextException {
        System.out.println("Search From a");

        List<IndexKeyInfo> sublist = levels.subList(1, levels.size());

        IndexKeyInfo levelKey = levels.get(0);

        // don't change the real from, may need it for other loops
        // we don't currently support NULL for either From or To
        Object localFrom = levelKey.from;
        Object localTo = levelKey.to;

        if ("DateTime".equals(levelKey.dataType.getId())) {
            localFrom = ((ZonedDateTime) localFrom).withZoneSameInstant(ZoneId.of("UTC"));
            localTo = ((ZonedDateTime) localTo).withZoneSameInstant(ZoneId.of("UTC"));

            System.out.println("from: " + TimeUtil.stampFmt.format((ZonedDateTime) localFrom));
            System.out.println("to: " + TimeUtil.stampFmt.format((ZonedDateTime) localTo));
        }

        try {
            if (levelKey.reversed) {
                indexkeys.add(localFrom);   // find the actual start (backwards), if null that it fine
                localFrom = ByteUtil.extractValue(this.adapter.getRequest().getInterface().getOrPrevPeerKey(indexkeys.toArray()));
                indexkeys.remove(indexkeys.size() - 1);

                if (localFrom == null)
                    return ExpressionResult.REJECTED;

                indexkeys.add(localTo);     // find the actual end
                localTo = ByteUtil.extractValue(this.adapter.getRequest().getInterface().getOrNextPeerKey(indexkeys.toArray()));
                indexkeys.remove(indexkeys.size() - 1);

                if ((localTo == null) || (DataUtil.compareCore(localFrom, localTo) < 0))
                    return ExpressionResult.REJECTED;

                while ((localFrom != null) && (DataUtil.compareCore(localFrom, localTo) >= 0)) {
                    indexkeys.add(localFrom);

                    ExpressionResult subresult = (sublist.size() > 0) ? this.searchLevel(indexkeys, sublist) : this.searchTail(indexkeys);

                    if (! subresult.resume) {
                        indexkeys.remove(indexkeys.size() - 1);
                        return ExpressionResult.HALT;
                    }

                    localFrom = ByteUtil.extractValue(this.adapter.getRequest().getInterface().prevPeerKey(indexkeys.toArray()));
                    indexkeys.remove(indexkeys.size() - 1);
                }
            }
            else {
                indexkeys.add(localFrom);   // find the actual start (backwards), if null that it fine
                localFrom = ByteUtil.extractValue(this.adapter.getRequest().getInterface().getOrNextPeerKey(indexkeys.toArray()));
                indexkeys.remove(indexkeys.size() - 1);

                if (localFrom == null)
                    return ExpressionResult.REJECTED;

                indexkeys.add(localTo);     // find the actual end
                localTo = ByteUtil.extractValue(this.adapter.getRequest().getInterface().getOrPrevPeerKey(indexkeys.toArray()));
                indexkeys.remove(indexkeys.size() - 1);

                if ((localTo == null) || (DataUtil.compareCore(localFrom, localTo) > 0))
                    return ExpressionResult.REJECTED;

                //System.out.println("search from / to: " + " - actual from: " + localFrom + " - actual to: " + localTo + " - looking for: " + levelKey.from);

                while ((localFrom != null) && (DataUtil.compareCore(localFrom, localTo) <= 0)) {
                    indexkeys.add(localFrom);

                    ExpressionResult subresult = (sublist.size() > 0) ? this.searchLevel(indexkeys, sublist) : this.searchTail(indexkeys);

                    if (! subresult.resume) {
                        indexkeys.remove(indexkeys.size() - 1);
                        return ExpressionResult.HALT;
                    }

                    localFrom = ByteUtil.extractValue(this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray()));
                    indexkeys.remove(indexkeys.size() - 1);
                }
            }

            return ExpressionResult.ACCEPTED;
        }
        catch (DatabaseException x) {
            Logger.error("Unable to search record index " + this.getIndexAlias() + " in db: " + x);

            return ExpressionResult.HALT;
        }
    }

    public ExpressionResult searchValuesLevel(List<Object> indexkeys, List<IndexKeyInfo> levels) throws OperatingContextException {
        System.out.println("Search Values a");

        List<IndexKeyInfo> sublist = levels.subList(1, levels.size());

        IndexKeyInfo levelKey = levels.get(0);

        //if ("Tags".equals(levelKey.keyName))
        //    System.out.println("search tags");

        for (Object value : levelKey.values) {
            Object currvalue = value;

            // only for Tags
            if ("Tags".equals(levelKey.keyName) && (value instanceof String) && ((String) value).endsWith("*")) {
                String strvalue = (String) value;

                IndexKeyInfo altlevelKey = levelKey.deepCopy();

                String from = strvalue.substring(0, strvalue.length() - 1);
                String to = from.endsWith(":") ? from.substring(0, from.length() - 1) + ";" : from + "~";       // high ascii value

                altlevelKey.srcValues = null;
                altlevelKey.srcFrom = StringStruct.of(from);
                altlevelKey.srcTo = StringStruct.of(to);
                altlevelKey.prepareInternal(locale.getDefaultLocale());

                List<IndexKeyInfo> altsublist = new ArrayList<>();

                altsublist.add(altlevelKey);
                altsublist.addAll(sublist);

                ExpressionResult subresult = this.searchLevel(indexkeys, altsublist);

                if (! subresult.resume)
                    return subresult;

                // if ends with : then also collect direct matches, but if not then skip to next value
                if (! from.endsWith(":"))
                    continue;

                currvalue = from.substring(0, from.length() - 1);
            }

            try {
                indexkeys.add(currvalue);

                if (this.adapter.getRequest().getInterface().hasAny(indexkeys.toArray())) {
                    ExpressionResult subresult = (sublist.size() > 0) ? this.searchLevel(indexkeys, sublist) : this.searchTail(indexkeys);

                    if (! subresult.resume) {
                        indexkeys.remove(indexkeys.size() - 1);
                        return subresult;
                    }
                }

                indexkeys.remove(indexkeys.size() - 1);
            }
            catch (DatabaseException x) {
                Logger.error("Unable to scan record index " + currvalue + " in db: " + x);
            }
        }

        return ExpressionResult.ACCEPTED;
    }

    public ExpressionResult searchTail(List<Object> indexkeys) throws OperatingContextException {
        ExpressionResult result = ExpressionResult.ACCEPTED;

        try {
            indexkeys.add(null);        // top of vaults

            byte[] vkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());

            while (vkey != null) {
                Object vval = ByteUtil.extractValue(vkey);

                indexkeys.remove(indexkeys.size() - 1);
                indexkeys.add(vval);

                if (vval instanceof String) {
                    String vaultName = (String) vval;

                    if (! this.vaultsExcluded.contains(vaultName) && ((this.vaultsAllowed.size() == 0) || this.vaultsAllowed.contains(vaultName))) {
                        Vault vault = OperationContext.getOrThrow().getSite().getVault(vaultName);

                        indexkeys.add(null);        // top of path

                        byte[] pkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());

                        while (pkey != null) {
                            Object pval = ByteUtil.extractValue(pkey);

                            indexkeys.remove(indexkeys.size() - 1);
                            indexkeys.add(pval);

                            if (pval instanceof String) {
                                CommonPath path = CommonPath.from((String) pval);

                                RecordStruct frec = this.fileAdapter.fileInfo(vault, path, scope);

                                if ((frec != null) && !frec.getFieldAsBooleanOrFalse("IsFolder")) {
                                    //System.out.println("Found: " + path);

                                    ExpressionResult fileresult = this.recordFilter.check(this.fileAdapter, this.scope, vault, path, frec);

                                    if (! fileresult.resume) {
                                        result = fileresult;
                                        break;
                                    }
                                }
                            }

                            pkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
                        }

                        indexkeys.remove(indexkeys.size() - 1); // back to vault level
                    }
                }

                if (! result.resume)
                    break;

                vkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
            }

            indexkeys.remove(indexkeys.size() - 1); // back to index level

            return result;
        }
        catch (DatabaseException x) {
            Logger.error("scan custom index in db: " + x);

            return ExpressionResult.HALT;
        }
    }

    static class IndexKeyInfo {
        protected String keyName = null;
        protected DataType dataType = null;
        protected ListStruct srcValues = null;
        protected ScalarStruct srcFrom = null;
        protected ScalarStruct srcTo = null;

        // internal to index
        protected List<Object> values = null;
        protected Object from = null;
        protected Object to = null;
        protected boolean reversed = false;

        public IndexKeyInfo deepCopy() {
            IndexKeyInfo cp = new IndexKeyInfo();

            cp.keyName = this.keyName;
            cp.dataType = this.dataType;
            cp.srcValues = this.srcValues;
            cp.srcFrom = this.srcFrom;
            cp.srcTo = this.srcTo;
            cp.values = this.values;
            cp.from = this.from;
            cp.to = this.to;
            cp.reversed = this.reversed;

            return cp;
        }

        protected void prepareInternal(String locale) {
            this.values = null;
            this.from = null;
            this.to = null;
            this.reversed = false;

            if (this.srcValues != null)
                this.prepareInternalList(locale);
            else
                this.prepareInternalFromTo(locale);
        }

        protected void prepareInternalList(String locale) {
            this.values = new ArrayList<>();

            for (int i = 0; i < this.srcValues.size(); i++) {
                Object iv = this.dataType.toIndex(this.srcValues.getAt(i), locale);

                this.values.add(iv);
            }
        }

        protected void prepareInternalFromTo(String locale) {
            if ("DateTime".equals(this.dataType.getId())) {
                ZonedDateTime fromdt = null;
                ZonedDateTime todt = null;

                // FROM

                if (this.srcFrom instanceof StringStruct) {
                    String frominfo = ((StringStruct) this.srcFrom).getValueAsString();

                    if ("-".equals(frominfo))
                        fromdt = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
                    else if ("+".equals(frominfo))
                        fromdt = ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
                    else if ("now".equals(frominfo))
                        fromdt = TimeUtil.now();
                    else if ("today".equals(frominfo))
                        fromdt = TimeUtil.now().toLocalDate().atStartOfDay(ZoneId.of("UTC"));
                    else if ("yesterday".equals(frominfo))
                        fromdt = TimeUtil.now().toLocalDate().minusDays(1).atStartOfDay(ZoneId.of("UTC"));
                    else if ("tomorrow".equals(frominfo))
                        fromdt = TimeUtil.now().toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC"));

                    if (fromdt == null) {
                        fromdt = Struct.objectToDateTime(frominfo);
                    }

                    if (fromdt == null) {
                        PeriodDuration period = PeriodDuration.parse(frominfo);

                        if (period != null)
                            fromdt = TimeUtil.now().plus(period);
                    }
                }
                else {
                    fromdt = Struct.objectToDateTime(this.srcFrom);
                }

                if (fromdt == null)
                    fromdt = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

                // TO

                if (this.srcTo instanceof StringStruct) {
                    String toinfo = ((StringStruct) this.srcTo).getValueAsString();

                    if ("-".equals(toinfo))
                        todt = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
                    else if ("+".equals(toinfo))
                        todt = ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
                    else if ("now".equals(toinfo))
                        todt = TimeUtil.now();
                    else if ("today".equals(toinfo))
                        todt = TimeUtil.now().toLocalDate().atStartOfDay(ZoneId.of("UTC"));
                    else if ("yesterday".equals(toinfo))
                        todt = TimeUtil.now().toLocalDate().minusDays(1).atStartOfDay(ZoneId.of("UTC"));
                    else if ("tomorrow".equals(toinfo))
                        todt = TimeUtil.now().toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC"));

                    if (todt == null)
                        todt = Struct.objectToDateTime(toinfo);

                    if (todt == null) {
                        PeriodDuration period = PeriodDuration.parse(toinfo);

                        if (period != null)
                            todt = TimeUtil.now().plus(period);
                    }
                }
                else {
                    todt = Struct.objectToDateTime(this.srcTo);
                }

                if (todt == null)
                    todt = ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

                this.from = this.dataType.toIndex(fromdt, locale);
                this.to = this.dataType.toIndex(todt, locale);

                // convert to long for reliable compare
                this.reversed = (todt.toInstant().toEpochMilli() < fromdt.toInstant().toEpochMilli());
            }
            else {
                from = this.dataType.toIndex(this.srcFrom, locale);
                to = this.dataType.toIndex(this.srcTo, locale);

                // TODO check reverse
            }
        }
    }
}
