package org.granite.tide.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.granite.tide.InstanceStore;
import org.granite.tide.Context;
import org.granite.tide.ContextManager;
import org.granite.tide.InstanceStoreFactory;
import org.granite.tide.Platform;


public class ContextManagerImpl implements ContextManager {
    
    static final String DEFAULT_CONTEXT = "__DEFAULT__CONTEXT__";
    
    protected final Platform platform;
    private InstanceStoreFactory instanceStoreFactory = new DefaultInstanceStoreFactory();
    private Map<String, Context> contextsById = new HashMap<String, Context>();
    private List<String> contextsToDestroy = new ArrayList<String>();
    
    
    public ContextManagerImpl(Platform platform) {
    	this.platform = platform;
    }
    
    public void setInstanceStoreFactory(InstanceStoreFactory instanceStoreFactory) {
    	this.instanceStoreFactory = instanceStoreFactory;
    }
    
    public static class DefaultInstanceStoreFactory implements InstanceStoreFactory {
		@Override
		public InstanceStore createStore(Context context) {
			return new SimpleInstanceStore(context);
		}    	
    }
    
    
    /**
     *  Determine if the specified context is the global one
     *  
     *  @param context
     *  @return true if global
     */
    public boolean isGlobal(Context context) {
        return contextsById.get(DEFAULT_CONTEXT) == context;
    }
    
    /**
     *  Return a context from its id
     *  
     *  @param contextId context id
     *  @return context
     */ 
    public Context getContext(String contextId) {
        return getContext(contextId, null, true);
    }
    
    
    protected Context createContext(Context parentCtx, String contextId) {
        Context ctx = new Context(this, parentCtx, contextId);
        ctx.setPlatform(platform);
        ctx.setInstanceStore(instanceStoreFactory.createStore(ctx));
        return ctx;
    }
    
    /**
     *  Return a context from its id
     *  
     *  @param contextId context id
     *  @param create should create when not existing
     *  @return context
     */ 
    public Context getContext(String contextId, String parentContextId, boolean create) {
        Context ctx = contextsById.get(contextId != null ? contextId : DEFAULT_CONTEXT);
        if (ctx == null && create) {
            Context parentCtx = contextsById.get(parentContextId == null ? DEFAULT_CONTEXT : parentContextId);
            if (parentContextId != null && parentCtx == null)
                throw new IllegalStateException("Parent context not found for id " + parentContextId);
            
            ctx = createContext(parentCtx, contextId);
            contextsById.put(contextId != null ? contextId : DEFAULT_CONTEXT, ctx);
            // TODO
//            if (contextId != null)
//                ctx.raiseEvent(Tide.CONTEXT_CREATE);
            ctx.postInit();
        }
        return ctx;
    }

    /**
     *  @private
     *  Create a new context if it does not exist
     * 
     *  @param contextId the requested context id
     *  @return the context
     */
    public Context newContext(String contextId, String parentContextId) {
        Context ctx = contextsById.get(contextId != null ? contextId : DEFAULT_CONTEXT);
        if (ctx != null && ctx.isFinished()) {
            ctx.clear(false);
            contextsById.remove(contextId);
            removeFromContextsToDestroy(contextId);
            ctx = null;
        }
        if (ctx == null) {
            Context parentCtx = contextsById.get(parentContextId != null ? parentContextId : DEFAULT_CONTEXT);
            ctx = createContext(parentCtx, contextId);
            if (contextId != null)
                contextsById.put(contextId, ctx);
//            ctx.raiseEvent(Tide.CONTEXT_CREATE);
            ctx.postInit();
        }
        return ctx;
    }
    
    /**
     *  @private
     *  Destroy a context
     * 
     *  @param contextId context id
     *  @param force force complete destruction of context
     */
    public void destroyContext(String contextId, boolean force) {
        Context ctx = contextId != null ? contextsById.get(contextId) : null;
        if (ctx != null) {
            // Destroy child contexts
            for (Context c : contextsById.values()) {
                if (c.getParentContext() == ctx)
                    destroyContext(c.getContextId(), force);
            }
            
            removeFromContextsToDestroy(contextId);
//            ctx.raiseEvent(Tide.CONTEXT_DESTROY);
            contextsById.get(contextId).clear(force);
            contextsById.remove(contextId);
        }
    }     
    
    /**
     *  Returns the list of conversation contexts
     * 
     *  @return conversation contexts
     */
    public List<Context> getAllContexts() {
        List<Context> contexts = new ArrayList<Context>();
        for (Entry<String, Context> ectx : contextsById.entrySet()) {
            if (!ectx.getKey().equals(DEFAULT_CONTEXT))
                contexts.add(ectx.getValue());
        }
        return contexts;
    }       
    
//    /**
//     *  Execute a function for each conversation context
//     * 
//     *  @param parentContext parent context
//     *  @param callback callback function
//     *  @param token token passed to the function
//     */
//    public function forEachChildContext(parentContext:Context, callback:Function, token:Object = null):void {
//        for each (var ctx:Context in _ctx) {
//            if (ctx.meta_parentContext === parentContext) {
//                if (token)
//                    callback(ctx, token);
//                else
//                    callback(ctx);
//            }
//        }
//    }       
    
    /**
     *  @private
     *  Destroy all contexts
     * 
     *  @param force force complete destruction of contexts (all event listeners...), used for testing
     */
    public void destroyContexts(boolean force) {
        contextsToDestroy.clear();
        
        Context globalCtx = contextsById.get(DEFAULT_CONTEXT);
        for (Entry<String, Context> ectx : contextsById.entrySet()) {
            if (!ectx.getKey().equals(DEFAULT_CONTEXT) && ectx.getValue().getParentContext() == globalCtx)
                destroyContext(ectx.getKey(), force);
        }
        globalCtx.clear(force);
    }
    
    /**
     *  @private
     *  Destroy finished contexts and reset current pending contexts
     */
    public void destroyFinishedContexts() {
        for (String contextId : contextsToDestroy)
            destroyContext(contextId, false);
        contextsToDestroy.clear();
    }
    
    
    /**
     *  @private
     *  Remove context from the list of contexts to destroy
     *  
     *  @param contextId context id
     */
    public void removeFromContextsToDestroy(String contextId) {
        int idx = contextsToDestroy.indexOf(contextId);
        if (idx >= 0)
            contextsToDestroy.remove(idx);
    }
    
    /**
     *  @private
     *  Add context to the list of contexts to destroy
     *  
     *  @param contextId context id
     */
    public void addToContextsToDestroy(String contextId) {
        if (contextsToDestroy.contains(contextId))
            return;
        contextsToDestroy.add(contextId);
    }
    
    
    public Context retrieveContext(Context sourceContext, String contextId, boolean wasConversationCreated, boolean wasConversationEnded) {
        Context context = null;
        if (!isGlobal(sourceContext) && contextId == null && wasConversationEnded) {
            // The conversation of the source context was ended
            // Get results in the current conversation when finished
            context = sourceContext;
            context.markAsFinished();
        }
        else if (!isGlobal(sourceContext) && contextId == null && !sourceContext.isContextIdFromServer()) {
            // A call to a non conversational component was issued from a conversation context
            // Get results in the current conversation
            context = sourceContext;
        }
        else if (!isGlobal(sourceContext) && contextId != null
            && (sourceContext.getContextId() == null || (!sourceContext.getContextId().equals(contextId) && !wasConversationCreated))) {
            // The conversationId has been updated by the server
            String previousContextId = sourceContext.getContextId();
            context = sourceContext;
            context.setContextId(contextId, true);
            updateContextId(previousContextId, context);
        }
        else {
            context = getContext(contextId);
            if (contextId != null)
                context.setContextId(contextId, true);
        }
        
        return context;
    }
    
    /**
     *  @private
     *  
     *  Destroys component instances in all contexts
     * 
     *  @param name component name
     */
    // TODO
//    public void destroyComponentInstances(String name) {
//        for (Context ctx : contextsById.values())
//            ctx.destroy(name, true);
//    }
    
    /**
     *  @private
     * 
     *  Defines new context for existing id
     * 
     *  @param previousContextId existing id
     *  @param context new context
     */
    public void updateContextId(String previousContextId, Context context) {
        if (previousContextId != null)
            contextsById.remove(previousContextId);
        contextsById.put(context.getContextId(), context);
    }

}
