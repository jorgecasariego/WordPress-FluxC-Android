package org.wordpress.android.fluxc.network.xmlrpc.taxonomy;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;

import org.apache.commons.text.StringEscapeUtils;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.RequestPayload;
import org.wordpress.android.fluxc.action.TaxonomyAction;
import org.wordpress.android.fluxc.generated.TaxonomyActionBuilder;
import org.wordpress.android.fluxc.generated.endpoint.XMLRPC;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.TermModel;
import org.wordpress.android.fluxc.model.TermsModel;
import org.wordpress.android.fluxc.network.BaseRequest.BaseErrorListener;
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError;
import org.wordpress.android.fluxc.network.HTTPAuthManager;
import org.wordpress.android.fluxc.network.UserAgent;
import org.wordpress.android.fluxc.network.xmlrpc.BaseXMLRPCClient;
import org.wordpress.android.fluxc.network.xmlrpc.XMLRPCRequest;
import org.wordpress.android.fluxc.store.TaxonomyStore.FetchTermResponsePayload;
import org.wordpress.android.fluxc.store.TaxonomyStore.FetchTermsResponsePayload;
import org.wordpress.android.fluxc.store.TaxonomyStore.RemoteTermResponsePayload;
import org.wordpress.android.fluxc.store.TaxonomyStore.TaxonomyError;
import org.wordpress.android.fluxc.store.TaxonomyStore.TaxonomyErrorType;
import org.wordpress.android.util.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxonomyXMLRPCClient extends BaseXMLRPCClient {
    public TaxonomyXMLRPCClient(Dispatcher dispatcher, RequestQueue requestQueue, UserAgent userAgent,
                                HTTPAuthManager httpAuthManager) {
        super(dispatcher, requestQueue, userAgent, httpAuthManager);
    }

    public void fetchTerm(final RequestPayload requestPayload, final TermModel term, final SiteModel site) {
        fetchTerm(requestPayload, term, site, TaxonomyAction.FETCH_TERM);
    }

    public void fetchTerm(final RequestPayload requestPayload, final TermModel term, final SiteModel site,
                          final TaxonomyAction origin) {
        List<Object> params = new ArrayList<>(5);
        params.add(site.getSelfHostedSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(term.getTaxonomy());
        params.add(term.getRemoteTermId());

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), XMLRPC.GET_TERM, params,
                new Listener<Object>() {
                    @Override
                    public void onResponse(Object response) {
                    if (response != null && response instanceof Map) {
                        TermModel termModel = termResponseObjectToTermModel(response, site);
                        FetchTermResponsePayload payload;
                        if (termModel != null) {
                            if (origin == TaxonomyAction.PUSH_TERM) {
                                termModel.setId(term.getId());
                            }
                            payload = new FetchTermResponsePayload(requestPayload, termModel, site, null);
                        } else {
                            payload = new FetchTermResponsePayload(requestPayload, term, site,
                                    new TaxonomyError(TaxonomyErrorType.INVALID_RESPONSE));
                        }
                        payload.origin = origin;

                        mDispatcher.dispatchRet(TaxonomyActionBuilder.newFetchedTermAction(payload));
                    }
                    }
                },
                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        // Possible non-generic errors:
                        // 403 - "Invalid taxonomy."
                        // 404 - "Invalid term ID."
                        // TODO: Check the error message and flag this as INVALID_TAXONOMY or UNKNOWN_TERM
                        // Convert GenericErrorType to TaxonomyErrorType where applicable
                        TaxonomyError taxonomyError;
                        switch (error.type) {
                            case AUTHORIZATION_REQUIRED:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.UNAUTHORIZED, error.message);
                                break;
                            default:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.GENERIC_ERROR, error.message);
                        }
                        FetchTermResponsePayload payload = new FetchTermResponsePayload(requestPayload, term, site,
                                taxonomyError);
                        payload.origin = origin;
                        mDispatcher.dispatchRet(TaxonomyActionBuilder.newFetchedTermAction(payload));
                    }
                }
        );

        add(requestPayload, request);
    }

    public void fetchTerms(final RequestPayload requestPayload, final SiteModel site, final String taxonomyName) {
        List<Object> params = new ArrayList<>(4);
        params.add(site.getSelfHostedSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(taxonomyName);

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), XMLRPC.GET_TERMS, params,
                new Listener<Object[]>() {
                    @Override
                    public void onResponse(Object[] response) {
                        TermsModel terms = termsResponseToTermsModel(response, site);

                        FetchTermsResponsePayload payload = new FetchTermsResponsePayload(requestPayload, terms, site,
                                taxonomyName);

                        if (terms != null) {
                            mDispatcher.dispatchRet(TaxonomyActionBuilder.newFetchedTermsAction(payload));
                        } else {
                            payload.error = new TaxonomyError(TaxonomyErrorType.INVALID_RESPONSE);
                            mDispatcher.dispatchRet(TaxonomyActionBuilder.newFetchedTermsAction(payload));
                        }
                    }
                },
                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        // Possible non-generic errors:
                        // 403 - "Invalid taxonomy."
                        // TODO: Check the error message and flag this as INVALID_TAXONOMY if applicable
                        // Convert GenericErrorType to TaxonomyErrorType where applicable
                        TaxonomyError taxonomyError;
                        switch (error.type) {
                            case AUTHORIZATION_REQUIRED:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.UNAUTHORIZED, error.message);
                                break;
                            default:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.GENERIC_ERROR, error.message);
                        }
                        FetchTermsResponsePayload payload = new FetchTermsResponsePayload(requestPayload, taxonomyError,
                                taxonomyName);
                        mDispatcher.dispatchRet(TaxonomyActionBuilder.newFetchedTermsAction(payload));
                    }
                }
        );

        add(requestPayload, request);
    }

    public void pushTerm(final RequestPayload requestPayload, final TermModel term, final SiteModel site) {
        Map<String, Object> contentStruct = termModelToContentStruct(term);

        List<Object> params = new ArrayList<>(4);
        params.add(site.getSelfHostedSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(contentStruct);

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), XMLRPC.NEW_TERM, params,
                new Listener<Object>() {
                    @Override
                    public void onResponse(Object response) {
                        term.setRemoteTermId(Long.valueOf((String) response));

                        RemoteTermResponsePayload payload = new RemoteTermResponsePayload(requestPayload, term, site,
                                null);
                        mDispatcher.dispatchRet(TaxonomyActionBuilder.newPushedTermAction(payload));
                    }
                },
                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        // Possible non-generic errors:
                        // 403 - "Invalid taxonomy."
                        // 403 - "Parent term does not exist."
                        // 403 - "The term name cannot be empty."
                        // 500 - "A term with the name provided already exists with this parent."
                        // TODO: Check the error message and flag this as one of the above specific errors if applicable
                        // Convert GenericErrorType to PostErrorType where applicable
                        TaxonomyError taxonomyError;
                        switch (error.type) {
                            case AUTHORIZATION_REQUIRED:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.UNAUTHORIZED, error.message);
                                break;
                            default:
                                taxonomyError = new TaxonomyError(TaxonomyErrorType.GENERIC_ERROR, error.message);
                        }
                        RemoteTermResponsePayload payload = new RemoteTermResponsePayload(requestPayload, term, site,
                                taxonomyError);
                        mDispatcher.dispatchRet(TaxonomyActionBuilder.newPushedTermAction(payload));
                    }
                }
        );

        request.disableRetries();
        add(requestPayload, request);
    }

    private TermsModel termsResponseToTermsModel(Object[] response, SiteModel site) {
        List<Map<?, ?>> termsList = new ArrayList<>();
        for (Object responseObject : response) {
            Map<?, ?> termMap = (Map<?, ?>) responseObject;
            termsList.add(termMap);
        }

        List<TermModel> termArray = new ArrayList<>();
        TermModel term;

        for (Object termObject : termsList) {
            term = termResponseObjectToTermModel(termObject, site);
            if (term != null) {
                termArray.add(term);
            }
        }

        if (termArray.isEmpty()) {
            return null;
        }

        return new TermsModel(termArray);
    }

    private TermModel termResponseObjectToTermModel(Object termObject, SiteModel site) {
        // Sanity checks
        if (!(termObject instanceof Map)) {
            return null;
        }

        Map<?, ?> termMap = (Map<?, ?>) termObject;
        TermModel term = new TermModel();

        String termId = MapUtils.getMapStr(termMap, "term_id");
        if (TextUtils.isEmpty(termId)) {
            // If we don't have a term ID, move on
            return null;
        }

        term.setLocalSiteId(site.getId());
        term.setRemoteTermId(Integer.valueOf(termId));
        term.setSlug(MapUtils.getMapStr(termMap, "slug"));
        term.setName(StringEscapeUtils.unescapeHtml4(MapUtils.getMapStr(termMap, "name")));
        term.setDescription(StringEscapeUtils.unescapeHtml4(MapUtils.getMapStr(termMap, "description")));
        term.setParentRemoteId(MapUtils.getMapLong(termMap, "parent"));
        term.setTaxonomy(MapUtils.getMapStr(termMap, "taxonomy"));

        return term;
    }

    private static Map<String, Object> termModelToContentStruct(TermModel term) {
        Map<String, Object> contentStruct = new HashMap<>();

        contentStruct.put("name", term.getName());
        contentStruct.put("taxonomy", term.getTaxonomy());

        if (term.getSlug() != null) {
            contentStruct.put("slug", term.getSlug());
        }

        if (term.getDescription() != null) {
            contentStruct.put("description", term.getDescription());
        }

        if (term.getParentRemoteId() > 0) {
            contentStruct.put("parent", term.getParentRemoteId());
        }

        return contentStruct;
    }
}
