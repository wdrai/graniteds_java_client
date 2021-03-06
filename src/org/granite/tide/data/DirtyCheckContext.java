package org.granite.tide.data;

import java.util.Map;
import java.util.Set;

import org.granite.tide.TrackingContext;
import org.granite.tide.data.DataManager.ChangeKind;


public interface DirtyCheckContext {
    
    public void setTrackingContext(TrackingContext trackingContext);

    public void clear(boolean notify);

    public void markNotDirty(Object object, Identifiable entity);

    public boolean checkAndMarkNotDirty(Identifiable local, Identifiable received);

    public boolean isEntityChanged(Object entity);

    public Map<String, Object> getSavedProperties(Object localEntity);

    public void resetEntity(MergeContext mergeContext, Object entity, Identifiable parent, Set<Object> cache);

    public void resetAllEntities(MergeContext mergeContext, Set<Object> cache);

    public void entityPropertyChangeHandler(Object owner, Object target, String property, Object oldValue, Object newValue);

    public void entityCollectionChangeHandler(Object owner, String property, ChangeKind kind, int location, Object[] items);

    public void entityMapChangeHandler(Object owner, String property, ChangeKind kind, int location, Object[] items);

}
