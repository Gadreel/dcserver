package dcraft.service.work;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.GzipStream;
import dcraft.stream.file.MemoryDestStream;
import dcraft.stream.file.MemorySourceStream;
import dcraft.stream.file.PgpEncryptStream;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.pgp.ClearsignUtil;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;

public class EncryptPortableMessageWork extends ChainWork {
    static public EncryptPortableMessageWork of(RecordStruct message, RecordStruct sendInfo) {
        EncryptPortableMessageWork work = new EncryptPortableMessageWork();
        work.message = message;
        work.sendInfo = sendInfo;
        return work;
    }

    protected RecordStruct message = null;
    protected RecordStruct sendInfo = null;
    protected CharSequence source = null;

    @Override
    protected void init(TaskContext taskctx) throws OperatingContextException {
        if (!this.message.validate("PortableMessage")) {
            this.then(taskctx1 -> {
                Logger.error("Invalid message structure, cannot encrypt");
                taskctx1.returnEmpty();
            });

            return;
        }

        this.source = this.message.toPrettyString();

        KeyRingResource keyring = ResourceHub.getResources().getKeyRing();

        // make sure we have a sign key

        PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey("encryptor@" + ApplicationHub.getDeployment() + ".dc");

        if (seclocalsign == null) {
            this.then(taskctx1 -> {
                Logger.error("Cannot encrypt portable message, missing signing key: ");
                taskctx1.returnEmpty();
            });

            return;
        }

        String encryptTo = this.sendInfo.getFieldAsString("EncryptTo");

        if (StringUtil.isNotEmpty(encryptTo)) {
            PGPPublicKeyRing encryptor = keyring.findUserPublicKey(encryptTo);

            if (encryptor == null) {
                this.then(taskctx1 -> {
                    Logger.error("Cannot encrypt portable message, missing encrypt key: " + encryptTo);
                    taskctx1.returnEmpty();
                });

                return;
            }

            MemorySourceStream memorySourceStream = MemorySourceStream.fromChars(this.source);
            MemoryDestStream memoryDestStream = new MemoryDestStream();

            StreamFragment fragment = StreamFragment.of(
                    memorySourceStream,
                    GzipStream.create(),
                    new PgpEncryptStream()
                            .withPgpKeyring(encryptor),
                    memoryDestStream
            );

            this
                    .then(StreamWork.of(fragment))
                    .then(taskctx1 -> {
                       this.source = Base64.encodeToString(memoryDestStream.getResultReset().toArray(), true);
                       taskctx1.returnEmpty();
                    });
        }

        this.then(taskctx1 -> {
            ClearsignUtil.ClearSignResult cres = ClearsignUtil.clearSignMessage(source, keyring, seclocalsign, keyring.getPassphrase());

            taskctx1.returnValue(cres.file);
        });
    }
}
