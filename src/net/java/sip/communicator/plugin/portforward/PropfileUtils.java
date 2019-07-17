package net.java.sip.communicator.plugin.portforward;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

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
            forwards.put(name, forward);
            System.out.println(name);
            System.out.println(forward.getContact());
            System.out.println(forward.getAddress());
            System.out.println(forward.isListen());
        }
    }

    private Map<String, Forward> forwards = new HashMap<>();

    public void start() throws Exception
    {
        load();
    }

    public class Forward
    {
        private final String name;

        public Forward(String name) throws Exception
        {
            this.name = name;
            getAddress();
            Objects.requireNonNull(getContact());
        }

        public String getContact()
        {
            return getProp("contact");
        }

        public InetSocketAddress getAddress() throws URISyntaxException
        {
            return parseAddress0(getProp("address"), 0);
        }

        /**
         * @return should we listen, default true
         */
        public boolean isListen()
        {
            return !Boolean.FALSE.equals(getBoolean("listen"));
        }

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

}
