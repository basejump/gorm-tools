package gpbench.benchmarks.legacy

import gpbench.basic.CityBasicRepo
import gpbench.benchmarks.AbstractBenchmark
import gpbench.helpers.CsvReader
import gpbench.helpers.JsonReader
import gpbench.helpers.RecordsLoader
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import org.springframework.jdbc.core.JdbcTemplate

@CompileStatic
abstract class BaseBenchmark extends AbstractBenchmark {
    JdbcTemplate jdbcTemplate

    CsvReader csvReader
    JsonReader jsonReader

    CityBasicRepo cityRepo

    boolean useDatabinding = false

    List<Map> cities

    BaseBenchmark(boolean databinding) {
        this.useDatabinding = databinding
    }

    void setup() {
        RecordsLoader recordsLoader = useDatabinding ? csvReader : jsonReader
        cities = recordsLoader.read("City")
    }

    @Transactional
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM city")
    }

}
