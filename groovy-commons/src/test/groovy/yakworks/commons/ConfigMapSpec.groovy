/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package yakworks.commons

import spock.lang.Issue;
import spock.lang.Specification

/**
 * copied from grails core
 */
class ConfigMapSpec extends Specification {

    def "should support merging ConfigObject maps"() {
        given:
        ConfigMap configMap = new ConfigMap()
        def config = new ConfigSlurper().parse('''
foo {
    bar = "good"
}
test.another = true
''')

        when:"a config object is merged"
        configMap.merge(config)

        then:"The merge is correct"
        configMap.size() == 4
        configMap['test'] instanceof ConfigMap
        configMap['test.another']  == true
        configMap['test']['another']  == true
    }

    def "should support merge correctly"() {
        given:
        ConfigMap configMap = new ConfigMap()
        when:
        configMap.merge(['foo.bar':'good1', bar:[foo:'good2']], true)
        //configMap.setProperty('foo.bar','fu')
        then:
        configMap.foo.bar == 'good1'
        configMap['foo']['bar'] == 'good1'
        configMap['foo.bar'] == 'good1'
        configMap.getProperty('foo.bar') == 'good1'

        configMap.bar.foo == 'good2'
        configMap.getProperty('bar.foo') == 'good2'

        when:
        configMap.foo.bar2 = 'baz'

        then:
        configMap['foo']['bar2'] == 'baz'
        configMap['foo.bar2'] == 'baz'
        configMap.getProperty('foo.bar2') == 'baz'
        configMap.foo.bar2 == 'baz'

        when:
        configMap.merge('foo2.bar2', 'buzz')
        //configMap.merge(['foo2.bar2': 'buzz'], true)

        then:
        configMap['foo2']['bar2'] == 'buzz'
        configMap['foo2.bar2'] == 'buzz'
        configMap.getProperty('foo2.bar2') == 'buzz'
        configMap.foo2.bar2 == 'buzz'

        when:
        configMap.merge(['foo.two':'good3', bar:[two:'good4']], true)
        configMap.merge(['grails.codegen.defaultPackage':"test"])
        configMap.merge([grails:[codegen:[defaultPackage:"test"]]])
        configMap.merge(['grails.codegen':[defaultPackage:"test"]], true)

        then:
        configMap.size() == 12
        configMap.containsKey('grails.codegen.defaultPackage')
        configMap.getProperty('grails.codegen.defaultPackage') == 'test'
        configMap.grails.codegen.defaultPackage == 'test'
        configMap.foo.bar == 'good1'
        configMap.getProperty('foo.bar') == 'good1'
        configMap.bar.foo == 'good2'
        configMap.getProperty('bar.foo') == 'good2'

        configMap.foo.two == 'good3'
        configMap.getProperty('foo.two') == 'good3'
        configMap.bar.two == 'good4'
        configMap.getProperty('bar.two') == 'good4'

    }
    def "should support flattening keys"() {
        given:
        ConfigMap configMap = new ConfigMap()
        when:
        configMap.a.b.c = 1
        configMap.a.b.d = 2
        then:
        configMap.toFlatConfig() == ['a.b.c': 1, 'a.b.d': 2]
    }

    def "should support hashCode()"() {
        given:
        ConfigMap configMap = new ConfigMap()
        when:
        configMap.a.b.c = 1
        configMap.a.b.d = 2
        then:"hasCode() doesn't cause a Stack Overflow error"
        configMap.hashCode() == configMap.hashCode()
    }

    def "should support flattening list values"() {
        given:
        ConfigMap configMap = new ConfigMap()
        when:
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        then:
        configMap.toFlatConfig() == [
            'a.b.c': [1, 2, 3],
             'a.b.c[0]': 1,
             'a.b.c[1]': 2,
             'a.b.c[2]': 3,
             'a.b.d': 2]
    }

    def "should support flattening to properties"() {
        given:
        ConfigMap configMap = new ConfigMap()
        when:
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        then:
        configMap.toProperties() == [
            'a.b.c': '1,2,3',
             'a.b.c[0]': '1',
             'a.b.c[1]': '2',
             'a.b.c[2]': '3',
             'a.b.d': '2']
    }

    def "should support cloning"() {
        given:
        ConfigMap configMap = new ConfigMap()
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        when:
        ConfigMap cloned = configMap.clone()
        then:
        cloned.toFlatConfig() == [
            'a.b.c': [1, 2, 3],
             'a.b.c[0]': 1,
             'a.b.c[1]': 2,
             'a.b.c[2]': 3,
             'a.b.d': 2]
        !cloned.is(configMap)
        cloned == configMap
    }

    def "should support binding"() {
        when:
        ConfigMap configMap = new ConfigMap()

        configMap.addToBinding("someVar", 'xxx' )
        configMap.addToBinding("wtf", { String prop -> "got $prop" } )
//        configMap.addToBinding([
//            props: [find: { String prop -> "got $prop" } ]
//        ])
        println "bindingMap ${configMap.getBindingMap()}"
        configMap.sv = '$someVar'
        configMap.foo = 'bar'
        configMap.cfg = '${config.foo}'
        configMap.methCall = '${wtf("that")}'


        then:
        configMap.methCall == 'got that'
        configMap.sv == 'xxx'
        configMap.cfg == 'bar'

    }
}
