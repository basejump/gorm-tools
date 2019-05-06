/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package gorm.tools.databinding

import gorm.tools.GormMetaUtils
import gorm.tools.beans.IsoDateUtil
import grails.core.GrailsApplication
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.converters.ValueConverter
import grails.databinding.events.DataBindingListener
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.util.Environment
import grails.web.databinding.GrailsWebDataBinder
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.web.databinding.DataBindingEventMulticastListener
import org.grails.web.databinding.GrailsWebDataBindingListener
import org.springframework.validation.BeanPropertyBindingResult

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Faster data binder for PersistentEntity.persistentProperties. Uses the persistentProperties to assign values from the Map
 * Explicitly checks and converts most common property types eg (numbers and dates). Otherwise fallbacks to value converters.
 *
 */
@SuppressWarnings(['CatchException'])
@CompileStatic
class EntityMapBinder extends GrailsWebDataBinder implements MapBinder {

    /**
     * A map that holds lists of properties which should be bound manually by a binder.
     * A key represents a domain class and the value is a list with properties.
     */
    static final Map<Class, List> EXPLICIT_BINDING_LIST = new ConcurrentHashMap<Class, List>()
    protected static final Map<Class, List> CLASS_TO_BINDING_INCLUDE_LIST = new ConcurrentHashMap<Class, List>()

    EntityMapBinder() {
        super(null)
    }

    EntityMapBinder(GrailsApplication grailsApplication) {
        super(grailsApplication)
    }

    /**
     * Binds data from a map on target object.
     *
     * @param obj The target object to bind
     * @param source The data binding source
     */
    @Override
    void bind(obj, DataBindingSource source) {
        bind obj, source, null, getBindingIncludeList(obj), null, null
    }

    /**
     * Binds data from a map on target object.
     *
     * @param obj The target object to bind
     * @param source The data binding source
     * @param listener DataBindingListener
     */
    @Override
    void bind(obj, DataBindingSource source, DataBindingListener listener) {
        bind obj, source, null, getBindingIncludeList(obj), null, listener
    }

    /**
     * Binds data from a map on target object.
     *
     * @param obj The target object to bind
     * @param source The data binding source
     * @param filter Only properties beginning with filter will be included in the data binding.
     * @param whiteList A list of property names to be included during this
     * data binding.  All other properties represented in the binding source
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.
     * @param listener DataBindingListener
     */
    @Override
    void bind(object, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener) {
        Object bindingResult = new BeanPropertyBindingResult(object, object.getClass().name)
        doBind object, source, filter, whiteList, blackList, listener, bindingResult
    }

    @Override
    protected void doBind(object, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener, errors) {
        //TODO this is where we will store errors
        BeanPropertyBindingResult bindingResult = (BeanPropertyBindingResult) errors
        GrailsWebDataBindingListener errorHandlingListener = new GrailsWebDataBindingListener(messageSource)
        List<DataBindingListener> allListeners = []
        allListeners << errorHandlingListener

        DataBindingEventMulticastListener listenerWrapper = new DataBindingEventMulticastListener(allListeners)

        fastBind(object, source, whiteList, listenerWrapper, errors)

        populateErrors(object, bindingResult)
    }

    /**
     * Binds data from a map on target object.
     *
     * @param args An optional map of options. supports two boolean options
     * <ul>
     *     <li><b>include</b> The list of properties to include in data binding</li>
     *     <li><b>errorHandling</b> If type conversion error should be handled and Added to Errors</li>
     * </ul>
     *
     * @param target The target object to bind
     * @param source The data binding source
     */
    @Override
    void bind(Map args = [:], Object target, Map<String, Object> source) {
        //BindAction bindAction = (BindAction) args["bindAction"]

        List includeList = (List) args["include"] ?: getBindingIncludeList(target)
        Boolean errorHandling = args["errorHandling"] == null ? true : args["errorHandling"]
        if (errorHandling) {
            bind target, new SimpleMapDataBindingSource(source), null, includeList, null, null
        } else {
            fastBind target, new SimpleMapDataBindingSource(source), includeList
        }
    }

    /**
     * Binds properties which specified in a white list on the given entity.
     * In case the white list is empty method takes the list of persistent properties and iterates on them.
     *
     * @param target    a target entity
     * @param source    a data binding source which contains property values
     * @param whiteList a list which contains properties for binding
     * @param listener  DataBindingListener
     * @param errors
     */
    void fastBind(Object target, DataBindingSource source, List whiteList = null, DataBindingListener listener = null, errors = null) {
        Objects.requireNonNull(target, "Target is null")
        if (!source) return
        GormStaticApi gormStaticApi = GormEnhancer.findStaticApi(target.getClass())
        PersistentEntity entity = gormStaticApi.gormPersistentEntity
        List<String> properties = whiteList ?: entity.persistentPropertyNames

        for (String prop : properties) {
            PersistentProperty perProp = entity.getPropertyByName(prop)
            try {
                setProp(target, source, perProp, listener, errors)
            } catch (Exception e) {
                if (errors) {
                    addBindingError(target, perProp.name, source.getPropertyValue(perProp.name), e, listener, errors)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Sets a value to a specified target's property.
     *
     * @param target   a target entity
     * @param source   a data binding source which contains property values
     * @param prop     a persistent property which should be filled with the value
     * @param listener DataBindingListener
     * @param errors
     */
    void setProp(target, DataBindingSource source, PersistentProperty prop, DataBindingListener listener = null, errors = null) {
        if (!source.containsProperty(prop.name)) return

        Object propValue = source.getPropertyValue(prop.name)
        Object valueToAssign = propValue

        if (propValue instanceof String) {
            String sval = propValue as String
            Class typeToConvertTo = prop.getType()
            //do we have tests for this?
            if (String.isAssignableFrom(typeToConvertTo)) {
                sval = sval.trim()
                sval = ("" == sval) ? null : sval
                valueToAssign = sval
            } else if (Date.isAssignableFrom(typeToConvertTo)) {
                valueToAssign = IsoDateUtil.parse(sval)
            } else if (LocalDate.isAssignableFrom(typeToConvertTo)) {
                valueToAssign = IsoDateUtil.parseLocalDate(sval)
                //LocalDate.parse(val, DateTimeFormatter.ISO_DATE_TIME)
            } else if (LocalDateTime.isAssignableFrom(typeToConvertTo)) {
                valueToAssign = IsoDateUtil.parseLocalDateTime(sval)
            } else if (Number.isAssignableFrom(typeToConvertTo)) {
                valueToAssign = sval.asType(typeToConvertTo)
            } else if (conversionHelpers.containsKey(typeToConvertTo)) {
                List<ValueConverter> convertersList = conversionHelpers.get(typeToConvertTo)
                ValueConverter converter = convertersList?.find { ValueConverter c -> c.canConvert(propValue) }
                if (converter) {
                    valueToAssign = converter.convert(propValue)
                }
            } else if (conversionService?.canConvert(propValue.getClass(), typeToConvertTo)) {
                valueToAssign = conversionService.convert(propValue, typeToConvertTo)
            }

            target[prop.name] = valueToAssign

        } else if (prop instanceof Association) {
            bindAssociation(target, valueToAssign, (Association) prop, listener, errors)
        } else {
            target[prop.name] = valueToAssign
        }

    }

    /**
     * Binds a given association to the target entity.
     * It checks if the given value contains an id and loads the associated entity.
     * In case the id is not specified, this method checks if the given association belongs to the target entity.
     * In case it does, or the it has the explicitly specified 'bindable:true' constraint, then a new instance is
     * created for the association.
     *
     * @param target      a target entity to bind an association to
     * @param value       an association's value
     * @param association an association property
     * @param listener    DataBindingListener
     * @param errors
     */
    void bindAssociation(target, value, Association association, DataBindingListener listener = null, errors = null) {
        String aprop = association.name

        //if value is null or they are the same instance type then just set and exit fast
        if (value == null || association.getType().isAssignableFrom(value.getClass())) {
            target[aprop] = value
            return
        }

        //if value has idVal then it should be set to existing instance and everything else will be ignored
        Object idValue = isDomainClass(value.getClass()) ? value['id'] : getIdentifierValueFrom(value)
        idValue = idValue == 'null' ? null :  idValue

        if (idValue) {
            // check if the target[aprop].id is the same and we don't need to do anything or
            // the target's property is null and we should bind it
            if(!target[aprop] || (target[aprop] && (target[aprop]['id'] != idValue))){
                //we are setting it to a new id so load it and assign
                target[aprop] = getPersistentInstance(getDomainClassType(target, association.name), idValue)
            }
        } else if (association.isOwningSide() || isExplicitBind(target, association.name)) {
            if (!(value instanceof Map)) {
                String msg = "Unable to create an association instance for the entity=${target}, the value=$value is not a Map"
                throw new IllegalArgumentException(msg)
            }
            //if its null then set it up
            if(target[aprop] == null) target[aprop] = association.type.newInstance()
            //recursive call to set the association up and assume its a map
            fastBind(target[aprop], new SimpleMapDataBindingSource((Map) value))
        }
    }

    /**
     * Checks if a given association is explicitly marked as bindable and should be binded in any case.
     *
     * @param target an entity which contains an association
     * @param name   a name of an association to check if it should be binded
     * @return true if the association name with prefix is present in the white list
     */
    static boolean isExplicitBind(Object target, String name) {
        EXPLICIT_BINDING_LIST.get(target.getClass()).contains(name)
    }

    /**
     * Sets up a list of properties which can be binded to a given domain entity.
     *
     * Puts the created list to ${@code CLASS_TO_BINDING_INCLUDE_LIST} map,
     * which caches such lists for domain classes.
     *
     * The method also checks if a property has an explicitly defined 'bindable:true' constraint.
     * In case the constraint is present, the property name is added to {@code EXPLICIT_BINDING_LIST}.
     *
     * @param object an entity for which the list should be created
     * @return a list of properties which can be bound to the given entity
     */
    static List getBindingIncludeList(final Object object) {
        List<String> whiteList = []
        final Class<? extends Object> objectClass = object.getClass()
        if (CLASS_TO_BINDING_INCLUDE_LIST.containsKey(objectClass)) {
            whiteList = CLASS_TO_BINDING_INCLUDE_LIST.get objectClass
        } else {
            GormStaticApi gormStaticApi = GormEnhancer.findStaticApi(object.getClass())
            PersistentEntity entity = gormStaticApi.gormPersistentEntity
            List<PersistentProperty> properties = entity.persistentProperties
            Map<String, ConstrainedProperty> constraints = GormMetaUtils.findConstrainedProperties(entity)
            List explicitBindingList = EXPLICIT_BINDING_LIST.get(objectClass) ?: []
            for (PersistentProperty prop : properties) {
                DefaultConstrainedProperty cp = (DefaultConstrainedProperty) constraints[prop.name]
                Boolean bindable = cp?.getMetaConstraintValue("bindable") as Boolean
                if (bindable == null || bindable == true) {
                    whiteList.add(prop.name)
                }
                if (bindable == true) {
                    explicitBindingList.add(prop.name)
                }
            }
            if (!Environment.getCurrent().isReloadEnabled()) {
                CLASS_TO_BINDING_INCLUDE_LIST.put objectClass, whiteList
                if (explicitBindingList) EXPLICIT_BINDING_LIST.put(objectClass, explicitBindingList)
            }
        }

        return whiteList
    }

    @Override
    @SuppressWarnings(["EmptyCatchBlock"])
    protected getPersistentInstance(Class<?> type, id) {
        try {
            GormEnhancer.findStaticApi(type).load((Serializable) id)
        } catch (Exception exc) {
        }
    }

}
