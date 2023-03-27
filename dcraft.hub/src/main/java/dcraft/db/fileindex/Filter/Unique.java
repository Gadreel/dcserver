package dcraft.db.fileindex.Filter;

import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Unique extends BasicFilter {
    static public Unique unique() {
        return new Unique();
    }

    protected Set<String> unique = new HashSet<>();

    public Collection<String> getValues() {
        return this.unique;
    }

    public int getCount() { return this.unique.size(); }

    public boolean isEmpty() {
        return this.unique.isEmpty();
    }

    public boolean contains(String v) {
        return this.unique.contains(v);
    }

    public boolean addAll(Collection<String> v) {
        return this.unique.addAll(v);
    }

    public boolean add(String v) {
        return this.unique.add(v);
    }

    // order is meaningless, but if you want just one of the elements
    public String getOne() {
        for (String o : this.unique)
            return o;

        return null;
    }

    @Override
    public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
        String val = vault.getName() + "|" + path.toString();

        // we have already returned this one
        if (this.unique.contains(val))
            return ExpressionResult.REJECTED;

        ExpressionResult nres = this.nestOrAccept(adapter, scope, vault, path, file);

        if (nres.accepted)
            this.unique.add(val);

        return nres;
    }
}
