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
import java.util.Properties;
import java.util.Set;

public class PropfileUtils
{

    private static final String PREFIX = "portforward.";

    public static Set<String> listSubKeys(Properties props, String prefix) {
        Set<String> res = new HashSet<>();
        for (String x : props.stringPropertyNames()) {
            if (x.startsWith(prefix)) {
                String[] a = x.substring(prefix.length()).split("[.]", 2);
                String key = a[0];
                res.add(key);
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception
    {
        Properties props = new Properties();
        props.load(new FileInputStream("C:/progs/media/jitsi/lib/portforward.properties"));
        Map<String, String> map = new HashMap<>();
        Set<String> subKeys = listSubKeys(props, PREFIX);
        for (String subKey : subKeys) {
            System.out.println(subKey);
            System.out.println(props.getProperty(PREFIX + subKey + ".contact"));
            System.out.println(props.getProperty(PREFIX + subKey + ".address"));
            System.out.println(props.getProperty(PREFIX + subKey + ".listen"));
        }
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
