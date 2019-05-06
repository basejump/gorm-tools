/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package gorm.tools.mango

import gorm.tools.Pager
import gorm.tools.mango.api.MangoQuery
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value

@CompileStatic
@Transactional(readOnly = true)
class DefaultMangoQuery implements MangoQuery {

    @Value('${gorm.tools.mango.criteriaKeyName:criteria}')
    //gets criteria keyword from config, if there is no, then uses 'criteria'
    String criteriaKeyName

    /**
     * Builds detached criteria for repository's domain based on mango criteria language and additional criteria
     *
     * @param params mango language criteria map
     * @param closure additional restriction for criteria
     * @return Detached criteria build based on mango language params and criteria closure
     */
    DetachedCriteria buildCriteria(Class domainClass, Map params = [:], Closure closure = null) {
        Map criteria
        if (params[criteriaKeyName] instanceof String) {
            JSON.use('deep')
            criteria = JSON.parse(params[criteriaKeyName] as String) as Map
        } else {
            criteria = params[criteriaKeyName] as Map ?: [:]
        }
        if (params.containsKey('sort')) {
            criteria['$sort'] = params['sort']
        }
        MangoBuilder.build(domainClass, criteria, closure)
    }

    /**
     * List of entities restricted by mango map and criteria closure
     *
     * @param params mango language criteria map
     * @param closure additional restriction for criteria
     * @return query of entities restricted by mango params
     */
    List query(Class domainClass, Map params = [:], Closure closure = null) {
        query(buildCriteria(domainClass, params, closure), params, closure)
    }

    /**
     * List of entities restricted by mango map and criteria closure
     *
     * @param params mango language criteria map
     * @param closure additional restriction for criteria
     * @return query of entities restricted by mango params
     */
    List query(DetachedCriteria criteria, Map params = [:], Closure closure = null) {
        Pager pager = new Pager(params)
        criteria.list(max: pager.max, offset: pager.offset)
    }

    /**
     *  Calculates sums for specified properties in enities query restricted by mango criteria
     *
     * @param params mango language criteria map
     * @param sums query of properties names that sums should be calculated for
     * @param closure additional restriction for criteria
     * @return map where keys are names of fields and value - sum for restricted entities
     */
    Map countTotals(Class domainClass, Map params = [:], List<String> sums, Closure closure = null) {
        DetachedCriteria mangoCriteria = buildCriteria(domainClass, params, closure)

        List totalList
        totalList = mangoCriteria.list {
            projections {
                for (String sumField : sums) {
                    sum(sumField)
                }
            }
        }

        List totalsData = (List) totalList[0]
        Map result = [:]
        sums.eachWithIndex { String name, i ->
            result[name] = totalsData[i]
        }
        return result
    }
}
