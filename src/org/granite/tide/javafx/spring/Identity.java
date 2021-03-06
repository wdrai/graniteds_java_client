package org.granite.tide.javafx.spring;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Future;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import org.granite.messaging.amf.RemoteClass;
import org.granite.tide.Context;
import org.granite.tide.TideResponder;
import org.granite.tide.impl.ComponentImpl;
import org.granite.tide.rpc.ExceptionHandler;
import org.granite.tide.rpc.ServerSession;
import org.granite.tide.rpc.SimpleTideResponder;
import org.granite.tide.rpc.TideFaultEvent;
import org.granite.tide.rpc.TideResultEvent;
import org.granite.util.WeakIdentityHashMap;

import flex.messaging.messages.ErrorMessage;


@RemoteClass("org.granite.tide.spring.security.Identity")
public class Identity extends ComponentImpl implements ExceptionHandler {
	
	private BooleanProperty loggedIn = new SimpleBooleanProperty(this, "loggedIn");
	private StringProperty username = new ReadOnlyStringWrapper(this, "username", null);
	

	public BooleanProperty loggedInProperty() {
		return loggedIn;
	}
	
	public boolean isLoggedIn() {
		return loggedIn.get();
	}
	
	public void setLoggedIn(boolean loggedIn) {
		this.loggedIn.set(loggedIn);
	}
	
	public StringProperty usernameProperty() {
		return username;
	}
	
	public String getUsername() {
		return username.get();
	}
	
	
    public Identity(final ServerSession serverSession) {
    	super(serverSession);
    	
        this.loggedIn.set(false);
        this.loggedIn.addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> property, Boolean oldValue, Boolean newValue) {
				if (Boolean.TRUE.equals(newValue)) {
					initSecurityCache();
					serverSession.afterLogin();
				}
			}        	
        });
    }
    
    /**
     * 	Triggers a remote call to check is user is currently logged in
     *  Can be used at application startup to handle browser refresh cases
     * 
     *  @param resultHandler optional result handler
     *  @param faultHandler optional fault handler 
     */
    public Future<String> checkLoggedIn(final TideResponder<String> tideResponder) {
    	return super.call("isLoggedIn", new SimpleTideResponder<String>() {
			@Override
			public void result(TideResultEvent<String> event) {
				if (event.getResult() != null) {
					Identity.this.username.set(event.getResult());
					Identity.this.loggedIn.set(true);
				}
				
				tideResponder.result(event);
			}
			
			@Override
			public void fault(TideFaultEvent event) {
				Identity.this.username.set(null);
				Identity.this.loggedIn.set(false);
				
				tideResponder.fault(event);
			}
    	});
    }
    
    
    public void login(final String username, String password, final TideResponder<String> tideResponder) {
    	getServerSession().login(username, password);
    	
    	checkLoggedIn(tideResponder);
    }
    
    
    public void logout(final TideResponder<Void> tideResponder) {
    	final Observer observer = new Observer() {
			@SuppressWarnings("unchecked")
			@Override
			public void update(Observable logout, Object event) {
		        Identity.this.loggedIn.set(false);
		        Identity.this.username.set(null);
		        
		        clearSecurityCache();
		        
		        if (tideResponder != null) {
					if (event instanceof TideResultEvent)
				        tideResponder.result((TideResultEvent<Void>)event);
					else if (event instanceof TideFaultEvent)
				        tideResponder.fault((TideFaultEvent)event);
		        }
			}
    	};
    	
    	getServerSession().logout(observer);
    }
        
    
    private Map<String, ObservableRole> ifAllGrantedCache = new HashMap<String, ObservableRole>();
    private Map<String, ObservableRole> ifAnyGrantedCache = new HashMap<String, ObservableRole>();
    private Map<String, ObservableRole> ifNotGrantedCache = new HashMap<String, ObservableRole>();
    
    
    public ObservableRole ifAllGranted(String roleName) {
    	ObservableRole role = ifAllGrantedCache.get(roleName);
    	if (role == null) {
    		role = new ObservableRole(this, "ifAllGranted", roleName);
    		ifAllGrantedCache.put(roleName, role);
    	}
    	return role;
    }
    
    public ObservableRole ifAnyGranted(String roleName) {
    	ObservableRole role = ifAnyGrantedCache.get(roleName);
    	if (role == null) {
    		role = new ObservableRole(this, "ifAnyGranted", roleName);
    		ifAnyGrantedCache.put(roleName, role);
    	}
    	return role;
    }
    
    public ObservableRole ifNotGranted(String roleName) {
    	ObservableRole role = ifNotGrantedCache.get(roleName);
    	if (role == null) {
    		role = new ObservableRole(this, "ifNotGranted", roleName);
    		ifNotGrantedCache.put(roleName, role);
    	}
    	return role;
    }

    public class ObservableRole extends ReadOnlyBooleanPropertyBase {
    	
    	private final Object bean;
    	private final String name;
    	private final String roleName;
    	
    	private Boolean hasRole = null;
    	
    	
    	public ObservableRole(Object bean, String name, String roleName) {
    		super();
    		this.bean = bean;
    		this.name = name;
    		this.roleName = roleName;
    	}

		@Override
		public Object getBean() {
			return bean;
		}

		@Override
		public String getName() {
			return name + "." + roleName;
		}

		@Override
		public boolean get() {
			if (hasRole == null) {
				if (isLoggedIn())
					getFromRemote(null);
				return false;
	    	}
			return hasRole;
	    }
		
		public boolean get(TideResponder<Boolean> tideResponder) {
			if (hasRole != null) {
		    	if (tideResponder != null) {
		    		TideResultEvent<Boolean> event = new TideResultEvent<Boolean>(getContext(), null, null, hasRole);
		    		tideResponder.result(event);
		    	}
		    	return hasRole;
			}
			if (isLoggedIn())
				getFromRemote(tideResponder);
			return false;
		}    
		
		public void getFromRemote(final TideResponder<Boolean> tideResponder) {
			Identity.this.call(name, roleName, new SimpleTideResponder<Boolean>() {
				@Override
				public void result(TideResultEvent<Boolean> event) {
					hasRole = event.getResult();
					fireValueChangedEvent();
				}
				
				@Override
				public void fault(TideFaultEvent event) {
					clear();
				}
			});
		}
		
		void clear() {
			hasRole = null;
			fireValueChangedEvent();
		}
    }
    
    
    private Map<Object, Map<String, ObservablePermission>> permissionsCache = new WeakIdentityHashMap<Object, Map<String, ObservablePermission>>();
    
    public ObservablePermission hasPermission(Object entity, String action) {
    	Map<String, ObservablePermission> entityPermissions = permissionsCache.get(entity);
    	if (entityPermissions == null) {
    		entityPermissions = new HashMap<String, ObservablePermission>();
    		permissionsCache.put(entity, entityPermissions);
    	}
    	ObservablePermission permission = entityPermissions.get(action);
    	if (permission == null) {
    		permission = new ObservablePermission(this, "hasPermission", entity, action);
    		entityPermissions.put(action, permission);
    	}
    	return permission;
    }

    public class ObservablePermission extends ReadOnlyBooleanPropertyBase {
    	
    	private final Object bean;
    	private final String name;
    	private final Object entity;
    	private final String action;
    	
    	private Boolean hasPermission = null;
    	
    	
    	public ObservablePermission(Object bean, String name, Object entity, String action) {
    		super();
    		this.bean = bean;
    		this.name = name;
    		this.entity = entity;
    		this.action = action;
    	}

		@Override
		public Object getBean() {
			return bean;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public boolean get() {
			if (hasPermission == null) {
				if (isLoggedIn())
					getFromRemote(null);
				return false;
	    	}
			return hasPermission;
	    }
		
		public boolean get(TideResponder<Boolean> tideResponder) {
			if (hasPermission != null) {
		    	if (tideResponder != null) {
		    		TideResultEvent<Boolean> event = new TideResultEvent<Boolean>(getContext(), null, null, hasPermission);
		    		tideResponder.result(event);
		    	}
		    	return hasPermission;
			}
			if (isLoggedIn())
				getFromRemote(tideResponder);
			return false;
		}    
		
		public void getFromRemote(final TideResponder<Boolean> tideResponder) {
			Identity.this.call(name, entity, action, new SimpleTideResponder<Boolean>() {
				@Override
				public void result(TideResultEvent<Boolean> event) {
					hasPermission = event.getResult();
					fireValueChangedEvent();
				}
				
				@Override
				public void fault(TideFaultEvent event) {
					clear();
				}
			});
		}
		
		void clear() {
			hasPermission = null;
			fireValueChangedEvent();
		}
    }
    

    private void initSecurityCache() {
    	for (ObservableRole role : ifAllGrantedCache.values())
    		role.clear();
    	for (ObservableRole role : ifAnyGrantedCache.values())
    		role.clear();
    	for (ObservableRole role : ifNotGrantedCache.values())
    		role.clear();
    	
    	for (Map<String, ObservablePermission> entityPermissions : permissionsCache.values()) {
    		for (ObservablePermission permission : entityPermissions.values())
    			permission.clear();
    	}
    }
    
    /**
     * 	Clear the security cache
     */
    public void clearSecurityCache() {
    	for (ObservableRole role : ifAllGrantedCache.values())
    		role.clear();
    	ifAllGrantedCache.clear();
    	for (ObservableRole role : ifAnyGrantedCache.values())
    		role.clear();
    	ifAnyGrantedCache.clear();
    	for (ObservableRole role : ifNotGrantedCache.values())
    		role.clear();
    	ifNotGrantedCache.clear();
    	
    	for (Map<String, ObservablePermission> entityPermissions : permissionsCache.values()) {
    		for (ObservablePermission permission : entityPermissions.values())
    			permission.clear();
    	}
    	permissionsCache.clear();
    }

	@Override
	public boolean accepts(ErrorMessage emsg) {
		return "Server.Security.NotLoggedIn".equals(emsg.getFaultCode());
	}

	@Override
	public void handle(Context context, ErrorMessage emsg, TideFaultEvent faultEvent) {
		if (isLoggedIn()) {
			// Session expired, directly mark the channel as logged out
			getServerSession().sessionExpired();
			setLoggedIn(false);
		}
	}
	
}

