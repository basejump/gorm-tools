/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package gorm.tools.repository.events

import gorm.tools.repository.api.RepositoryApi
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent
import org.springframework.core.ResolvableType
import org.springframework.core.ResolvableTypeProvider

//import org.springframework.core.GenericTypeResolver

/**
 * Base event class for Repository event firing
 * @param D
 */
@CompileStatic
class RepositoryEvent<D> extends ApplicationEvent implements ResolvableTypeProvider {//extends ApplicationEvent {//

    /** the domain instance this event is for */
    D entity
    /** if this event fired during binding action then this is the data used */
    Map data
    /** during a binding action this can be set to the BindAction.Created, Updated*/
    String bindAction

    /** RepositoryEventType.eventKey. set in constructor. ex: a BeforePersistEvent this will be 'beforePersist' */
    String eventKey = "repoEvent"

    RepositoryEvent(RepositoryApi repo, final D entity, String eventKey) {
        super(repo)
        this.entity = entity
        this.eventKey = eventKey
        //this.entity = mappingContext.getPersistentEntity(entityObject.getClass().getName());
    }

//    RepositoryEvent(final Datastore source, final D entity, String eventKey) {
//        super(source)
//        MappingContext mappingContext = source.getMappingContext()
//        this.entity = mappingContext.getProxyHandler().unwrap(entity)
//        this.eventKey = eventKey
//        //this.entity = mappingContext.getPersistentEntity(entityObject.getClass().getName());
//    }

    /**
     * done per the spring docs so that listeners can bind to the generic of the event.
     * ex: implements ApplicationListener<BeforeBindEvent<City>>
     * or @EventListener
     *    void beforeBind(BeforeBindEvent<City> event)
     */
    @Override
    ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(getEntity()))
    }

    /**
     * @return the routing key in the form of "DomainClass.eventMethod", for example "City.afterPersist"
     */
    String getRoutingKey() { "${entity.class.simpleName}.${eventKey}" }

    void setDataFromArgMap(Map args){
        this.data = args ? args['data'] as Map : null
        this.bindAction = args ? args['bindAction'] as String : null
    }
}
