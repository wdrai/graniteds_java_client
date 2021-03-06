package org.granite.tide.rpc;

import java.util.Map;

import org.granite.logging.Logger;
import org.granite.rpc.events.FaultEvent;
import org.granite.tide.Context;
import org.granite.tide.TideResponder;

import flex.messaging.messages.ErrorMessage;

/**
 *  @private
 *  Implementation of fault handler
 *  
 *  @param sourceContext source context of remote call
 *  @param info fault object
 *  @param componentName component name
 *  @param op remote operation
 *  @param tideResponder Tide responder for the remote call
 */
public class FaultHandler<T> implements Runnable {
	
	private static final Logger log = Logger.getLogger(FaultHandler.class);
	
	private final ServerSession serverSession;
	private final Context sourceContext;
	private final String componentName;
	private final String operation;
	private final FaultEvent event;
	@SuppressWarnings("unused")
	private final Object info;
	private final TideResponder<T> tideResponder;
	private final ComponentResponder componentResponder;
	
	
	public FaultHandler(ServerSession serverSession, Context sourceContext, String componentName, String operation, FaultEvent event, Object info, 
			TideResponder<T> tideResponder, ComponentResponder componentResponder) {
		this.serverSession = serverSession;
		this.sourceContext = sourceContext;
		this.componentName = componentName;
		this.operation = operation;
		this.event = event;
		this.info = info;
		this.tideResponder = tideResponder;
		this.componentResponder = componentResponder;
	}

	public void run() {
        log.error("fault %s", event.toString());
        
//        var sessionId:String = faultEvent.message.headers[Tide.SESSION_ID_TAG];
//        var conversationId:String = null;
//        if (faultEvent.message.headers[Tide.IS_LONG_RUNNING_CONVERSATION_TAG])
//            conversationId = faultEvent.message.headers[Tide.CONVERSATION_TAG];
//        var wasConversationCreated:Boolean = faultEvent.message.headers[Tide.WAS_LONG_RUNNING_CONVERSATION_CREATED_TAG] != null;
//        var wasConversationEnded:Boolean = faultEvent.message.headers[Tide.WAS_LONG_RUNNING_CONVERSATION_ENDED_TAG] != null;
//        
//        var context:Context = _contextManager.retrieveContext(sourceContext, conversationId, wasConversationCreated, wasConversationEnded);
        
        Context context = sourceContext.getContextManager().retrieveContext(sourceContext, null, false, false);
        
        ErrorMessage emsg = event.getMessage();
        ErrorMessage m = emsg;
        Map<String, Object> extendedData = emsg != null ? emsg.getExtendedData() : null;
        do {
            if (m != null && m.getFaultCode() != null && m.getFaultCode().indexOf("Server.Security.") == 0) {
                emsg = m;
                extendedData = emsg != null ? emsg.getExtendedData() : null;
                break;
            }
            if (m != null && m.getRootCause() instanceof FaultEvent)
                m = (ErrorMessage)((FaultEvent)m.getRootCause()).getRootCause();
            else if (m.getRootCause() instanceof ErrorMessage)
                m = (ErrorMessage)m.getRootCause();
            else
            	m = null;
        }
        while (m != null);
        
        serverSession.fault(event, emsg);
        
        context.internalFault(componentName, operation, emsg);
        
        boolean handled = false;
        Fault fault = new Fault(emsg.getFaultCode(), emsg.getFaultString(), emsg.getFaultDetail());
        fault.setContent(event.getMessage());
        fault.setRootCause(event.getRootCause());
        
        TideFaultEvent faultEvent = new TideFaultEvent(context, event.getToken(), componentResponder, fault, extendedData);
        if (tideResponder != null) {
            tideResponder.fault(faultEvent);
            if (faultEvent.isDefaultPrevented())
                handled = true;
        }
        
        if (!handled) {
            ExceptionHandler[] exceptionHandlers = context.getContextManager().getContext(null).allByType(ExceptionHandler.class);
            if (exceptionHandlers != null && emsg != null) {
                // Lookup for a suitable exception handler
                for (ExceptionHandler handler : exceptionHandlers) {
                    if (handler.accepts(emsg)) {
                        handler.handle(context, emsg, faultEvent);
                        handled = true;
                        break;
                    }
                }
                if (!handled)
                    log.error("Unhandled fault: " + emsg.getFaultCode() + ": " + emsg.getFaultDetail());
            }
            else if (exceptionHandlers != null && exceptionHandlers.length > 0 && event.getMessage() instanceof ErrorMessage) {
                // Handle fault with default exception handler
                exceptionHandlers[0].handle(context, (ErrorMessage)event.getMessage(), faultEvent);
            }
            else {
                log.error("Unknown fault: " + event.toString());
            }
        }
        
        // TODO dispatch event
//	        if (!handled && !serverSession.isLogoutInProgress())
//	            raiseEvent(Tide.CONTEXT_FAULT, info.message);
        
        serverSession.tryLogout();
    }
}