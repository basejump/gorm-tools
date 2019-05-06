/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package yakworks.commons

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.runtime.DefaultGroovyMethods

import java.util.regex.Pattern

/**
 * Copied from org.grails.config.ConfigMap so we can use it as a gradle plugin here
 * This is like groovy's ConfigObject but better.
 */
@EqualsAndHashCode
@CompileStatic
class ConfigMap implements Map<String, Object>, Cloneable {

    //private static final Pattern SPLIT_PATTERN = ~/\./
    private static final String SPRING_PROFILES = 'spring.profiles.active'
    private static final String SPRING = 'spring'
    private static final String PROFILES = 'profiles'

    final ConfigMap rootConfig
    final List<String> path
    final Map<String, Object> delegateMap
    final String dottedPath
    //extra binding for gstring templates
    final Map bindingMap

    public ConfigMap() {
        rootConfig = this
        path = []
        dottedPath = ""
        bindingMap = [:]
        delegateMap = [:]
    }

    public ConfigMap(ConfigMap rootConfig, List<String> path) {
        super()
        this.rootConfig = rootConfig
        this.path = path
        dottedPath = path.join('.')
        delegateMap = [:]
    }

    private ConfigMap(ConfigMap rootConfig, List<String> path, Map<String, Object> delegateMap) {
        this.rootConfig = rootConfig
        this.path = path
        dottedPath = path.join('.')
        this.delegateMap= delegateMap
    }

    @Override
    String toString() {
        delegateMap.toString()
    }

    @CompileDynamic
    public ConfigMap clone() {
        return new ConfigMap(rootConfig, path, delegateMap.clone())
    }

    @Override
    int size() {
        delegateMap.size()
    }

    @Override
    boolean isEmpty() {
        delegateMap.isEmpty()
    }

    @Override
    boolean containsKey(Object key) {
        delegateMap.containsKey key
    }

    @Override
    boolean containsValue(Object value) {
        delegateMap.containsValue value
    }

    @Override
    Object get(Object key) {
        delegateMap.get(key)
    }

    @Override
    Object put(String key, Object value) {
        delegateMap.put(key, value)
    }

    @Override
    Object remove(Object key) {
        delegateMap.remove key
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        delegateMap.putAll m
    }

    @Override
    void clear() {
        delegateMap.clear()
    }

    @Override
    Set<String> keySet() {
        delegateMap.keySet()
    }

    @Override
    Collection<Object> values() {
        delegateMap.values()
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        delegateMap.entrySet()
    }

    public void merge(String key, Object value) {
        merge([(key): value], true)
    }

    public void merge(Map sourceMap, boolean parseFlatKeys=false) {
        mergeMaps(this, "", this, sourceMap, parseFlatKeys)
    }

    private void mergeMaps(ConfigMap rootMap, String path, ConfigMap targetMap, Map sourceMap, boolean parseFlatKeys) {
        if (!shouldSkipBlock(sourceMap, path)) {
            for (Entry entry in sourceMap) {
                Object sourceKeyObject = entry.key
                Object sourceValue = entry.value
                String sourceKey = String.valueOf(sourceKeyObject)
                if (parseFlatKeys) {
                    String[] keyParts = sourceKey.split(/\./)
                    if (keyParts.length > 1) {
                        mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                        def pathParts = keyParts[0..-2]
                        Map actualTarget = targetMap.navigateSubMap(pathParts as List, true)
                        sourceKey = keyParts[-1]
                        mergeMapEntry(rootMap, pathParts.join('.'), actualTarget, sourceKey, sourceValue, parseFlatKeys)
                    } else {
                        mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                    }
                } else {
                    mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                }
            }
        }
    }

    private boolean shouldSkipBlock(Map sourceMap, String path) {
        Object springProfileDefined = System.properties.getProperty(SPRING_PROFILES)
        boolean hasSpringProfiles =
            sourceMap.get(SPRING) instanceof Map && ((Map)sourceMap.get(SPRING)).get(PROFILES) ||
                path == SPRING && sourceMap.get(PROFILES)

        return !springProfileDefined && hasSpringProfiles
    }

    protected void mergeMapEntry(ConfigMap rootMap, String path, ConfigMap targetMap, String sourceKey, Object sourceValue,
                                 boolean parseFlatKeys, boolean isNestedSet = false) {
        Object currentValue = targetMap.containsKey(sourceKey) ? targetMap.get(sourceKey) : null
        Object newValue
        if(sourceValue instanceof Map) {
            List<String> newPathList = []
            newPathList.addAll( targetMap.getPath() )
            newPathList.add(sourceKey)
            ConfigMap subMap
            if(currentValue instanceof ConfigMap) {
                subMap = (ConfigMap)currentValue
            }
            else {
                subMap = new ConfigMap( (ConfigMap)targetMap.rootConfig, newPathList.asImmutable())
                if(currentValue instanceof Map) {
                    subMap.putAll((Map)currentValue)
                }
            }
            String newPath = path ? "${path}.${sourceKey}" : sourceKey
            mergeMaps(rootMap, newPath , subMap, (Map)sourceValue, parseFlatKeys)
            newValue = subMap
        } else {
            newValue = sourceValue
        }
        if (isNestedSet && newValue == null) {
            if(path) {

                def subMap = rootMap.get(path)
                if(subMap instanceof Map) {
                    subMap.remove(sourceKey)
                }
                def keysToRemove = rootMap.keySet().findAll { String key ->
                    key.startsWith("${path}.")
                }
                for(key in keysToRemove) {
                    rootMap.remove(key)
                }
            }
            targetMap.remove(sourceKey)
        } else {
            if(path) {
                rootMap.put( "${path}.${sourceKey}".toString(), newValue )
            }
            mergeMapEntry(targetMap, sourceKey, newValue)
        }
    }

    protected Object mergeMapEntry(ConfigMap targetMap, String sourceKey, Object newValue) {
        targetMap.put(sourceKey, newValue)
    }

    public Object getAt(Object key) {
        getProperty(String.valueOf(key))
    }

    public void setAt(Object key, Object value) {
        setProperty(String.valueOf(key), value)
    }

    public Object getProperty(String name) {
        //println "getProperty $name"
        if (!containsKey(name)) {
            return new NullSafeNavigator(this, [name].asImmutable())
        }
        def val = get(name)
        //println "getProperty $name : $val"
        //if it starts with a $ then eval it with using merge.
        if(val && val instanceof String && (val.startsWith('$') || val.startsWith('\$'))){
            //println "evaluating $val"
            val = templateEval(val)  //evaluate the GString it
            //now that its been evaluated we will merge it in so we don't do it again the next time its accessed
            //if it has dot keys in the key then we need to merge in the evaluated result
            boolean hasDotKeys = name.matches(/.+[\.].+/)
            hasDotKeys ? merge(name, val) : setProperty(name, val)
            //mergeMapEntry(rootConfig, dottedPath, this, name, val, hasDotKeys, true)
            //setProperty(name, val)
        }
        return val
    }

    void addToBinding(Map vars){
        bindingMap.putAll(vars)
    }
    void addToBinding(String var, Object val){
        bindingMap.put(var, val)
    }

    /**
     * Evals the text using a GStringTemplateEngine
     * @param text
     * @return
     */
    String templateEval(String text){
        Map binding = [config:rootConfig] + rootConfig.getBindingMap()
        def engine = new groovy.text.SimpleTemplateEngine()
        def template = engine.createTemplate(text).make(binding.withDefault{null})
        //println "templateEval with binding ${binding}"
        return template.toString()
    }

    /**
     * Gets the key and if its a Map will make sure all strings that start with $ have been evaluated
     * returns this instance
     */
    ConfigMap  evalAll(){
        this.keySet().each { key ->
            def val = getProperty(key) //accessing the property will force an eval if its a string and starts with $
            if(val instanceof ConfigMap) val.evalAll()
        }
        return this
    }

    /**
     * returns a new map after pruning all nulls and falsey values
     */
    Map prune(){
        Maps.prune(this)
    }

    public void setProperty(String name, Object value) {
        mergeMapEntry(rootConfig, dottedPath, this, name, value, false, true)
    }

    public Object navigate(String... path) {
        return navigateMap(this, path)
    }

    private Object navigateMap(Map<String, Object> map, String... path) {
        if(map==null || path == null) return null
        if(path.length == 0) {
            return map
        } else if (path.length == 1) {
            return map.get(path[0])
        } else {
            def submap = map.get(path[0])
            if(submap instanceof Map) {
                return navigateMap((Map<String, Object>) submap, path.tail())
            }
            return submap
        }
    }

    public ConfigMap navigateSubMap(List<String> path, boolean createMissing) {
        ConfigMap rootMap = this
        ConfigMap currentMap = this
        StringBuilder accumulatedPath = new StringBuilder()
        boolean isFirst = true
        for(String pathElement : path) {
            if(isFirst) {
                isFirst = false
                accumulatedPath.append(pathElement)
            }
            else {
                accumulatedPath.append(".").append(pathElement)
            }

            Object currentItem = currentMap.get(pathElement)
            if(currentItem instanceof ConfigMap) {
                currentMap = (ConfigMap)currentItem
            } else if (createMissing) {
                List<String> newPathList = []
                newPathList.addAll( currentMap.getPath() )
                newPathList.add(pathElement)

                Map<String, Object> newMap = new ConfigMap( (ConfigMap)currentMap.rootConfig, newPathList.asImmutable())
                currentMap.put(pathElement, newMap)

                def fullPath = accumulatedPath.toString()
                if(!rootMap.containsKey(fullPath)) {
                    rootMap.put(fullPath, newMap)
                }
                currentMap = newMap
            } else {
                return null
            }
        }
        currentMap
    }

    /**
     * returns an "un-optimized" map combining the denormalized keys with dots and removes duplicate keys
     * basically puts it back into a "normalized" map
     *
     * @return
     */
    public Map<String, Object> toNormalMap() {
        //return toFlatConfig()
        Closure nester

        nester = { Map rslt, String key, val ->
            String[] keys = key.split( /\./, 2 )
            String key0 = keys[0]

            if(!rslt.containsKey(key0)) rslt[key0] = [:]

            if( keys.length == 2){
                nester rslt[key0] as Map, keys[1], val
            }
            else{
                rslt[key] = val
            }
            rslt
        }

        Map<String, Object> flat = toFlatConfig()
        //skip the array keys "foo[0]"
        flat.entrySet().removeIf { Entry<String, Object> entry->
            entry.key.matches(/.*\[\d\]/)
        }

        Map treeMap = injectTrampoline(flat, nester)

        return treeMap
    }

    //@CompileDynamic //FIXME this should not need CompileDynamic, compile works fine but it shows an annoying error on intellij
    Map injectTrampoline(Map flat, Closure nester){
        (Map)flat.inject([:], nester.trampoline())
    }

    public Map<String, Object> toFlatConfig() {
        Map<String,Object> flatConfig = [:]
        flattenKeys(flatConfig, this, [], false)
        flatConfig
    }

    public Properties toProperties() {
        Properties properties = new Properties()
        flattenKeys(properties as Map<String, Object>, this, [], true)
        properties
    }

    private void flattenKeys(Map<String, Object> flatConfig, Map currentMap, List<String> path, boolean forceStrings) {
        currentMap.each { key, value ->
            String stringKey = String.valueOf(key)
            if(value != null) {
                if(value instanceof Map) {
                    List<String> newPathList = []
                    newPathList.addAll( path )
                    newPathList.add( stringKey )

                    flattenKeys(flatConfig, (Map)value, newPathList.asImmutable(), forceStrings)
                } else {
                    String fullKey
                    if(path) {
                        fullKey = path.join('.') + '.' + stringKey
                    } else {
                        fullKey = stringKey
                    }
                    if(value instanceof Collection) {
                        if(forceStrings) {
                            flatConfig.put(fullKey, ((Collection)value).join(","))
                        } else {
                            flatConfig.put(fullKey, value)
                        }
                        int index = 0
                        for(Object item: (Collection)value) {
                            String collectionKey = "${fullKey}[${index}]"
                            flatConfig.put(collectionKey, forceStrings ? String.valueOf(item) : item)
                            index++
                        }
                    } else {
                        flatConfig.put(fullKey, forceStrings ? String.valueOf(value) : value)
                    }
                }
            }
        }
    }

    @Override
    int hashCode() {
        return delegateMap.hashCode()
    }

    @Override
    boolean equals(Object obj) {
        return delegateMap == obj
    }

    /**
     * Uses JsonOutput.prettyPrint to give a pretty and more human readable string
     * @return
     */
    String prettyPrint() {
        groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(toNormalMap()))
    }

    @SuppressWarnings(['ExplicitCallToGetAtMethod', 'EqualsAndHashCode', 'ToStringReturnsNull', 'BooleanMethodReturnsNull'])
    @EqualsAndHashCode
    @CompileStatic
    static class NullSafeNavigator implements Map<String, Object>{
        final ConfigMap parent
        final List<String> path

        NullSafeNavigator(ConfigMap parent, List<String> path) {
            this.parent = parent
            this.path = path
        }

        Object getAt(Object key) {
            getProperty(String.valueOf(key))
        }

        void setAt(Object key, Object value) {
            setProperty(String.valueOf(key), value)
        }

        @Override
        int size() {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.size()
            }
            return 0
        }

        @Override
        boolean isEmpty() {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.isEmpty()
            }
            return true
        }

        boolean containsKey(Object key) {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap == null) return false
            else {
                return parentMap.containsKey(key)
            }
        }

        @Override
        boolean containsValue(Object value) {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.containsValue(value)
            }
            return false
        }

        @Override
        Object get(Object key) {
            return getAt(key)
        }

        @Override
        Object put(String key, Object value) {
            throw new UnsupportedOperationException("Configuration cannot be modified")
        }

        @Override
        Object remove(Object key) {
            throw new UnsupportedOperationException("Configuration cannot be modified")
        }

        @Override
        void putAll(Map<? extends String, ?> m) {
            throw new UnsupportedOperationException("Configuration cannot be modified")
        }

        @Override
        void clear() {
            throw new UnsupportedOperationException("Configuration cannot be modified")
        }

        @Override
        Set<String> keySet() {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.keySet()
            }
            return Collections.emptySet()
        }

        @Override
        Collection<Object> values() {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.values()
            }
            return Collections.emptySet()
        }

        @Override
        Set<Map.Entry<String, Object>> entrySet() {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.entrySet()
            }
            return Collections.emptySet()
        }

        Object getProperty(String name) {
            ConfigMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap == null) {
                return new NullSafeNavigator(parent, ((path + [name]) as List<String>).asImmutable())
            } else {
                return parentMap.get(name)
            }
        }

        public void setProperty(String name, Object value) {
            ConfigMap parentMap = parent.navigateSubMap(path, true)
            parentMap.setProperty(name, value)
        }

        public boolean asBoolean() {
            false
        }

        public Object invokeMethod(String name, Object args) {
            throw new UnsupportedOperationException("Cannot invoke method " + name + "() on NullSafeNavigator")
        }

        public boolean equals(Object to) {
            return to == null || DefaultGroovyMethods.is(this, to)
        }

        public Iterator iterator() {
            return Collections.EMPTY_LIST.iterator()
        }

        public Object plus(String s) {
            return toString() + s
        }

        public Object plus(Object o) {
            throw new UnsupportedOperationException("Cannot invoke method plus on NullSafeNavigator")
        }

        public boolean is(Object other) {
            return other == null || DefaultGroovyMethods.is(this, other)
        }

        public Object asType(Class c) {
            return ((c == Boolean) || (c == boolean)) ? false : null
        }

        public String toString() {
            return null
        }

        //        public int hashCode() {
        //            throw new NullPointerException("Cannot invoke method hashCode() on NullSafeNavigator");
        //        }
    }
}
