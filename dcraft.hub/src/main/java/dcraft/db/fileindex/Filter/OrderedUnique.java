package dcraft.db.fileindex.Filter;

import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class OrderedUnique extends BasicFilter {
    static public OrderedUnique unique(ResultMode mode) {
        OrderedUnique filter = new OrderedUnique();
        filter.mode = mode;
        return filter;
    }

    protected ResultMode mode = ResultMode.FileInfo;     // return: 1 = path with file info, 2 = data with extra meta
    protected long max = 0;

    protected Set<String> unique = new HashSet<>();
    protected List<RecordStruct> values = new ArrayList<>();

    public OrderedUnique withMax(long max) {
        this.max = max;
        return this;
    }

    public List<RecordStruct> getValues() {
        return this.values;
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public boolean contains(String v) {
        return this.unique.contains(v);
    }

    public RecordStruct getOne() {
        return this.values.size() > 0 ? this.values.get(0) : null;
    }

    @Override
    public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
        String val = vault.getName() + "|" + path.toString();

        // we have already returned this one
        if (this.unique.contains(val))
            return ExpressionResult.REJECTED;

        ExpressionResult nres = this.nestOrAccept(adapter, scope, vault, path, file);

        if (nres.accepted) {
            this.unique.add(val);

            if (this.mode == ResultMode.FileInfo) {
                this.values.add(RecordStruct.record()
                        .with("Vault", vault.getName())
                        .with("Path", path)
                        .with("File", file)
                );
            }
            else {
                BaseStruct data = adapter.getData(vault, path);

                if (data instanceof RecordStruct) {
                    ((RecordStruct) data).getFieldAsRecord("Meta")
                            .with("Vault", vault.getName())
                            .with("Path", path);

                    this.values.add((RecordStruct) data);
                }
            }

            // we have already returned this one
            if ((this.max > 0) && (this.unique.size() >= this.max))
                return ExpressionResult.FOUND;  // accept but halt
        }

        return nres;
    }

    public enum ResultMode {
        FileInfo, DataInfo
    }
}
