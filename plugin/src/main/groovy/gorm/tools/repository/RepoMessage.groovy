/*
* Copyright 2019 9ci Inc - Licensed under the Apache License, Version 2.0 (the "License")
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
*/
package gorm.tools.repository

import gorm.tools.beans.AppCtx
import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.MessageSource
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * A bunch of statics to support the Repository artifacts.
 */
@CompileStatic
class RepoMessage {

    /**
     * returns a messageMap not.found.message for a not found error
     *
     * @param params must have a key named id of the object that was not found
     */
    static Map notFound(String domainClassName, Map params) {
        notFoundId(domainClassName, params.id as Serializable)
    }

    static Map notFoundId(String domainClassName, Serializable id) {
        String domainLabel = GrailsNameUtils.getShortName(domainClassName)
        return [code          : "default.not.found.message",
                args          : [domainLabel, id],
                defaultMessage: "${domainLabel} not found with id ${id}"]
    }

    /**
     * just a short method to return a messageMap from the vals passed in
     */
    static Map setup(messageCode, args, defaultMessage = "") {
        return [code: messageCode, args: args, defaultMessage: defaultMessage]
    }

    /**
     * build a map of message params with ident,domainLabel and args
     * ident -> the value of the name field is the dom has one, or the id if not.
     * domainLabel -> the label to use for the domain. just the class name unless there is a entry in message.properties
     * arg -> the array [domainLabel,ident]
     *
     * @param entity the domain instance to build the message params from
     */
    @CompileDynamic
    static Map buildMessageParams(entity) {
        String ident = badge(entity.id, entity)
        String domainLabel = resolveDomainLabel(entity)
        List args = [domainLabel, ident]
        return [ident: ident, domainLabel: domainLabel, args: args]
    }

    /**
     * build a map of message params as @buildMessageParams, but light version to speed up it
     * ident -> entity id
     * domainLabel -> domain class name
     * arg -> the array [domainLabel,ident]
     *
     * @param entity the domain instance to build the message params from
     */
    @CompileDynamic
    static Map buildLightMessageParams(entity) {
        String ident = entity.id
        String domainLabel = entity.class.name
        List args = [domainLabel, ident]
        return [ident: ident, domainLabel: domainLabel, args: args]
    }

    static Map created(entity, boolean buildLight = false) {
        Map p = buildLight ? buildLightMessageParams(entity) : buildMessageParams(entity)
        return setup("default.created.message", p.args, "${p.domainLabel} ${p.ident} created")
    }

    static Map saved(entity, boolean buildLight = false) {
        Map p = buildLight ? buildLightMessageParams(entity) : buildMessageParams(entity)
        return setup("default.saved.message", p.args, "${p.domainLabel} ${p.ident} saved")
    }

    static Map notSaved(entity, boolean buildLight = false) {
        String domainLabel = buildLight ? entity.class.name : resolveDomainLabel(entity)
        return setup("default.not.saved.message", [domainLabel], "${domainLabel} save failed")
    }

    //TODO:
    static Map notSavedDataAccess(entity, boolean buildLight = false) {
        String domainLabel = buildLight ? entity.class.name : resolveDomainLabel(entity)
        return setup("default.not.saved.message", [domainLabel], "${domainLabel} save failed")
    }

    static Map updated(entity, boolean buildLight = false) {
        Map p = buildLight ? buildLightMessageParams(entity) : buildMessageParams(entity)
        return setup("default.updated.message", p.args, "${p.domainLabel} ${p.ident} updated")
    }

    static Map notUpdated(entity, boolean buildLight = false) {
        Map p = buildLight ? buildLightMessageParams(entity) : buildMessageParams(entity)
        return setup("default.not.updated.message", p.args, "${p.domainLabel} ${p.ident} update failed")
    }

    static Map deleted(entity, ident, boolean buildLight = false) {
        String domainLabel = buildLight ? entity.class.name : resolveDomainLabel(entity)
        return setup("default.deleted.message", [domainLabel, ident], "${domainLabel} ${ident} deleted")
    }

    static Map notDeleted(entity, ident, boolean buildLight = false) {
        String domainLabel = buildLight ? entity.class.name : resolveDomainLabel(entity)
        return setup("default.not.deleted.message", [domainLabel, ident], "${domainLabel} ${ident} could not be deleted")
    }

    static Map optimisticLockingFailure(entity, boolean buildLight = false) {
        String domainLabel = buildLight ? entity.class.name : resolveDomainLabel(entity)
        return setup("default.optimistic.locking.failure", [domainLabel], "Another user has updated the ${domainLabel} while you were editing")
    }

    static String resolveDomainLabel(entity) {
        return resolveMessage("${propName(entity.class.name)}.label", "${GrailsNameUtils.getShortName(entity.class.name)}")
    }

    //@CompileDynamic
    static String resolveMessage(String code, String defaultMsg) {
        return AppCtx.get("messageSource", MessageSource).getMessage(code, [] as Object[], defaultMsg, defaultLocale())
    }

    static Locale defaultLocale() {
        try {
            GrailsWebRequest webRequest = RequestContextHolder.currentRequestAttributes() as GrailsWebRequest
            Locale currentLocale = RequestContextUtils.getLocale(webRequest.getCurrentRequest())
            return currentLocale
        }
        catch (java.lang.IllegalStateException e) {
            return Locale.ENGLISH
        }
    }

    static String propName(String prop) {
        GrailsNameUtils.getPropertyName(prop)
    }

    //used for messages, if the entity has a name field then use that other wise fall back on the id and return that
    @CompileDynamic
    static String badge(id, entity) {
        boolean hasName = entity?.metaClass.hasProperty(entity, 'name')
        return ((hasName && entity) ? entity.name : id)
    }

}
