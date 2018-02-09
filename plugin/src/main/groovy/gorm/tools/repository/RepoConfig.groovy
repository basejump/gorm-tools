package gorm.tools.repository

import gorm.tools.WithTrx
import gorm.tools.databinding.BindAction
import gorm.tools.databinding.MapBinder
import gorm.tools.mango.api.MangoQueryTrait
import gorm.tools.repository.api.GormBatchRepo
import gorm.tools.repository.api.RepositoryApi
import gorm.tools.repository.errors.EntityNotFoundException
import gorm.tools.repository.errors.EntityValidationException
import gorm.tools.repository.errors.RepoExceptionSupport
import gorm.tools.repository.events.RepoEventPublisher
import grails.validation.ValidationException
import groovy.transform.CompileStatic
import groovyx.gpars.util.PoolUtils
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.core.Datastore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.GenericTypeResolver
import org.springframework.dao.DataAccessException

import javax.annotation.PostConstruct
import javax.persistence.Transient

/**
 * @author Alexey Zvegintcev (@alexeyzvegintcev)
 * @since 6.x
 */
@CompileStatic
trait RepoEntityConfig {
    @Transient
    static Map repoEntityConfig
}
