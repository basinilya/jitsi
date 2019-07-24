package net.java.sip.communicator.plugin.portforward;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.ice4j.pseudotcp.PseudoTcpSocket;
import org.ice4j.pseudotcp.PseudoTcpSocketFactory;

public class PropfileUtils
{

    private static final String PREFIX = "portforward.";

    public static Set<String> listSubKeys(Properties props, String prefix)
    {
        Set<String> res = new HashSet<>();
        for (String x : props.stringPropertyNames())
        {
            if (x.startsWith(prefix))
            {
                String[] a = x.substring(prefix.length()).split("[.]", 2);
                String key = a[0];
                res.add(key);
            }
        }
        return res;
    }

    private Properties props;

    public void load() throws Exception
    {
        props = new Properties();
        try (FileInputStream in = new FileInputStream(
            "C:/progs/media/jitsi/lib/portforward.properties"))
        {
            props.load(in);
        }
        Set<String> forwardNames = listSubKeys(props, PREFIX);
        for (String name : forwardNames)
        {
            Forward forward = new Forward(name);
            System.out.println(name);
            System.out.println(forward.getContactName());
            System.out.println(forward.getAddress());
            System.out.println(forward.isListen());
        }
    }

    public void addForward(Forward forward) {
        forwardsByName.put(forward.name, forward);
        forward.start();
    }

    //public Forward getByContact
    //private Map<String, Forward> by
    public void contactStatusChange(String contact, boolean online) {
        //
    }

    private static final int MTU = 1300;

    private Map<String, Forward> forwardsByName = new HashMap<>();
    private Map<String, Contact> contactsByName = new HashMap<>();

    private final PseudoTcpSocketFactory pseudoTcpSocketFactory = new PseudoTcpSocketFactory();

    private static long HALF = 4000000000000000000L;

    public enum ControlCommand {
        CONNECT
    }

    private static class ByteBufferOutputStream extends OutputStream
    {
        private final ByteBuffer buffer;

        ByteBufferOutputStream(ByteBuffer buffer)
        {
            this.buffer = buffer;
        }

        public void write(int b) throws IOException
        {
            buffer.put((byte) b);
        }
    }

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int MAX_COMMAND = 1000;

    public class Contact {

        public Contact(DatagramSocket datagramSocket, SocketAddress remoteAddress, boolean accept) throws IOException {
            this.datagramSocket = datagramSocket;
            this.remoteAddress = remoteAddress;
            controlSocket = pseudoTcpSocketFactory.createSocket(datagramSocket);
            controlSocket.setConversationID(0L);
            controlSocket.setMTU(MTU);
            controlSocket.setDebugName("control");
            if (accept) {
                controlSocket.accept(CONNECT_TIMEOUT);
            } else {
                listenCounter = HALF;
                controlSocket.connect(remoteAddress, CONNECT_TIMEOUT);
            }
            controlOut = new BufferedOutputStream(controlSocket.getOutputStream());
            controlIn = new DataInputStream(new BufferedInputStream(controlSocket.getInputStream()));
            controlThread = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    try
                    {
                        controlLoop();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }, "controlThread-" + remoteAddress.toString());
            controlThread.start();
        }
        private final DatagramSocket datagramSocket;
        private final PseudoTcpSocket controlSocket;
        private final SocketAddress remoteAddress;
        private final BufferedOutputStream controlOut;
        private final DataInputStream controlIn;
        private long listenCounter;
        private Thread controlThread;

        private void controlLoop() throws Exception {
            for(;;) {
                int payloadLength = controlIn.readShort();
                if (payloadLength < 1 || payloadLength > MAX_COMMAND) {
                    throw new Exception("Allowed payload size between " + 1 + " and "
                        + MAX_COMMAND + ", actual: " + payloadLength);
                }
                byte[] payload = new byte[payloadLength];
                controlIn.readFully(payload);
                DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(payload));
                String commandName = msgIn.readUTF();
                ControlCommand command = ControlCommand.valueOf(commandName);
                switch (command) {
                case CONNECT:
                    {
                        String forwardName = msgIn.readUTF();
                        long conversationID = msgIn.readLong();
                        Forward forward = forwardsByName.get(forwardName);
                        PseudoTcpSocket rightSock = pseudoTcpSocketFactory.createSocket(datagramSocket);
                        rightSock.setMTU(MTU);
                        rightSock.setConversationID(conversationID);
                        String debugName = forwardName + "-" + conversationID + "-" + "C";
                        rightSock.setDebugName(debugName);
                        rightSock.connect(remoteAddress, CONNECT_TIMEOUT);
                        if (forward.isListen()) {
                            rightSock.close();
                        } else {
                            InetSocketAddress unresolved = forward.getAddress();
                            InetSocketAddress resolved =
                                unresolved.isUnresolved()
                                    ? new InetSocketAddress(InetAddress.getByName(unresolved.getHostName()), unresolved.getPort())
                                    : unresolved;
                            Socket leftSock = new Socket(resolved.getAddress(), resolved.getPort());
                            AtomicInteger refCount = new AtomicInteger(2);
                            startPump(leftSock, rightSock, refCount, "==> " + debugName);
                            startPump(rightSock, leftSock, refCount, "<== " + debugName);
                           // ok = true;                            
                        }
                    }
                    break;
                }
            }
        }

        public long getNextConversationId(String forwardName) throws Exception {
            Objects.requireNonNull(forwardName);
            listenCounter++;

            ByteBuffer bb = ByteBuffer.allocate(MAX_COMMAND);
            @SuppressWarnings("resource")
            DataOutputStream message = new DataOutputStream(new ByteBufferOutputStream(bb));

            message.writeShort(0);
            message.writeUTF(ControlCommand.CONNECT.name());
            message.writeUTF(forwardName);
            message.writeLong(listenCounter);
            int payloadLength = bb.position() - 2;
            bb.putShort(0, (short)payloadLength);
            bb.flip();

            byte[] arr = bb.array();
            controlOut.write(arr, 0, bb.limit());
            controlOut.flush();

            return listenCounter;
        }
        public DatagramSocket getDatagramSocket()
        {
            return datagramSocket;
        }
        public SocketAddress getRemoteAddress()
        {
            return remoteAddress;
        }
    }
    
    public Contact getConnectedContact(String contactName) throws Exception {
        Contact contact = contactsByName.get(contactName);
        return contact;
    }
    
    public void start() throws Exception
    {
        load();
    }

    public static void closeQuietly(Closeable resource) {
        if (resource != null) {
        try
        {
            resource.close();
        }
        catch (IOException e)
        {
        }
        }
    }
    

    public static void startPump(final Socket readSock, final Socket writeSock, final AtomicInteger refCount, String name) {
        Objects.requireNonNull(readSock);
        Objects.requireNonNull(writeSock);
        Thread t = new Thread(new Runnable() {

            @Override
            public void run()
            {
                try
                {
                    try {
                        InputStream in = readSock.getInputStream();
                        OutputStream out = writeSock.getOutputStream();
                        byte[] buf = new byte[MTU];
                        int nb;
                        while (-1 != (nb = in.read(buf))) {
                            out.write(buf, 0, nb);
                            out.flush();
                        }
                    } finally {
                        writeSock.shutdownOutput();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                } finally {
                    if (refCount.decrementAndGet() == 0) {
                        closeQuietly(readSock);
                        closeQuietly(writeSock);
                    }
                }
            }                
        }, name);
        t.start();
    }

    public class Forward
    {
        private final String name;
        private InetSocketAddress address;
        private String contactName;

        private Thread listenThread; 

        private void acceptLoop() throws Exception {
            InetSocketAddress resolved = getResolved(address);
            ServerSocket ss = new ServerSocket(resolved.getPort(), 10, resolved.getAddress());
            for (;;) {
                Socket leftSock = ss.accept();
                PseudoTcpSocket rightSock = null;
                boolean ok = false;
                try {
                    Contact contact = getConnectedContact(contactName);
                    DatagramSocket dgramSock = contact.getDatagramSocket();
                    rightSock = pseudoTcpSocketFactory.createSocket(dgramSock);
                    rightSock.setMTU(MTU);
                    long conversationID = contact.getNextConversationId(name);
                    rightSock.setConversationID(conversationID);
                    String debugName = name + "-" + conversationID + "-" + "A";
                    rightSock.setDebugName(debugName);
                    rightSock.accept(CONNECT_TIMEOUT);
                    AtomicInteger refCount = new AtomicInteger(2);
                    startPump(leftSock, rightSock, refCount, "==> " + debugName);
                    startPump(rightSock, leftSock, refCount, "<== " + debugName);
                    ok = true;
                } catch (Exception e) {
                    continue;
                } finally {
                    if (!ok) {
                        closeQuietly(leftSock);
                        closeQuietly(rightSock);
                    }
                }
            }
        }
        
        public void start() {
            if (!listen) {
                return;
            }
            listenThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        acceptLoop();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }, "forward-" + name);
            listenThread.start();
        }
        
        public Forward(String name) throws Exception
        {
            this.name = Objects.requireNonNull(name);
            parseAddress();
            parseContact();
            parseListen();
        }

        public String getContactName()
        {
            return contactName;
        }

        private void parseContact() {
            contactName = getProp("contact");
            Objects.requireNonNull(contactName);
        }

        public InetSocketAddress getAddress()
        {
            return address;
        }

        private void parseAddress() throws URISyntaxException {
            address = parseAddress0(getProp("address"), 0);
        }

        /**
         * @return should we listen, default true
         */
        public boolean isListen()
        {
            return listen;
        }

        private void parseListen() {
            listen = !Boolean.FALSE.equals(getBoolean("listen"));
        }

        private boolean listen;

        /**
         * 
         * @param prop
         * @return true or false if and only if the string value is exactly
         *         "true" or "false", otherwise null
         */
        public Boolean getBoolean(String prop)
        {
            String s = getProp(prop);
            return Boolean.toString(false).equalsIgnoreCase(s) ? Boolean.FALSE
                : Boolean.toString(true).equalsIgnoreCase(s) ? Boolean.TRUE : null;
        }

        public String getProp(String prop)
        {
            return props.getProperty(PREFIX + name + "." + prop);
        }

    }

    public static void main(String[] args) throws Exception
    {
        PropfileUtils inst = new PropfileUtils();
        inst.start();
        System.exit(0);
        InetSocketAddress xxx = parseAddress0("[::]", 0);
        System.out.println(xxx.getHostName());
        System.out.println(xxx.getPort());
        new InetSocketAddress(xxx.getHostName(), xxx.getPort());
        // props.prop
    }

    public static InetSocketAddress parseAddress0(final String addressString,
        final int defaultPort /**/)
        throws URISyntaxException
    {
        final URI uri = new URI("my://" + addressString);

        final String host = uri.getHost();
        int port = uri.getPort();

        if (port == -1)
        {
            port = defaultPort;
        }

        if (host == null || port == -1)
        {
            throw new URISyntaxException(uri.toString(),
                "must have host or no default port specified");
        }

        return InetSocketAddress.createUnresolved(host, port);
    }

    
    public static InetSocketAddress getResolved(InetSocketAddress unresolved) throws UnknownHostException {
        return unresolved.isUnresolved()
            ? new InetSocketAddress(InetAddress.getByName(unresolved.getHostName()), unresolved.getPort())
            : unresolved;
    }
}
