package gorm.tools

import grails.core.GrailsDomainClassProperty
import grails.compiler.GrailsCompileStatic
import org.apache.commons.lang.Validate

@GrailsCompileStatic
class GormUtils {

    final static List<String> IGNORED_PROPERTIES = ["id", "version", "createdBy", "createdDate", "editedBy", "editedDate", "num"]

    /**
     * @param domainClass Domain class
     * @param old - domain class to copy properties from
     * @param override - properties to override after copying
     * @param ignoreAssociations - should associations be copied ? - ignored by default
     */
    static <T> T copyDomain(Class<T> domainClass, Object old, Map override = [:], boolean ignoreAssociations = true) {
        T copy = domainClass.newInstance()
        return copyDomain(copy, old, override, ignoreAssociations) as T
    }

    /**
     * @param copy domain instance to copy properties to
     * @param old - domain class to copy properties from
     * @param override - properties to override after copying
     * @param ignoreAssociations - should associations be copied ? - ignored by default
     */

    static Object copyDomain(Object copy, Object old, Map override = [:], boolean ignoreAssociations = true) {
        if (copy == null) throw new IllegalArgumentException("Copy is null")
        if (old == null) return null

        GormMetaUtils.getDomainClass(old.class).persistentProperties.each { GrailsDomainClassProperty dp ->
            if (IGNORED_PROPERTIES.contains(dp.name) || dp.identity) return
            if (ignoreAssociations && dp.isAssociation()) return

            String name = dp.name
            copy[name] = old[name]
        }

        if (override) {
            copy.properties = override
        }

        return copy
    }

    static void copyProperties(Object source, Object target, String... propNames) {
        copyProperties(source, target, true, propNames)
    }

    /**
     * Copy all  given property values from source to target.
     * @param source
     * @param target
     * @param propNames
     */
    static void copyProperties(Object source, Object target, boolean copyOnlyIfNull, String... propNames) {

        for (String prop : propNames) {
            if (copyOnlyIfNull && (target[prop] != null)) {
                continue
            } else {
                target[prop] = source[prop]
            }
        }

    }

    /**
     * Return the value of the (possibly nested) property of the specified name, for the specified source object
     *
     * Example getPropertyValue(source, "x.y.z")
     *
     * @param source - The source object
     * @param property - the property
     * @return value of the specified property or null if any of the intermediate objects are null
     */
    static Object getPropertyValue(Object source, String property) {
        Validate.notNull(source)
        Validate.notEmpty(property)

        Object result = property.tokenize('.').inject(source){ Object obj, String prop ->
            Object value = null
            if (obj != null) value = obj[prop]
            return value
        }

        return result
    }

}