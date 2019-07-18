package net.java.sip.communicator.plugin.portforward;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

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

    private Map<String, Forward> forwardsByName = new HashMap<>();
    private Map<String, Contact> contactsByName = new HashMap<>();

    private final PseudoTcpSocketFactory pseudoTcpSocketFactory = new PseudoTcpSocketFactory();

    public class Contact {
        private Socket controlSocket;
        private DatagramSocket datagramSocket;
        private SocketAddress remoteAddress;
        private long conversationId;

        public long getNextConversationId(String forwardName) throws Exception {
            DataOutputStream out = new DataOutputStream(controlSocket.getOutputStream());
            return 0;
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
                Socket sock = ss.accept();
                boolean ok = false;
                try {
                    Contact contact = getConnectedContact(contactName);
                    long conversationID = contact.getNextConversationId(name);
                    DatagramSocket dgramSock = contact.getDatagramSocket();
                    PseudoTcpSocket socket = pseudoTcpSocketFactory.createSocket(dgramSock);
                    socket.setConversationID(conversationID);
                    socket.setMTU(1500);
                    socket.setDebugName(name + "-" + conversationID);
                    if (listen) {
                        //
                    } else {
                        SocketAddress remoteAddr = contact.getRemoteAddress();
                        socket.connect(remoteAddr, 15000);
                    }
                    ok = true;
                } catch (Exception e) {
                    continue;
                } finally {
                    if (!ok) {
                        sock.close();
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
            });
            listenThread.setName("forward-" + name);
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
