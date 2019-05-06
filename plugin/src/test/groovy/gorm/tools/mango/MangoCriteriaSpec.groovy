/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package gorm.tools.mango

import grails.buildtestdata.TestData
import grails.gorm.DetachedCriteria
import grails.test.hibernate.HibernateSpec
import grails.testing.spring.AutowiredTest
import org.junit.Ignore
import testing.Location
import testing.Nested
import testing.Org
import testing.OrgType

class MangoCriteriaSpec extends HibernateSpec implements AutowiredTest {

    List<Class> getDomainClasses() { [Org, Location, Nested] }

    static DetachedCriteria build(map, Closure closure = null) {
        //DetachedCriteria detachedCriteria = new DetachedCriteria(Org)
        return MangoBuilder.build(Org, map, closure)
    }

    void setupSpec() {
        Org.withTransaction {
            def type = TestData.build(OrgType)
            (1..10).each { index ->
                String value = "Name#" + index
                new Org(id: index,
                    name: value,
                    type: type,
                    inactive: (index % 2 == 0),
                    amount: (index - 1) * 1.34,
                    amount2: (index - 1) * (index - 1) * 0.3,
                    date: new Date().clearTime() + index,
                    name2: index % 2 == 0 ? null : "Name2#" + index,
                    location: (new Location(address: "City#$index", nested: new Nested(name: "Nested#${2 * index}", value: index)).save())
                ).save(failOnError: true)
            }
        }
    }

    @spock.lang.Ignore //FIXME this should give a better error message
    def "test field that does not exist"() {
        when:
        List res = build([nonExistingFooBar: true]).list()

        then:
        res.size() == 5
    }

    def "test detached isActive"() {
        when:
        List res = build([inactive: true]).list()

        then:
        res.size() == 5
    }

    def "test detached string"() {
        when:
        List res = build([name: "Name#1"]).list()

        then:
        res.size() == 1
    }

    def "test detached like"() {
        when:

        List res = build([name: "Name#%"]).list()

        then:
        res.size() == 10
    }

    def "test combined"() {
        when:

        List res = build(([amount: [1 * 1.34, 2 * 1.34, 3 * 1.34, 4 * 1.34], inactive: true])).list()

        then:
        res.size() == 2
    }

    def "test detached BigDecimal"() {
        when:


        List res = build(([amount: 1.34])).list()

        then:
        res.size() == 1

        when:
        res = build(([amount: ['$gt': 6.0]])).list()

        then:
        res.size() == 5
    }

    def "test detached Date"() {
        when:


        List res = build(([date: new Date().clearTime() + 2])).list()

        then:
        res.size() == 1

        when:
        res = build(([date: ['$gt': new Date().clearTime() + 7]])).list()

        then:
        res.size() == 3
    }

    def "test gt"() {
        when:


        List res = build(([id: ['$gt': 4]])).list()

        then:
        res.size() == 6
    }

    def "test ne"() {
        when:


        List res = build(([id: ['$ne': 4]])).list()

        then:
        res.size() == 9
    }

    def "test nested"() {
        when:

        List res = build((["location.id": ['$eq': 6]])).list()

        then:
        res.size() == 1
    }

    def "test nested String"() {
        when:

        List res = build((["location.address": "City#4"])).list()

        then:
        res.size() == 1
    }

    def "test nested location city"() {
        when:

        List res = build(([
            location: [
                '$or': [
                    address: "City#4",
                    id  : 4
                ]
            ]
        ])).list()

        then:
        res.size() == 1
    }


    def "test nestedId"() {
        when:

        List res = build((["locationId": ['$eq': 6]])).list()

        then:
        res.size() == 1
    }


    def "test or"() {
        when:

        List res = build([
            '$or': [
                [name: "Name#7"],
                [id: 2]
            ]
        ]).list()

        then:
        res.size() == 2
    }

    def "test not in list"() {
        when:


        List res = build(([amount: ['$nin': [1 * 1.34, 2 * 1.34, 3 * 1.34, 4 * 1.34]]])).list()

        then:
        res.size() == 6
    }

    def "test in list"() {
        when:


        List res = build(([id: [1, 2, 3, 4]])).list()

        then:
        res.size() == 4
    }

    def "test not"() {
        when:


        List res = build((['$not': [[id: ['$eq': 1]]]])).list()

        then:
        res.size() == 9
    }


    def "test between"() {
        when:


        List res = build(([amount: ['$between': [1 * 1.34, 4 * 1.34]]])).list()

        then:
        res.size() == 4
    }

    def "test isNull/ isNotNull"() {
        when:


        List res = build(([name2: ['$isNull': true]])).list()

        then:
        res.size() == 5

        when:

        res = build(([name2: '$isNull'])).list()

        then:
        res.size() == 5
    }

    def "test fields comparison"() {
        when:

        List res = build(([amount: ['$gtef': "amount2"]])).list()

        then:
        res.size() == 5

        when:
        res = build(([amount: ['$gtf': "amount2"]])).list()

        then:
        res.size() == 4

        when:
        res = build(([amount: ['$ltf': "amount2"]])).list()

        then:
        res.size() == 5

        when:
        res = build(([amount: ['$eqf': "amount2"]])).list()

        then:
        res.size() == 1

        when:
        res = build(([amount: ['$nef': "amount2"]])).list()

        then:
        res.size() == 9
    }

    def "test quickSearch"() {
        when:

        List res = build((['$quickSearch': "Name#%"])).list()

        then:
        res.size() == 10

        when:

        res = build((['$quickSearch': "Name#3"])).list()

        then:
        res.size() == 1

        when: "quick search is combined with another field"
        res = build((['$quickSearch': "Name#%", inactive: true])).list()

        then:
        res.size() == 5


    }

    def "test with closure"() {
        when:


        List res = build([name: "Name#%"]) { gt "id", 5 }.list()

        then:
        res.size() == 5
    }


    def "test with deep nested"() {
        when:
        List res = build((["location.nested.name": "Nested#4"])).list()

        then:
        res.size() == 1
    }

    def "test with `or` on one level"() {
        when:
        List res = build((['$or': [["location.id": 5], ["name": "Name#1", "location.id": 4]]])).list()

        then:
        res.size() == 1
    }

    def "test order"() {
        when:
        List res = build(([id: [1, 2, 3, 4], '$sort': [id: "desc"]])).list()

        then:
        res.size() == 4
        res[0].id == 4
    }

}
