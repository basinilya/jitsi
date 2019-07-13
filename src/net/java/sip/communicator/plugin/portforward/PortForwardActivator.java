package net.java.sip.communicator.plugin.portforward;

import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.irc.*;
import net.java.sip.communicator.plugin.irccommands.command.*;
import net.java.sip.communicator.plugin.irccommands.command.Mode;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.util.ServiceUtils;

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
    public void start(final BundleContext bContext)
    {
        PortForwardActivator.bundleContext = bContext;
        MetaContactListService metaCListService = ServiceUtils.getService(
                bundleContext,
                MetaContactListService.class);
        metaCListService.getRoot();
        // basinilya@jabber.ru-2-zazaza
        Iterator<MetaContact> it = metaCListService.findAllMetaContactsForAddress("zzzz@zzz.org");
        for(;it.hasNext();) {
            MetaContact metaCon = it.next();
            System.out.println(metaCon.getDisplayName());
            Contact contact = metaCon.getDefaultContact();
            OperationSetBasicInstantMessaging imOpSet = contact.getProtocolProvider().getOperationSet(OperationSetBasicInstantMessaging.class);
            Message message = imOpSet.createMessage("hello");
            imOpSet.sendInstantMessage(contact, message);
            System.out.println(contact.getDisplayName());
            System.out.println(contact.getPresenceStatus().getStatusName());
            break;
        }
        MetaContactListListener lstn;
       // metaCListService.addMetaContactListListener(l);
    }
    public static BundleContext bundleContext;
}
