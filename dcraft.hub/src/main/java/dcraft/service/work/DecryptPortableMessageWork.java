package dcraft.service.work;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.*;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.util.Base64;
import dcraft.util.StringUtil;

public class DecryptPortableMessageWork extends ChainWork {
    static public DecryptPortableMessageWork of(String source) {
        DecryptPortableMessageWork work = new DecryptPortableMessageWork();
        work.source = source;
        return work;
    }

    protected CharSequence source = null;

    @Override
    protected void init(TaskContext taskctx) throws OperatingContextException {
        CharSequence pmsg = PortableMessageUtil.verifyPortableMessage(this.source);

        if (StringUtil.isEmpty(pmsg)) {
            this.then(taskctx1 -> {
                Logger.error("Unable to decrypt message due to incomplete message sig verify");
                taskctx1.returnEmpty();
            });

            return;
        }

        // scan through sb looking for first non-whitespace char.  if { then it is plain text,
        // if not then the message is encrypted to my deployment's encrypt key  so decrypt first if it is

        boolean useDecrypt = true;

        for (int i = 0; i < pmsg.length(); i++) {
            char n = pmsg.charAt(i);

            if (! Character.isWhitespace(n)) {
                if (n == '{') {
                    this.source = pmsg;
                    useDecrypt = false;
                }

                break;
            }
        }

        if (useDecrypt) {
            byte[] data = Base64.decode(pmsg.toString());

            KeyRingResource keyring = ResourceHub.getResources().getKeyRing();

            MemorySourceStream encSource = MemorySourceStream.fromBinary(data);
            MemoryDestStream plainDest = new MemoryDestStream();

            StreamFragment fragment2 = StreamFragment.of(
                    encSource,
                    new PgpDecryptStream()
                            .withKeyResource(keyring)
                            .withPassword(keyring.getPassphrase()),
                    new UngzipStream(),
                    plainDest
            );

            this
                    .then(StreamWork.of(fragment2))
                    .then(taskctx12 -> {
                        DecryptPortableMessageWork.this.source = plainDest.getResultReset().toChars();
                        taskctx12.returnEmpty();
                    });
        }

        this
                .then(taskctx13 -> {
                    CharSequence decrypted = DecryptPortableMessageWork.this.source;

                    if (StringUtil.isEmpty(decrypted)) {
                        Logger.error("Unable to decrypt the Portable Message structure");
                        taskctx13.returnEmpty();
                        return;
                    }

                    System.out.println("got 2: " + decrypted);

                    RecordStruct brec = Struct.objectToRecord(decrypted);

                    if (brec == null) {
                        Logger.error("Unable to parse the Portable Message");
                        taskctx13.returnEmpty();
                        return;
                    }

                    if (brec.validate("PortableMessage")) {
                        taskctx13.returnValue(brec);
                    }
                    else {
                        Logger.error("Unable to validate the Portable Message structure");
                        taskctx13.returnEmpty();
                    }
                });
    }
}
