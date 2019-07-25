package net.java.sip.communicator.plugin.portforward;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class PortForwardUtils
{
    public static final int MTU = 1300;


    private PortForwardUtils()
    {
    }

    public static void startPump(final Socket readSock, final Socket writeSock,
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


    public static void closeQuietly(Closeable resource)
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


    public static InetSocketAddress parseAddressString(final String addressString,
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

    public static InetSocketAddress getResolved(InetSocketAddress unresolved)
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

    private static boolean staticEntering(String sourceMethod, Object... params)
    {
        LOGGER.entering(PortForwardManager.class.getName(), sourceMethod, params);
        return false;
    }

    private static void staticExiting(String sourceMethod, Object res, boolean ok)
    {
        if (ok) {
            LOGGER.exiting(PortForwardManager.class.getName(), sourceMethod, res);
        } else {
            LOGGER.exiting(PortForwardManager.class.getName(), sourceMethod);
        }
    }
    private static final Logger LOGGER =
        Logger.getLogger(PortForwardUtils.class.getName());
}
