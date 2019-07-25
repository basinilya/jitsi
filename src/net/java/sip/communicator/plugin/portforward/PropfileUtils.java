package net.java.sip.communicator.plugin.portforward;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.ice4j.pseudotcp.PseudoTcpSocket;
import org.ice4j.pseudotcp.PseudoTcpSocketFactory;

public class PropfileUtils
{

    private static final String PREFIX = "portforward.";

    private static Set<String> listSubKeys(Properties props, String prefix)
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

    private void load() throws Exception
    {
        boolean ok = ts.entering("load");
        try {
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
                addForward(forward);
            }
            ok = true;
        } finally {
            ts.exiting("load", "void", ok);
        }
    }

    private void addForward(Forward forward)
    {
        boolean ok = ts.entering("addForward", forward);
        try {
            forwardsByName.put(forward.name, forward);
            forward.start();
            ok = true;
        } finally {
            ts.exiting("addForward", "void", ok);
        }
    }

    // private Forward getByContact
    // private Map<String, Forward> by
    private void contactStatusChange(String contact, boolean online)
    {
        //
    }

    private static final int MTU = 1300;

    private Map<String, Forward> forwardsByName = new HashMap<>();

    private Map<String, Contact> contactsByName = new HashMap<>();

    private final PseudoTcpSocketFactory pseudoTcpSocketFactory =
        new PseudoTcpSocketFactory();

    private static long HALF = 4000000000000000000L;

    private enum ControlCommand
    {
        CONNECT
    }

    private static class ByteBufferOutputStream
        extends OutputStream
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

    private final ExecutorService executorService =
        Executors.newCachedThreadPool();

    private class Contact
    {
        private final TraceSupport ts = new TraceSupport(this);

        private Contact(DatagramSocket datagramSocket, SocketAddress remoteAddress, boolean accept)
            throws IOException
        {
            boolean ok = ts.entering("Contact", datagramSocket, remoteAddress, accept);
            try {
                this.datagramSocket = datagramSocket;
                this.remoteAddress = remoteAddress;
                controlSocket = pseudoTcpSocketFactory.createSocket(datagramSocket);
                controlSocket.setConversationID(0L);
                controlSocket.setMTU(MTU);
                controlSocket.setDebugName("control");
                if (accept)
                {
                    controlSocket.accept(CONNECT_TIMEOUT);
                }
                else
                {
                    listenCounter = HALF;
                    controlSocket.connect(remoteAddress, CONNECT_TIMEOUT);
                }
                controlOut =
                    new BufferedOutputStream(controlSocket.getOutputStream());
                controlIn = new DataInputStream(
                    new BufferedInputStream(controlSocket.getInputStream()));
                controlThread = new Thread(new Runnable()
                {
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
                ok = true;
            } finally {
                ts.exiting("Contact", this, ok);
            }
        }

        private final DatagramSocket datagramSocket;

        private final PseudoTcpSocket controlSocket;

        private final SocketAddress remoteAddress;

        private final BufferedOutputStream controlOut;

        private final DataInputStream controlIn;

        private long listenCounter;

        private Thread controlThread;

        private void controlLoop() throws Exception
        {
            boolean ok = ts.entering("controlLoop");
            try {
                for (;;)
                {
                    int payloadLength = controlIn.readShort();
                    if (payloadLength < 1 || payloadLength > MAX_COMMAND)
                    {
                        throw new Exception("Allowed payload size between " + 1
                            + " and " + MAX_COMMAND + ", actual: " + payloadLength);
                    }
                    final byte[] payload = new byte[payloadLength];
                    controlIn.readFully(payload);
                    Thread serveThread = new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                serve(payload);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }, "serveThread");
                    serveThread.start();
                }
                // unreachable
            } finally {
                ts.exiting("controlLoop", "void", ok);
            }
        }

        private void serve(byte[] payload) throws Exception
        {
            boolean ok = ts.entering("serve", payload);
            try {
                DataInputStream msgIn =
                    new DataInputStream(new ByteArrayInputStream(payload));
                String commandName = msgIn.readUTF();
                ControlCommand command = ControlCommand.valueOf(commandName);
                switch (command)
                {
                case CONNECT:
                    serveConnect(msgIn);
                    break;
                }
                ok = true;
            } finally {
                ts.exiting("serve", "void", ok);
            }
        }

        private void serveConnect(DataInputStream msgIn) throws Exception        {
            boolean ok = ts.entering("serveConnect", msgIn);
            PseudoTcpSocket leftSock = null;
            Socket rightSock = null;
            Future<Socket> fut = null;
            try
            {
                
                String forwardName = msgIn.readUTF();
                long conversationID = msgIn.readLong();
                Forward forward = forwardsByName.get(forwardName);
                final InetSocketAddress unresolved = forward.getAddress();
                if (!forward.isListen())
                {
                    fut = executorService.submit(new Callable<Socket>()
                    {
                        @Override
                        public Socket call() throws Exception
                        {
                            boolean ok = ts.entering("connectTask-("+ unresolved +").call");
                            Socket res = null;
                            try {
                                InetSocketAddress resolved =
                                    unresolved.isUnresolved()
                                        ? new InetSocketAddress(
                                            InetAddress.getByName(
                                                unresolved.getHostName()),
                                            unresolved.getPort())
                                        : unresolved;
                                res = new Socket(resolved.getAddress(),
                                    resolved.getPort());
                                ok = true;
                                return res;
                            } finally {
                                ts. exiting("connectTask-("+ unresolved +").call", res, ok);
                            }
                        }
                    });
                }
                
                leftSock =
                    pseudoTcpSocketFactory.createSocket(datagramSocket);
                leftSock.setMTU(MTU);
                leftSock.setConversationID(conversationID);
                String debugName =
                    forwardName + "-" + conversationID + "-" + "C";
                leftSock.setDebugName(debugName);
                leftSock.connect(remoteAddress, CONNECT_TIMEOUT);
                if (!forward.isListen())
                {
                    rightSock = fut.get();
                    AtomicInteger refCount = new AtomicInteger(2);
                    startPump(leftSock, rightSock, refCount,
                        debugName + " ==>");
                    startPump(rightSock, leftSock, refCount,
                        debugName + " <==");
                    ok = true;
                }
            }
            finally
            {
                if (!ok)
                {
                    closeQuietly(leftSock);
                    if (fut != null)
                    {
                        closeQuietly(fut.get());
                    }
                }
                ts.exiting("serveConnect", "void", ok);
            }
        }

        private long getNextConversationId(String forwardName) throws Exception
        {
            boolean ok = ts.entering("getNextConversationId", forwardName);
            long res = 0;
            try {
                Objects.requireNonNull(forwardName);
                listenCounter++;
    
                ByteBuffer bb = ByteBuffer.allocate(MAX_COMMAND);
                @SuppressWarnings("resource")
                DataOutputStream message =
                    new DataOutputStream(new ByteBufferOutputStream(bb));
    
                message.writeShort(0);
                message.writeUTF(ControlCommand.CONNECT.name());
                message.writeUTF(forwardName);
                message.writeLong(listenCounter);
                int payloadLength = bb.position() - 2;
                bb.putShort(0, (short) payloadLength);
                bb.flip();
    
                byte[] arr = bb.array();
                controlOut.write(arr, 0, bb.limit());
                controlOut.flush();
    
                res = listenCounter;
                ok = true;
                return res;
            } finally {
                ts.exiting("getNextConversationId", res, ok);
            }
        }

        private DatagramSocket getDatagramSocket()
        {
            return datagramSocket;
        }
    }

    private final TraceSupport ts = new TraceSupport(this);
    
    private Contact getConnectedContact(String contactName) throws Exception
    {
        boolean ok = ts.entering("getConnectedContact", contactName);
        Contact res = null;
        try {
            res = contactsByName.get(contactName);
            ok = true;
            return res;
        } finally {
            ts.exiting("getConnectedContact", res, ok);
        }
    }

    private void start() throws Exception
    {
        boolean ok = ts.entering("start");
        try {
            load();
            ok = true;
        } finally {
            ts.exiting("start", "void", ok);
        }
    }

    private static void closeQuietly(Closeable resource)
    {
        boolean ok = staticEntering("closeQuietly", resource);
        try {
            if (resource != null)
            {
                try
                {
                    resource.close();
                }
                catch (IOException e)
                {
                }
            }
            ok = true;
        } finally {
            staticExiting("closeQuietly", "void", ok);
        }
    }

    private static void startPump(final Socket readSock, final Socket writeSock,
        final AtomicInteger refCount, String name)
    {
        boolean ok = staticEntering("startPump", readSock, writeSock, refCount, name);
        try {
            Objects.requireNonNull(readSock);
            Objects.requireNonNull(writeSock);
            Thread t = new Thread(new Runnable()
            {
    
                @Override
                public void run()
                {
                    boolean ok = staticEntering(Thread.currentThread().getName() + ".run");
                    try
                    {
                        try
                        {
                            InputStream in = readSock.getInputStream();
                            OutputStream out = writeSock.getOutputStream();
                            byte[] buf = new byte[MTU];
                            int nb;
                            while (-1 != (nb = in.read(buf)))
                            {
                                out.write(buf, 0, nb);
                                out.flush();
                            }
                        }
                        finally
                        {
                            writeSock.shutdownOutput();
                        }
                        ok = true;
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                    finally
                    {
                        if (refCount.decrementAndGet() == 0)
                        {
                            closeQuietly(readSock);
                            closeQuietly(writeSock);
                        }
                        staticExiting(Thread.currentThread().getName() + ".run", "void", ok);
                    }
                }
            }, name);
            t.start();
            ok = true;
        } finally {
            staticExiting("startPump", "void", ok);
        }
    }

    private class Forward
    {
        private final String name;

        private InetSocketAddress address;

        private String contactName;

        private Thread listenThread;

        private void acceptLoop() throws Exception
        {
            boolean ok = ts.entering("acceptLoop");
            ServerSocket ss = null;
            try
            {
                InetSocketAddress resolved = getResolved(address);
                ss = new ServerSocket(resolved.getPort(), 10, resolved.getAddress());
                for (;;)
                {
                    Socket leftSock = ss.accept();
                    serve(leftSock);
                }
                // unreachable
            }
            finally
            {
                closeQuietly(ss);
                ts.exiting("acceptLoop", "void", ok);
            }
        }

        private void serve(Socket leftSock)
        {
            boolean ok = ts.entering("serve", leftSock);
            PseudoTcpSocket rightSock = null;
            try
            {
                Contact contact = getConnectedContact(contactName);
                DatagramSocket dgramSock = contact.getDatagramSocket();
                rightSock =
                    pseudoTcpSocketFactory.createSocket(dgramSock);
                rightSock.setMTU(MTU);
                long conversationID =
                    contact.getNextConversationId(name);
                rightSock.setConversationID(conversationID);
                String debugName =
                    name + "-" + conversationID + "-" + "A";
                rightSock.setDebugName(debugName);
                rightSock.accept(CONNECT_TIMEOUT);
                AtomicInteger refCount = new AtomicInteger(2);
                startPump(leftSock, rightSock, refCount,
                    "==> " + debugName);
                startPump(rightSock, leftSock, refCount,
                    "<== " + debugName);
                ok = true;
            }
            catch (Exception e)
            {
                return;
            }
            finally
            {
                if (!ok)
                {
                    closeQuietly(leftSock);
                    closeQuietly(rightSock);
                }
                ts.exiting("serve", "void", ok);
            }
        }

        private void start()
        {
            boolean ok = ts.entering("start");
            try {
                if (!listen)
                {
                    return;
                }
                listenThread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        boolean ok = ts.entering(Thread.currentThread().getName() + ".run");
                        try
                        {
                            acceptLoop();
                            ok = true;
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        } finally {
                            ts.exiting(Thread.currentThread().getName() + ".run", "void", ok);
                        }
                    }
                }, "listenThread-" + name);
                listenThread.start();
                ok = true;
            } finally {
                ts.exiting("start", "void", ok);
            }
        }

        private final TraceSupport ts = new TraceSupport(this);
        
        private Forward(String name)
            throws Exception
        {
            boolean ok = ts.entering("Forward", name);
            try {
                this.name = Objects.requireNonNull(name);
                parseAddress();
                parseContact();
                parseListen();
                ok = true;
            } finally {
                ts.exiting("Forward", this, ok);
            }
        }

        private String getContactName()
        {
            return contactName;
        }

        private void parseContact()
        {
            contactName = getProp("contact");
            Objects.requireNonNull(contactName);
        }

        private InetSocketAddress getAddress()
        {
            return address;
        }

        private void parseAddress() throws URISyntaxException
        {
            address = parseAddress0(getProp("address"), 0);
        }

        /**
         * @return should we listen, default true
         */
        private boolean isListen()
        {
            return listen;
        }

        private void parseListen()
        {
            listen = !Boolean.FALSE.equals(getBoolean("listen"));
        }

        private boolean listen;

        /**
         * 
         * @param prop
         * @return true or false if and only if the string value is exactly
         *         "true" or "false", otherwise null
         */
        private Boolean getBoolean(String prop)
        {
            String s = getProp(prop);
            return Boolean.toString(false).equalsIgnoreCase(s) ? Boolean.FALSE
                : Boolean.toString(true).equalsIgnoreCase(s) ? Boolean.TRUE
                    : null;
        }

        private String getProp(String prop)
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

    private static InetSocketAddress parseAddress0(final String addressString,
        final int defaultPort /**/)
        throws URISyntaxException
    {
        boolean ok = staticEntering("parseAddress0", addressString, defaultPort);
        InetSocketAddress res = null;
        try {
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
    
            res = InetSocketAddress.createUnresolved(host, port);
            ok = true;
            return res;
        } finally {
            staticExiting("parseAddress0", res, ok);
        }
    }

    private static InetSocketAddress getResolved(InetSocketAddress unresolved)
        throws UnknownHostException
    {
        boolean ok = staticEntering("getResolved", unresolved);
        InetSocketAddress res = null;
        try {
            res =
                unresolved.isUnresolved()
                    ? new InetSocketAddress(
                        InetAddress.getByName(unresolved.getHostName()),
                        unresolved.getPort())
                    : unresolved;
            ok = true;
            return res;
        } finally {
            staticExiting("getResolved", res, ok);
        }
    }

    
    private static class TraceSupport {
        private final Object self;

        private TraceSupport(Object self) {
            this.self = self;
        }

        private boolean entering(String sourceMethod, Object... params)
        {
            LOGGER.entering(self.getClass().getName(), sourceMethod, prepend(params, self));
            return false;
        }

        private void exiting(String sourceMethod, Object res, boolean ok)
        {
            if (ok) {
                LOGGER.exiting(self.getClass().getName(), sourceMethod, res);
            } else {
                LOGGER.exiting(self.getClass().getName(), sourceMethod);
            }
        }

    }
    
    private static boolean staticEntering(String sourceMethod, Object... params)
    {
        LOGGER.entering(PropfileUtils.class.getName(), sourceMethod, params);
        return false;
    }

    private static void staticExiting(String sourceMethod, Object res, boolean ok)
    {
        if (ok) {
            LOGGER.exiting(PropfileUtils.class.getName(), sourceMethod, res);
        } else {
            LOGGER.exiting(PropfileUtils.class.getName(), sourceMethod);
        }
    }

    public static Object[] prepend(Object[] a, Object b)
    {
        if (a == null)
        {
            return new Object[]{ b };
        }

        int length = a.length;
        Object[] result = new Object[length + 1];
        System.arraycopy(a, 0, result, 1, length);
        result[0] = b;
        return result;
    }

    private static final Logger LOGGER =
        Logger.getLogger(PropfileUtils.class.getName());
}
