package net.java.sip.communicator.plugin.portforward;

import java.util.logging.Logger;

public class TraceSupport {

    private final Object self;
    private final Logger logger;

    public TraceSupport(Object self, Logger logger) {
        this.self = self;
        this.logger = logger;
    }

    public boolean entering(String sourceMethod, Object... params)
    {
        logger.entering(self.getClass().getName(), sourceMethod, PortForwardUtils.prepend(params, self));
        return false;
    }

    public void exiting(String sourceMethod, Object res, boolean ok)
    {
        if (ok) {
            logger.exiting(self.getClass().getName(), sourceMethod, res);
        } else {
            logger.exiting(self.getClass().getName(), sourceMethod);
        }
    }

}
