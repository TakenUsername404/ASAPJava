package net.sharksystem.asap;

import net.sharksystem.asap.protocol.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiASAPEngineFS_Impl implements MultiASAPEngineFS, ASAPConnectionListener, ThreadFinishedListener {
    private CharSequence owner;
    private final HashMap<CharSequence, EngineSetting> folderMap;
    private final long maxExecutionTime;

    public static MultiASAPEngineFS createMultiEngine(CharSequence rootFolder, long maxExecutionTime,
                                                      ASAPReceivedChunkListener listener) throws ASAPException, IOException {
        return new MultiASAPEngineFS_Impl(rootFolder, maxExecutionTime, listener);
    }

    public static MultiASAPEngineFS createMultiEngine(CharSequence folder, ASAPReceivedChunkListener listener)
            throws ASAPException, IOException {

        return MultiASAPEngineFS_Impl.createMultiEngine(folder, DEFAULT_MAX_PROCESSING_TIME, listener);
    }

    MultiASAPEngineFS_Impl(CharSequence owner, List<ASAPEngineFSSetting> settings, long maxExecutionTime)
            throws ASAPException {

        if(settings == null) throw new ASAPException("no settings at all - makes no sense");
        if(settings.size() == 0) throw new ASAPException("no settings - makes no sense");

        this.owner = owner;
        this.folderMap = new HashMap<>();

        // fill settings
        for(ASAPEngineFSSetting setting : settings) {
            if(setting.folder.toString().contains("/") || setting.folder.toString().contains("\\")) {
                throw new ASAPException("sub folders are not allowed: " + setting.folder);
            }
            folderMap.put(setting.format, new EngineSetting(setting.folder, setting.listener));
        }

        this.maxExecutionTime = maxExecutionTime;
    }

    /**
     * assumed that a number of asap storages are already exists in subdirectories of the
     * root directory. setting list can be created by iterating those storages.
     * @param rootFolderName
     */
    MultiASAPEngineFS_Impl(CharSequence rootFolderName, long maxExecutionTime,
                                  ASAPReceivedChunkListener listener) throws ASAPException, IOException {

        this.owner = ASAPEngine.DEFAULT_OWNER; // probably dummy name
        this.maxExecutionTime = maxExecutionTime;
        this.folderMap = new HashMap<>();

        File rootFolder = new File(rootFolderName.toString());

        if (!rootFolder.isDirectory()) {
            throw new ASAPException("is not directory: " + rootFolderName);
        }

        File[] files = rootFolder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String fileName = file.getCanonicalPath();
                ASAPEngine engine = ASAPEngineFS.getExistingASAPEngineFS(fileName);
                EngineSetting setting = new EngineSetting(
                        rootFolderName + "/" + fileName, // folder
                        listener// listener
                        );
                setting.setASAPEngine(engine);
                this.folderMap.put(engine.format, setting);
            }
        }
    }

    public CharSequence getOwner() {
        return this.owner;
    }

    public ASAPEngine getEngineByFormat(CharSequence format) throws ASAPException, IOException {
        // get engine
        EngineSetting engineSetting = this.getEngineSettings(format);
        ASAPEngine asapEngine = engineSetting.engine;

        if (asapEngine == null) {
            asapEngine = ASAPEngineFS.getASAPEngine(owner.toString(), engineSetting.folder.toString(), format);
            engineSetting.setASAPEngine(asapEngine); // remember - keep that object
        }
        return asapEngine;
    }

    private EngineSetting getEngineSettings(CharSequence format) throws ASAPException {
        EngineSetting folderAndListener = folderMap.get(format);
        if(folderAndListener == null) throw new ASAPException("no folder for owner / format: " + owner + "/" + format);

        return folderAndListener;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                          connection management                                         //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public ASAPConnection handleConnection(InputStream is, OutputStream os) throws IOException, ASAPException {
        ASAPConnection_Impl asapConnection = new ASAPConnection_Impl(
                is, os, this, new ASAP_Modem_Impl(),
                maxExecutionTime, this, this);

        Thread thread = new Thread(asapConnection);
        thread.start();

        // remember
        this.runningThreads.add(thread);

        StringBuilder sb = new StringBuilder();
        sb.append(this.getLogStart());
        sb.append("launched new asapConnection thread, total number is now: ");
        sb.append(this.runningThreads.size());
        System.out.println(sb.toString());

        return asapConnection;
    }

    /** all running threads */
    private List<Thread> runningThreads = new ArrayList<>();

    @Override
    public void finished(Thread thread) {
        if(thread == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getLogStart());
            sb.append("finished thread cannot be null - do nothing");
            System.err.println(sb.toString());
            return;
        }

        this.runningThreads.remove(thread);

        StringBuilder sb = new StringBuilder();
        sb.append(this.getLogStart());
        sb.append("thread terminated - number of running threads is now: ");
        sb.append(this.runningThreads.size());
        System.out.println(sb.toString());
    }

    // thread connected to a peer
    private Map<CharSequence, ASAPConnection> connectedThreads = new HashMap<>();
    private Map<ASAPConnection, CharSequence> threadPeerNames = new HashMap<>();

    @Override
    public void asapConnectionStarted(String peerName, ASAPConnection thread) {
        if(thread == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getLogStart());
            sb.append("asap connection started but thread terminated cannot be null - do nothing");
            System.err.println(sb.toString());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(this.getLogStart());
        sb.append("asap connection started, got a peername: ");
        sb.append(peerName);
        System.out.println(sb.toString());

        this.connectedThreads.put(peerName, thread);
        this.threadPeerNames.put(thread, peerName);
    }

    @Override
    public synchronized void asapConnectionTerminated(Exception terminatingException, ASAPConnection thread) {
        if(thread == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getLogStart());
            sb.append("terminated connection cannot be null - do nothing");
            System.err.println(sb.toString());
            return;
        }

        // get thread name
        CharSequence threadName = this.threadPeerNames.remove(thread);
        StringBuilder sb = new StringBuilder();
        sb.append(this.getLogStart());
        sb.append("thread terminated connected to: ");

        if(threadName != null) {
            sb.append(threadName);
        } else {
            sb.append("null");
        }

        System.out.println(sb.toString());
    }

    @Override
    public boolean existASAPConnection(CharSequence recipient) {
        return this.getASAPConnection(recipient) != null;
    }

    @Override
    public ASAPConnection getASAPConnection(CharSequence recipient) {
        return this.connectedThreads.get(recipient);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                              ASAP management                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void pushInterests(OutputStream os) throws IOException, ASAPException {
        ASAP_1_0 protocol = new ASAP_Modem_Impl();

        // issue an interest for each owner / format combination
        for(CharSequence format : this.folderMap.keySet()) {
            protocol.interest(this.owner, null, format,null, -1, -1, os, false);
        }
    }

    @Override
    public Thread getExecutorThread(ASAP_PDU_1_0 asappdu, InputStream is, OutputStream os,
                                    ThreadFinishedListener threadFinishedListener) throws ASAPException {
        // process pdu
        return new ASAPPDUExecutor(asappdu, is, os,
                this.getEngineSettings(asappdu.getFormat()),
                new ASAP_Modem_Impl());
    }

   private String getLogStart() {
        return this.getClass().getSimpleName() + ": ";
    }

    private class EngineSetting {
        final CharSequence folder;
        final ASAPReceivedChunkListener listener;
        private ASAPEngine engine;

        EngineSetting(CharSequence folder, ASAPReceivedChunkListener listener) {
            this.folder = folder;
            this.listener = listener;
        }

        void setASAPEngine(ASAPEngine engine) {
            this.engine = engine;
        }
    }

    public static class ASAPPDUExecutor extends Thread {
        private final ASAP_PDU_1_0 asapPDU;
        private final InputStream is;
        private final OutputStream os;
        private final EngineSetting engineSetting;
        private final ASAP_1_0 protocol;

        ASAPPDUExecutor(ASAP_PDU_1_0 asapPDU, InputStream is, OutputStream os,
                        EngineSetting engineSetting, ASAP_1_0 protocol) {
            this.asapPDU = asapPDU;
            this.is = is;
            this.os = os;
            this.engineSetting = engineSetting;
            this.protocol = protocol;
        }

        public void run() {
            try {
                switch (asapPDU.getCommand()) {
                    case ASAP_1_0.INTEREST_CMD:
                        engineSetting.engine.handleASAPInterest((ASAP_Interest_PDU_1_0) asapPDU, protocol, os);
                        break;
                    case ASAP_1_0.OFFER_CMD:
                        engineSetting.engine.handleASAPOffer((ASAP_OfferPDU_1_0) asapPDU, protocol, os);
                        break;
                    case ASAP_1_0.ASSIMILATE_CMD:
                        engineSetting.engine.handleASAPAssimilate((ASAP_AssimilationPDU_1_0) asapPDU, protocol, is, os,
                                engineSetting.listener);
                        break;

                    default:
                        System.err.println(
                                this.getClass().getSimpleName() + ": " + "unknown ASAP command: " + asapPDU.getCommand());
                }
            }
            catch(IOException | ASAPException e) {
                System.err.println("Exception while processing ASAP PDU - close streams" + e.getLocalizedMessage());
                try {
                    os.close(); // more important to close than input stream - do it first
                    is.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
