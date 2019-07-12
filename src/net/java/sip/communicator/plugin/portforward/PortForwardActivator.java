package net.java.sip.communicator.plugin.portforward;

import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.irc.*;
import net.java.sip.communicator.plugin.irccommands.command.*;
import net.java.sip.communicator.plugin.irccommands.command.Mode;

import org.osgi.framework.*;

/**
 * Activator of the Port Forward plugin.
 *
 * @author basin
 */
public class PortForwardActivator
    implements BundleActivator
{

    /**
     * Stopping the bundle.
     *
     * @param context the bundle context
     */
    @Override
    public void stop(final BundleContext context)
    {
    }

    /**
     * Starting the bundle.
     *
     * @param context the bundle context
     */
    @Override
    public void start(final BundleContext context)
    {
    }
}
