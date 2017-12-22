package gpbench.benchmarks

import gorm.tools.repository.RepoUtil
import gpbench.City
import gpbench.CityRepo
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

/**
 *  Commit each record in individual transaction
 */
@CompileStatic
class CommitEachSaveBenchmark extends BaseBenchmark {

    CommitEachSaveBenchmark(boolean databinding) {
        super(databinding)
    }

    @Override
    def execute() {
        assert City.count() == 0
        insert(cities, cityRepo)
        assert City.count() == 115000
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void insert(List<Map> batch, CityRepo repo) {
        batch.eachWithIndex { Map record, int index ->
            insert(record, repo)
            if (index % batchSize == 0) RepoUtil.flushAndClear()
        }
    }

    @Transactional
    void insert(Map record, CityRepo repo) {
        try {
            if (useDatabinding) repo.create(record)
            else repo.insertWithSetter(record)
        } catch (Exception e) {
            e.printStackTrace()
        }

    }

    @Override
    String getDescription() {
        return "Commit each save: databinding=${useDatabinding}"
    }
}