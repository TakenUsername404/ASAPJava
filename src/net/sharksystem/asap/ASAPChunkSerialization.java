package net.sharksystem.asap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Implements parts of the AAS protocol.
 * @author thsc
 */
abstract class ASAPChunkSerialization {
    static void sendChunk(ASAPChunk chunk, DataOutputStream dos)
            throws IOException {
        
        //<<<<<<<<<<<<<<<<<<debug
        StringBuilder b = new StringBuilder();
        b.append("send chunk url / #messages: ");
        b.append(chunk.getUri());
        b.append(" / ");
        b.append(chunk.getNumberMessage());
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug
        
        // send url
        dos.writeUTF(chunk.getUri());
        
        // send #messages
        dos.writeInt(chunk.getNumberMessage());

        Iterator<CharSequence> messages = chunk.getMessages();
        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append("iterate messages ");
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug
        while(messages.hasNext()) {
            CharSequence message = messages.next();
            //<<<<<<<<<<<<<<<<<<debug
            b = new StringBuilder();
            b.append("read message from chunk: ");
            b.append(message);
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug
            
            // send message
            dos.writeUTF((String) message);

            //<<<<<<<<<<<<<<<<<<debug
            b = new StringBuilder();
            b.append("wrote message to stream: ");
            b.append(message);
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug

        }
        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append("stop iterating messages ");
        System.out.println(b.toString());
    }
    
    /**
     * This methods reads until an IOException notifies closed connection.
     * It expects serialized chunks (plural!) which are written to received
     * message storage.
     * 
     * @param chunkStorage where the received chunks are stored
     * @param storage that looks like a design glitch TODO
     * @param dis input stream to read serialized chunks
     * @param listener to be notified about a successfully deserialized chunk
     * @throws IOException 
     */
    static void readChunks(String sender, ASAPStorage chunkStorage,
                           ASAPChunkStorage storage, DataInputStream dis,
                           ASAPReceivedChunkListener listener) throws IOException {
        
        StringBuilder b = new StringBuilder();
        b.append("ASPChunkSerialization.readChunks (sender: ");
        b.append(sender);
        b.append(") ");
        
        String logStart = b.toString();
        
        try {
            while(true) { // until IOException informs end of communication
                // read URI
                String uri = dis.readUTF();

                // read number of messages
                int number = dis.readInt();

                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(logStart);
                b.append("read chunkURI / #messages / sender: ");
                b.append(uri);
                b.append(" / ");
                b.append(number);
                b.append(" / ");
                b.append(sender);
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug
                ASAPChunk chunk =
                        storage.getChunk(uri, chunkStorage.getEra());

                if(chunk != null) {
                    //<<<<<<<<<<<<<<<<<<debug
                    b = new StringBuilder();
                    b.append(logStart);
                    b.append("got chunk: ");
                    b.append(uri);
                    System.out.println(b.toString());
                    //>>>>>>>>>>>>>>>>>>>debug
                } else {
                    //<<<<<<<<<<<<<<<<<<debug
                    b = new StringBuilder();
                    b.append(logStart);
                    b.append("ERROR: no chunk found for sender/uri: ");
                    b.append(" / ");
                    b.append(uri);
                    System.err.println(b.toString());
                    //>>>>>>>>>>>>>>>>>>>debug
                    throw new IOException("couldn't create local chunk storage - give up");
                }

                for(;number > 0; number--) {
                    // escapes with IOException
                    String message = dis.readUTF();
                    //<<<<<<<<<<<<<<<<<<debug
                    b = new StringBuilder();
                    b.append(logStart);
                    b.append("read message: ");
                    b.append(message);
                    System.out.println(b.toString());
                    //>>>>>>>>>>>>>>>>>>>debug

                    chunk.addMessage(message);
                }
                
                // read all messages
                listener.chunkReceived(sender, uri, chunkStorage.getEra());
            }
            
        } catch (IOException ex) {
            // done - connection closed
            
            //<<<<<<<<<<<<<<<<<<debug
            b = new StringBuilder();
            b.append(logStart);
            b.append("connection close - done");
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug
        }
    }
}
