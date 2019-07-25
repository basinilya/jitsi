package net.java.sip.communicator.plugin.portforward;

import static net.java.sip.communicator.plugin.portforward.PortForwardUtils.parseAddressString;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Properties;

public class ForwardConfig
{

    private final String name;

    private InetSocketAddress address;

    private String contactName;

    private boolean listen;

    public ForwardConfig(Properties props, String name)
        throws Exception
    {
        this.name = Objects.requireNonNull(name);
        Builder builder = new Builder();
        builder.props = Objects.requireNonNull(props);
        builder.build();
    }

    public String getName() {
        return name;
    }

    public String getContactName()
    {
        return contactName;
    }

    public InetSocketAddress getAddress()
    {
        return address;
    }

    /**
     * @return should we listen, default true
     */
    public boolean isListen()
    {
        return listen;
    }

    private class Builder
    {
        private Properties props;

        private void build() throws Exception
        {
            parseAddress();
            parseContact();
            parseListen();
        }

        private void parseListen()
        {
            listen = !Boolean.FALSE.equals(getBoolean("listen"));
        }

        private void parseContact()
        {
            contactName = getProp("contact");
            Objects.requireNonNull(contactName);
        }

        private void parseAddress() throws URISyntaxException
        {
            address = parseAddressString(getProp("address"), 0);
        }

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
            return props
                .getProperty(PortForwardUtils.PREFIX + name + "." + prop);
        }
    }

}
