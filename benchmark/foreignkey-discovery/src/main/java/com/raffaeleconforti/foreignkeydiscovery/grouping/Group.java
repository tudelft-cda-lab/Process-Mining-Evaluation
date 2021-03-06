package com.raffaeleconforti.foreignkeydiscovery.grouping;

import com.raffaeleconforti.foreignkeydiscovery.conceptualmodels.Attribute;
import com.raffaeleconforti.foreignkeydiscovery.conceptualmodels.Entity;
import com.raffaeleconforti.foreignkeydiscovery.mapping.MappedObject;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;

import java.util.Set;

/**
 * Defines a grouping of entities into subProcesses
 *
 * @author Raffaele Conforti
 */

public class Group {
    private Entity mainEntity; //defines the artifact identifier
    private Set<Entity> entities; //other than the main entity
    private Set<MappedObject<Attribute, Entity>> timeStamps;

    public Group(Entity entity) {
        this.mainEntity = entity;
        this.entities = new UnifiedSet<Entity>();
        this.timeStamps = new UnifiedSet<MappedObject<Attribute, Entity>>();
        for (Attribute ts : entity.getTimestamps()) {
            timeStamps.add(new MappedObject<Attribute, Entity>(ts, entity));
        }
    }

    public void addEntity(Entity e) {
        entities.add(e);
        for (Attribute ts : e.getTimestamps()) {
            timeStamps.add(new MappedObject<Attribute, Entity>(ts, e));
        }
    }

    public Set<Entity> getEntities() {
        Set<Entity> allEntities = new UnifiedSet<Entity>();
        allEntities.add(mainEntity);
        allEntities.addAll(entities);
        return allEntities;
    }

    public Entity getMainEntity() {
        return mainEntity;
    }

    public Set<MappedObject<Attribute, Entity>> getTimeStamps() {
        return timeStamps;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof Group) {
            Group group = (Group) o;
            if(this.mainEntity.getName().equals(group.mainEntity.getName())) {
                for(Entity e1 : this.entities) {
                    boolean found = false;
                    for(Entity e2 : group.entities) {
                        if(e1.equals(e2)) {
                            found = true;
                            break;
                        }
                    }
                    if(!found) return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        StringBuilder sb = new StringBuilder();
        sb.append(mainEntity.getName());
        for(Entity e : entities) {
            sb.append("\n").append(e.getName());
        }
        return sb.toString().hashCode();
    }


}
