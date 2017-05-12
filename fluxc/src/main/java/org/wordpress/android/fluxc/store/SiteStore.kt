package org.wordpress.android.fluxc.store

import android.database.Cursor
import android.text.TextUtils

import com.wellsql.generated.SiteModelTable
import com.yarolegovich.wellsql.WellSql
import com.yarolegovich.wellsql.mapper.SelectMapper

import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.Payload
import org.wordpress.android.fluxc.action.SiteAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.annotations.action.IAction
import org.wordpress.android.fluxc.model.PostFormatModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.SitesModel
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.site.DomainSuggestionResponse
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient.DeleteSiteResponsePayload
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient.ExportSiteResponsePayload
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient.IsWPComResponsePayload
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient.NewSiteResponsePayload
import org.wordpress.android.fluxc.network.xmlrpc.site.SiteXMLRPCClient
import org.wordpress.android.fluxc.persistence.SiteSqlUtils
import org.wordpress.android.fluxc.persistence.SiteSqlUtils.DuplicateSiteException
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T

import java.util.ArrayList
import java.util.Locale

import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLite based only. There is no in memory copy of mapped data, everything is queried from the DB.
 */
@Singleton
class SiteStore @Inject
constructor(dispatcher: Dispatcher, private val mSiteRestClient: SiteRestClient, private val mSiteXMLRPCClient: SiteXMLRPCClient) : Store(dispatcher) {
    // Payloads
    class RefreshSitesXMLRPCPayload : Payload() {
        var username: String? = null
        var password: String? = null
        var url: String? = null
    }

    class NewSitePayload(var siteName: String, var siteTitle: String, var language: String,
                         var visibility: SiteVisibility, var dryRun: Boolean) : Payload()

    class FetchedPostFormatsPayload(var site: SiteModel, var postFormats: List<PostFormatModel>) : Payload()

    class SuggestDomainsPayload(var query: String, var includeWordpressCom: Boolean,
                                var includeDotBlogSubdomain: Boolean, var quantity: Int) : Payload()

    class SuggestDomainsResponsePayload : Payload {
        var query: String
        var suggestions: List<DomainSuggestionResponse>

        constructor(query: String, error: BaseNetworkError) {
            this.query = query
            this.error = error
            this.suggestions = ArrayList<DomainSuggestionResponse>()
        }

        constructor(query: String, suggestions: ArrayList<DomainSuggestionResponse>) {
            this.query = query
            this.suggestions = suggestions
        }
    }

    class SiteError(var type: SiteErrorType) : Store.OnChangedError

    class PostFormatsError(var type: PostFormatsErrorType) : Store.OnChangedError

    class NewSiteError(var type: NewSiteErrorType, var message: String) : Store.OnChangedError

    class DeleteSiteError : Store.OnChangedError {
        var type: DeleteSiteErrorType
        var message: String

        constructor(errorType: String, message: String) {
            this.type = DeleteSiteErrorType.fromString(errorType)
            this.message = message
        }

        constructor(errorType: DeleteSiteErrorType) {
            this.type = errorType
            this.message = ""
        }
    }

    class ExportSiteError(var type: ExportSiteErrorType) : Store.OnChangedError

    // OnChanged Events
    class OnSiteChanged(var rowsAffected: Int) : Store.OnChanged<SiteError>()

    class OnSiteRemoved(var mRowsAffected: Int) : Store.OnChanged<SiteError>()

    class OnAllSitesRemoved(var mRowsAffected: Int) : Store.OnChanged<SiteError>()

    class OnNewSiteCreated : Store.OnChanged<NewSiteError>() {
        var dryRun: Boolean = false
        var newSiteRemoteId: Long = 0
    }

    class OnSiteDeleted(error: DeleteSiteError) : Store.OnChanged<DeleteSiteError>() {
        init {
            this.error = error
        }
    }

    class OnSiteExported : Store.OnChanged<ExportSiteError>()

    class OnPostFormatsChanged(var site: SiteModel) : Store.OnChanged<PostFormatsError>()

    class OnURLChecked(var url: String) : Store.OnChanged<SiteError>() {
        var isWPCom: Boolean = false
    }

    class SuggestDomainError(apiErrorType: String, var message: String) : Store.OnChangedError {
        var type: SuggestDomainErrorType

        init {
            this.type = SuggestDomainErrorType.fromString(apiErrorType)
        }
    }

    class OnSuggestedDomains(var query: String, var suggestions: List<DomainSuggestionResponse>) : Store.OnChanged<SuggestDomainError>()

    class UpdateSitesResult {
        var rowsAffected = 0
        var duplicateSiteFound = false
    }

    enum class SiteErrorType {
        INVALID_SITE,
        DUPLICATE_SITE,
        GENERIC_ERROR
    }

    enum class SuggestDomainErrorType {
        EMPTY_QUERY,
        INVALID_MINIMUM_QUANTITY,
        INVALID_MAXIMUM_QUANTITY,
        GENERIC_ERROR;


        companion object {

            fun fromString(string: String): SuggestDomainErrorType {
                if (!TextUtils.isEmpty(string)) {
                    for (v in SuggestDomainErrorType.values()) {
                        if (string.equals(v.name, ignoreCase = true)) {
                            return v
                        }
                    }
                }
                return GENERIC_ERROR
            }
        }
    }

    enum class PostFormatsErrorType {
        INVALID_SITE,
        GENERIC_ERROR
    }

    enum class DeleteSiteErrorType {
        INVALID_SITE,
        UNAUTHORIZED, // user don't have permission to delete
        AUTHORIZATION_REQUIRED, // missing access token
        GENERIC_ERROR;


        companion object {

            fun fromString(string: String): DeleteSiteErrorType {
                if (!TextUtils.isEmpty(string)) {
                    if (string == "unauthorized") {
                        return UNAUTHORIZED
                    } else if (string == "authorization_required") {
                        return AUTHORIZATION_REQUIRED
                    }
                }
                return GENERIC_ERROR
            }
        }
    }

    enum class ExportSiteErrorType {
        INVALID_SITE,
        GENERIC_ERROR
    }

    // Enums
    enum class NewSiteErrorType {
        SITE_NAME_REQUIRED,
        SITE_NAME_NOT_ALLOWED,
        SITE_NAME_MUST_BE_AT_LEAST_FOUR_CHARACTERS,
        SITE_NAME_MUST_BE_LESS_THAN_SIXTY_FOUR_CHARACTERS,
        SITE_NAME_CONTAINS_INVALID_CHARACTERS,
        SITE_NAME_CANT_BE_USED,
        SITE_NAME_ONLY_LOWERCASE_LETTERS_AND_NUMBERS,
        SITE_NAME_MUST_INCLUDE_LETTERS,
        SITE_NAME_EXISTS,
        SITE_NAME_RESERVED,
        SITE_NAME_RESERVED_BUT_MAY_BE_AVAILABLE,
        SITE_NAME_INVALID,
        SITE_TITLE_INVALID,
        GENERIC_ERROR;


        companion object {

            // SiteStore semantics prefers SITE over BLOG but errors reported from the API use BLOG
            // these are used to convert API errors to the appropriate enum value in fromString
            private val BLOG = "BLOG"
            private val SITE = "SITE"

            fun fromString(string: String): NewSiteErrorType {
                if (!TextUtils.isEmpty(string)) {
                    val siteString = string.toUpperCase(Locale.US).replace(BLOG, SITE)
                    for (v in NewSiteErrorType.values()) {
                        if (siteString.equals(v.name, ignoreCase = true)) {
                            return v
                        }
                    }
                }
                return GENERIC_ERROR
            }
        }
    }

    enum class SiteVisibility private constructor(private val mValue: Int) {
        PRIVATE(-1),
        BLOCK_SEARCH_ENGINE(0),
        PUBLIC(1);

        fun value(): Int {
            return mValue
        }
    }

    override fun onRegister() {
        AppLog.d(T.API, "SiteStore onRegister")
    }

    /**
     * Returns all sites in the store as a [SiteModel] list.
     */
    val sites: List<SiteModel>
        get() = WellSql.select<SiteModel>(SiteModel::class.java).asModel

    /**
     * Returns all sites in the store as a [Cursor].
     */
    val sitesCursor: Cursor
        get() = WellSql.select<SiteModel>(SiteModel::class.java).asCursor

    /**
     * Returns the number of sites of any kind in the store.
     */
    val sitesCount: Int
        get() = sitesCursor.count

    /**
     * Checks whether the store contains any sites of any kind.
     */
    fun hasSite(): Boolean {
        return sitesCount != 0
    }

    /**
     * Obtains the site with the given (local) id and returns it as a [SiteModel].
     */
    fun getSiteByLocalId(id: Int): SiteModel? {
        val result = SiteSqlUtils.getSitesWith(SiteModelTable.ID, id).asModel
        if (result.size > 0) {
            return result[0]
        }
        return null
    }

    /**
     * Checks whether the store contains a site matching the given (local) id.
     */
    fun hasSiteWithLocalId(id: Int): Boolean {
        return SiteSqlUtils.getSitesWith(SiteModelTable.ID, id).asCursor.count > 0
    }

    /**
     * Returns all .COM sites in the store.
     */
    val wpComSites: List<SiteModel>
        get() = SiteSqlUtils.getSitesWith(SiteModelTable.IS_WPCOM, true).asModel

    /**
     * Returns sites accessed via WPCom REST API (WPCom sites or Jetpack sites connected via WPCom REST API).
     */
    val sitesAccessedViaWPComRest: List<SiteModel>
        get() = SiteSqlUtils.getSitesAccessedViaWPComRest().asModel

    /**
     * Returns the number of sites accessed via WPCom REST API (WPCom sites or Jetpack sites connected
     * via WPCom REST API).
     */
    val sitesAccessedViaWPComRestCount: Int
        get() = SiteSqlUtils.getSitesAccessedViaWPComRest().asCursor.count

    /**
     * Checks whether the store contains at least one site accessed via WPCom REST API (WPCom sites or Jetpack
     * sites connected via WPCom REST API).
     */
    fun hasSitesAccessedViaWPComRest(): Boolean {
        return sitesAccessedViaWPComRestCount != 0
    }

    /**
     * Returns the number of .COM sites in the store.
     */
    val wpComSitesCount: Int
        get() = SiteSqlUtils.getSitesWith(SiteModelTable.IS_WPCOM, true).asCursor.count

    /**
     * Returns sites with a name or url matching the search string.
     */
    fun getSitesByNameOrUrlMatching(searchString: String): List<SiteModel> {
        return SiteSqlUtils.getSitesByNameOrUrlMatching(searchString)
    }

    /**
     * Returns sites accessed via WPCom REST API (WPCom sites or Jetpack sites connected via WPCom REST API) with a
     * name or url matching the search string.
     */
    fun getSitesAccessedViaWPComRestByNameOrUrlMatching(searchString: String): List<SiteModel> {
        return SiteSqlUtils.getSitesAccessedViaWPComRestByNameOrUrlMatching(searchString)
    }

    /**
     * Checks whether the store contains at least one .COM site.
     */
    fun hasWPComSite(): Boolean {
        return wpComSitesCount != 0
    }

    /**
     * Returns sites accessed via XMLRPC (self-hosted sites or Jetpack sites accessed via XMLRPC).
     */
    val sitesAccessedViaXMLRPC: List<SiteModel>
        get() = SiteSqlUtils.getSitesAccessedViaXMLRPC().asModel

    /**
     * Returns the number of sites accessed via XMLRPC (self-hosted sites or Jetpack sites accessed via XMLRPC).
     */
    val sitesAccessedViaXMLRPCCount: Int
        get() = SiteSqlUtils.getSitesAccessedViaXMLRPC().asCursor.count

    /**
     * Checks whether the store contains at least one site accessed via XMLRPC (self-hosted sites or
     * Jetpack sites accessed via XMLRPC).
     */
    fun hasSiteAccessedViaXMLRPC(): Boolean {
        return sitesAccessedViaXMLRPCCount != 0
    }

    /**
     * Returns all visible sites as [SiteModel]s. All self-hosted sites over XML-RPC are visible by default.
     */
    val visibleSites: List<SiteModel>
        get() = SiteSqlUtils.getSitesWith(SiteModelTable.IS_VISIBLE, true).asModel

    /**
     * Returns the number of visible sites. All self-hosted sites over XML-RPC are visible by default.
     */
    val visibleSitesCount: Int
        get() = SiteSqlUtils.getSitesWith(SiteModelTable.IS_VISIBLE, true).asCursor.count

    /**
     * Returns all visible .COM sites as [SiteModel]s.
     */
    val visibleSitesAccessedViaWPCom: List<SiteModel>
        get() = SiteSqlUtils.getVisibleSitesAccessedViaWPCom().asModel

    /**
     * Returns the number of visible .COM sites.
     */
    val visibleSitesAccessedViaWPComCount: Int
        get() = SiteSqlUtils.getVisibleSitesAccessedViaWPCom().asCursor.count

    /**
     * Checks whether the .COM site with the given (local) id is visible.
     */
    fun isWPComSiteVisibleByLocalId(id: Int): Boolean {
        return WellSql.select<SiteModel>(SiteModel::class.java)
                .where().beginGroup()
                .equals(SiteModelTable.ID, id)
                .equals(SiteModelTable.IS_WPCOM, true)
                .equals(SiteModelTable.IS_VISIBLE, true)
                .endGroup().endWhere()
                .asCursor.count > 0
    }

    /**
     * Given a (remote) site id, returns the corresponding (local) id.
     */
    fun getLocalIdForRemoteSiteId(siteId: Long): Int {
        val sites = WellSql.select<SiteModel>(SiteModel::class.java)
                .where().beginGroup()
                .equals(SiteModelTable.SITE_ID, siteId)
                .or()
                .equals(SiteModelTable.SELF_HOSTED_SITE_ID, siteId)
                .endGroup().endWhere()
                .getAsModel { cursor ->
                    val siteModel = SiteModel()
                    siteModel.id = cursor.getInt(cursor.getColumnIndex(SiteModelTable.ID))
                    siteModel
                }
        if (sites.size > 0) {
            return sites[0].id
        }
        return 0
    }

    /**
     * Given a (remote) self-hosted site id and XML-RPC url, returns the corresponding (local) id.
     */
    fun getLocalIdForSelfHostedSiteIdAndXmlRpcUrl(selfHostedSiteId: Long, xmlRpcUrl: String): Int {
        val sites = WellSql.select<SiteModel>(SiteModel::class.java)
                .where().beginGroup()
                .equals(SiteModelTable.SELF_HOSTED_SITE_ID, selfHostedSiteId)
                .equals(SiteModelTable.XMLRPC_URL, xmlRpcUrl)
                .endGroup().endWhere()
                .getAsModel { cursor ->
                    val siteModel = SiteModel()
                    siteModel.id = cursor.getInt(cursor.getColumnIndex(SiteModelTable.ID))
                    siteModel
                }
        if (sites.size > 0) {
            return sites[0].id
        }
        return 0
    }

    /**
     * Given a (local) id, returns the (remote) site id. Searches first for .COM and Jetpack, then looks for self-hosted
     * sites.
     */
    fun getSiteIdForLocalId(id: Int): Long {
        val result = WellSql.select<SiteModel>(SiteModel::class.java)
                .where().beginGroup()
                .equals(SiteModelTable.ID, id)
                .endGroup().endWhere()
                .getAsModel { cursor ->
                    val siteModel = SiteModel()
                    siteModel.siteId = cursor.getInt(cursor.getColumnIndex(SiteModelTable.SITE_ID)).toLong()
                    siteModel.selfHostedSiteId = cursor.getLong(
                            cursor.getColumnIndex(SiteModelTable.SELF_HOSTED_SITE_ID))
                    siteModel
                }
        if (result.isEmpty()) {
            return 0
        }

        if (result[0].siteId > 0) {
            return result[0].siteId
        } else {
            return result[0].selfHostedSiteId
        }
    }

    /**
     * Given a .COM site ID (either a .COM site id, or the .COM id of a Jetpack site), returns the site as a
     * [SiteModel].
     */
    fun getSiteBySiteId(siteId: Long): SiteModel? {
        if (siteId == 0) {
            return null
        }

        val sites = SiteSqlUtils.getSitesWith(SiteModelTable.SITE_ID, siteId).asModel

        if (sites.isEmpty()) {
            return null
        } else {
            return sites[0]
        }
    }

    fun getPostFormats(site: SiteModel): List<PostFormatModel> {
        return SiteSqlUtils.getPostFormats(site)
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val actionType = action.type as? SiteAction ?: return

        when (actionType) {
            SiteAction.FETCH_SITE -> fetchSite(action.payload as SiteModel)
            SiteAction.FETCH_SITES -> mSiteRestClient.fetchSites()
            SiteAction.FETCH_SITES_XML_RPC -> fetchSitesXmlRpc(action.payload as RefreshSitesXMLRPCPayload)
            SiteAction.UPDATE_SITE -> updateSite(action.payload as SiteModel)
            SiteAction.UPDATE_SITES -> updateSites(action.payload as SitesModel)
            SiteAction.DELETE_SITE -> deleteSite(action.payload as SiteModel)
            SiteAction.EXPORT_SITE -> exportSite(action.payload as SiteModel)
            SiteAction.REMOVE_SITE -> removeSite(action.payload as SiteModel)
            SiteAction.REMOVE_ALL_SITES -> removeAllSites()
            SiteAction.REMOVE_WPCOM_AND_JETPACK_SITES -> removeWPComAndJetpackSites()
            SiteAction.SHOW_SITES -> toggleSitesVisibility(action.payload as SitesModel, true)
            SiteAction.HIDE_SITES -> toggleSitesVisibility(action.payload as SitesModel, false)
            SiteAction.CREATE_NEW_SITE -> createNewSite(action.payload as NewSitePayload)
            SiteAction.IS_WPCOM_URL -> checkUrlIsWPCom(action.payload as String)
            SiteAction.SUGGEST_DOMAINS -> suggestDomains(action.payload as SuggestDomainsPayload)
            SiteAction.CREATED_NEW_SITE -> handleCreateNewSiteCompleted(action.payload as NewSiteResponsePayload)
            SiteAction.FETCH_POST_FORMATS -> fetchPostFormats(action.payload as SiteModel)
            SiteAction.FETCHED_POST_FORMATS -> updatePostFormats(action.payload as FetchedPostFormatsPayload)
            SiteAction.DELETED_SITE -> handleDeletedSite(action.payload as DeleteSiteResponsePayload)
            SiteAction.EXPORTED_SITE -> handleExportedSite(action.payload as ExportSiteResponsePayload)
            SiteAction.CHECKED_IS_WPCOM_URL -> handleCheckedIsWPComUrl(action.payload as IsWPComResponsePayload)
            SiteAction.SUGGESTED_DOMAINS -> handleSuggestedDomains(action.payload as SuggestDomainsResponsePayload)
        }
    }

    private fun removeSite(site: SiteModel) {
        val rowsAffected = SiteSqlUtils.deleteSite(site)
        emitChange(OnSiteRemoved(rowsAffected))
    }

    private fun removeAllSites() {
        val rowsAffected = SiteSqlUtils.deleteAllSites()
        val event = OnAllSitesRemoved(rowsAffected)
        emitChange(event)
    }

    private fun removeWPComAndJetpackSites() {
        // Logging out of WP.com. Drop all WP.com sites, and all Jetpack sites that were fetched over the WP.com
        // REST API only (they don't have a .org site id)
        val wpcomAndJetpackSites = SiteSqlUtils.getSitesAccessedViaWPComRest().asModel
        val rowsAffected = removeSites(wpcomAndJetpackSites)
        emitChange(OnSiteRemoved(rowsAffected))
    }

    private fun createNewSite(payload: NewSitePayload) {
        mSiteRestClient.newSite(payload.siteName, payload.siteTitle, payload.language, payload.visibility,
                payload.dryRun)
    }

    private fun handleCreateNewSiteCompleted(payload: NewSiteResponsePayload) {
        val onNewSiteCreated = OnNewSiteCreated()
        onNewSiteCreated.error = payload.error
        onNewSiteCreated.dryRun = payload.dryRun
        onNewSiteCreated.newSiteRemoteId = payload.newSiteRemoteId
        emitChange(onNewSiteCreated)
    }

    private fun fetchSite(site: SiteModel) {
        if (site.isUsingWpComRestApi) {
            mSiteRestClient.fetchSite(site)
        } else {
            mSiteXMLRPCClient.fetchSite(site)
        }
    }

    private fun fetchSitesXmlRpc(payload: RefreshSitesXMLRPCPayload) {
        mSiteXMLRPCClient.fetchSites(payload.url, payload.username, payload.password)
    }

    private fun fetchPostFormats(site: SiteModel) {
        if (site.isUsingWpComRestApi) {
            mSiteRestClient.fetchPostFormats(site)
        } else {
            mSiteXMLRPCClient.fetchPostFormats(site)
        }
    }

    private fun deleteSite(site: SiteModel) {
        // Not available for Jetpack sites
        if (!site.isWPCom) {
            val event = OnSiteDeleted(DeleteSiteError(DeleteSiteErrorType.INVALID_SITE))
            emitChange(event)
            return
        }
        mSiteRestClient.deleteSite(site)
    }

    private fun exportSite(site: SiteModel) {
        // Not available for Jetpack sites
        if (!site.isWPCom) {
            val event = OnSiteExported()
            event.error = ExportSiteError(ExportSiteErrorType.INVALID_SITE)
            emitChange(event)
            return
        }
        mSiteRestClient.exportSite(site)
    }

    private fun updateSite(siteModel: SiteModel) {
        val event = OnSiteChanged(0)
        if (siteModel.isError) {
            // TODO: what kind of error could we get here?
            event.error = SiteError(SiteErrorType.GENERIC_ERROR)
        } else {
            try {
                event.rowsAffected = SiteSqlUtils.insertOrUpdateSite(siteModel)
            } catch (e: DuplicateSiteException) {
                event.error = SiteError(SiteErrorType.DUPLICATE_SITE)
            }

        }
        emitChange(event)
    }

    private fun updateSites(sitesModel: SitesModel) {
        val event = OnSiteChanged(0)
        if (sitesModel.isError) {
            // TODO: what kind of error could we get here?
            event.error = SiteError(SiteErrorType.GENERIC_ERROR)
        } else {
            val res = createOrUpdateSites(sitesModel)
            event.rowsAffected = res.rowsAffected
            if (res.duplicateSiteFound) {
                event.error = SiteError(SiteErrorType.DUPLICATE_SITE)
            }
        }
        emitChange(event)
    }

    private fun updatePostFormats(payload: FetchedPostFormatsPayload) {
        val event = OnPostFormatsChanged(payload.site)
        if (payload.isError) {
            // TODO: what kind of error could we get here?
            event.error = PostFormatsError(PostFormatsErrorType.GENERIC_ERROR)
        } else {
            SiteSqlUtils.insertOrReplacePostFormats(payload.site, payload.postFormats)
        }
        emitChange(event)
    }

    private fun handleDeletedSite(payload: DeleteSiteResponsePayload) {
        val event = OnSiteDeleted(payload.error)
        if (!payload.isError) {
            SiteSqlUtils.deleteSite(payload.site)
        }
        emitChange(event)
    }

    private fun handleExportedSite(payload: ExportSiteResponsePayload) {
        val event = OnSiteExported()
        if (payload.isError) {
            // TODO: what kind of error could we get here?
            event.error = ExportSiteError(ExportSiteErrorType.GENERIC_ERROR)
        }
        emitChange(event)
    }

    private fun checkUrlIsWPCom(payload: String) {
        mSiteRestClient.checkUrlIsWPCom(payload)
    }

    private fun handleCheckedIsWPComUrl(payload: IsWPComResponsePayload) {
        val event = OnURLChecked(payload.url)
        if (payload.isError) {
            // Return invalid site for all errors (this endpoint seems a bit drunk).
            // Client likely needs to know if there was an error or not.
            event.error = SiteError(SiteErrorType.INVALID_SITE)
        }
        event.isWPCom = payload.isWPCom
        emitChange(event)
    }

    private fun createOrUpdateSites(sites: SitesModel): UpdateSitesResult {
        val result = UpdateSitesResult()
        for (site in sites.sites) {
            try {
                result.rowsAffected += SiteSqlUtils.insertOrUpdateSite(site)
            } catch (caughtException: DuplicateSiteException) {
                result.duplicateSiteFound = true
            }

        }
        return result
    }

    private fun removeSites(sites: List<SiteModel>): Int {
        var rowsAffected = 0
        for (site in sites) {
            rowsAffected += SiteSqlUtils.deleteSite(site)
        }
        return rowsAffected
    }

    private fun toggleSitesVisibility(sites: SitesModel, visible: Boolean): Int {
        var rowsAffected = 0
        for (site in sites.sites) {
            rowsAffected += SiteSqlUtils.setSiteVisibility(site, visible)
        }
        return rowsAffected
    }

    private fun suggestDomains(payload: SuggestDomainsPayload) {
        mSiteRestClient.suggestDomains(payload.query, payload.includeWordpressCom, payload.includeDotBlogSubdomain,
                payload.quantity)
    }

    private fun handleSuggestedDomains(payload: SuggestDomainsResponsePayload) {
        val event = OnSuggestedDomains(payload.query, payload.suggestions)
        if (payload.isError) {
            if (payload.error is WPComGsonRequest.WPComGsonNetworkError) {
                event.error = SuggestDomainError((payload.error as WPComGsonNetworkError).apiError,
                        payload.error.message)
            } else {
                event.error = SuggestDomainError("", payload.error.message)
            }
        }
        emitChange(event)
    }
}
