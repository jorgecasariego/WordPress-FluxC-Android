package org.wordpress.android.stores.network.xmlrpc.post;

import android.text.TextUtils;

import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.stores.Dispatcher;
import org.wordpress.android.stores.generated.PostActionBuilder;
import org.wordpress.android.stores.model.PostLocation;
import org.wordpress.android.stores.model.PostModel;
import org.wordpress.android.stores.model.PostStatus;
import org.wordpress.android.stores.model.PostsModel;
import org.wordpress.android.stores.model.SiteModel;
import org.wordpress.android.stores.network.HTTPAuthManager;
import org.wordpress.android.stores.network.UserAgent;
import org.wordpress.android.stores.network.rest.wpcom.auth.AccessToken;
import org.wordpress.android.stores.network.xmlrpc.BaseXMLRPCClient;
import org.wordpress.android.stores.network.xmlrpc.XMLRPC;
import org.wordpress.android.stores.network.xmlrpc.XMLRPCRequest;
import org.wordpress.android.stores.store.PostStore.ChangeRemotePostPayload.UploadMode;
import org.wordpress.android.stores.store.PostStore.FetchPostsResponsePayload;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.MapUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostXMLRPCClient extends BaseXMLRPCClient {
    private static final int NUM_POSTS_TO_REQUEST = 20;

    private int mFeaturedImageId;

    public PostXMLRPCClient(Dispatcher dispatcher, RequestQueue requestQueue, AccessToken accessToken,
                            UserAgent userAgent, HTTPAuthManager httpAuthManager) {
        super(dispatcher, requestQueue, accessToken, userAgent, httpAuthManager);
    }

    public void getPost(final PostModel post, final SiteModel site) {
        List<Object> params;

        if (post.isPage()) {
            params = new ArrayList<>(4);
            params.add(site.getDotOrgSiteId());
            params.add(post.getRemotePostId());
            params.add(site.getUsername());
            params.add(site.getPassword());
        } else {
            params = new ArrayList<>(3);
            params.add(post.getRemotePostId());
            params.add(site.getUsername());
            params.add(site.getPassword());
        }

        params.add(site.getDotOrgSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(post.getRemotePostId());

        XMLRPC method = (post.isPage() ? XMLRPC.GET_PAGE : XMLRPC.GET_POST);

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), method, params,
                new Listener<Object>() {
                    @Override
                    public void onResponse(Object response) {
                        if (response != null && response instanceof Map) {
                            PostModel postModel = postResponseObjectToPostModel(response, site, post.isPage());
                            if (postModel != null) {
                                mDispatcher.dispatch(PostActionBuilder.newUpdatePostAction(postModel));
                            } else {
                                // TODO: do nothing or dispatch error?
                            }
                        }
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Implement lower-level catching in BaseXMLRPCClient
                    }
                });

        add(request);
    }

    public void getPosts(final SiteModel site, final boolean getPages, final int offset) {
        int numPostsToRequest = offset + NUM_POSTS_TO_REQUEST;

        List<Object> params = new ArrayList<>(4);
        params.add(site.getDotOrgSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(numPostsToRequest);

        XMLRPC method = (getPages ? XMLRPC.GET_PAGES : XMLRPC.GET_POSTS);

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), method, params,
                new Listener<Object[]>() {
                    @Override
                    public void onResponse(Object[] response) {
                        boolean canLoadMore;
                        int startPosition = 0;
                        if (response != null && response.length > 0) {
                            canLoadMore = true;

                            // If we're loading more posts, only save the posts at the end of the array.
                            // NOTE: Switching to wp.getPosts wouldn't require janky solutions like this
                            // since it allows for an offset parameter.
                            if (offset > 0 && response.length > NUM_POSTS_TO_REQUEST) {
                                startPosition = response.length - NUM_POSTS_TO_REQUEST;
                            }
                        } else {
                            canLoadMore = false;
                        }

                        PostsModel posts = postsResponseToPostsModel(response, site, getPages, startPosition);

                        FetchPostsResponsePayload payload = new FetchPostsResponsePayload(posts, site, getPages,
                                offset > 0, canLoadMore);

                        if (posts != null) {
                            mDispatcher.dispatch(PostActionBuilder.newFetchedPostsAction(payload));
                        } else {
                            // TODO: do nothing or dispatch error?
                        }
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Implement lower-level catching in BaseXMLRPCClient
                    }
                }
        );

        add(request);
    }

    public void pushPost(final PostModel post, final SiteModel site, final UploadMode uploadMode) {
        if (TextUtils.isEmpty(post.getStatus())) {
            post.setStatus(PostStatus.toString(PostStatus.PUBLISHED));
        }

        // TODO: Implement media processing, along with an upload progress event - the event recipient will also be responsible for tracking hasImage, hasVideo, etc for analytics
        String descriptionContent = post.getDescription();
        //String descriptionContent = processPostMedia(post.getDescription());

        String moreContent = "";
        if (!TextUtils.isEmpty(post.getMoreText())) {
            moreContent = post.getMoreText();
            // TODO: Media processing
            //moreContent = processPostMedia(post.getMoreText());
        }

        // TODO: Media processing
        // If media file upload failed, let's stop here and prompt the user
//        if (mIsMediaError) {
//            return false;
//        }

        JSONArray categoriesJsonArray = post.getJSONCategories();
        String[] postCategories = null;
        if (categoriesJsonArray != null) {
            postCategories = new String[categoriesJsonArray.length()];
            for (int i = 0; i < categoriesJsonArray.length(); i++) {
                try {
                    postCategories[i] = TextUtils.htmlEncode(categoriesJsonArray.getString(i));
                } catch (JSONException e) {
                    AppLog.e(T.POSTS, e);
                }
            }
        }

        Map<String, Object> contentStruct = new HashMap<>();

        // Post format
        if (!post.isPage()) {
            if (!TextUtils.isEmpty(post.getPostFormat())) {
                contentStruct.put("wp_post_format", post.getPostFormat());
            }
        }

        contentStruct.put("post_type", (post.isPage()) ? "page" : "post");
        contentStruct.put("title", post.getTitle());
        long pubDate = post.getDateCreatedGmt();
        if (pubDate != 0) {
            Date date_created_gmt = new Date(pubDate);
            contentStruct.put("date_created_gmt", date_created_gmt);
            Date dateCreated = new Date(pubDate + (date_created_gmt.getTimezoneOffset() * 60000));
            contentStruct.put("dateCreated", dateCreated);
        }

        if (!TextUtils.isEmpty(moreContent)) {
            descriptionContent = descriptionContent.trim() + "<!--more-->" + moreContent;
            post.setMoreText("");
        }

        // get rid of the p and br tags that the editor adds.
        if (post.isLocalDraft()) {
            descriptionContent = descriptionContent.replace("<p>", "").replace("</p>", "\n").replace("<br>", "");
        }

        // gets rid of the weird character android inserts after images
        descriptionContent = descriptionContent.replaceAll("\uFFFC", "");

        contentStruct.put("description", descriptionContent);
        if (!post.isPage()) {
            contentStruct.put("mt_keywords", post.getKeywords());

            if (postCategories != null && postCategories.length > 0) {
                contentStruct.put("categories", postCategories);
            }
        }

        contentStruct.put("mt_excerpt", post.getExcerpt());
        contentStruct.put((post.isPage()) ? "page_status" : "post_status", post.getStatus());

        // Geolocation
        if (post.supportsLocation()) {
            JSONObject remoteGeoLatitude = post.getCustomField("geo_latitude");
            JSONObject remoteGeoLongitude = post.getCustomField("geo_longitude");
            JSONObject remoteGeoPublic = post.getCustomField("geo_public");

            Map<Object, Object> hLatitude = new HashMap<Object, Object>();
            Map<Object, Object> hLongitude = new HashMap<Object, Object>();
            Map<Object, Object> hPublic = new HashMap<Object, Object>();

            try {
                if (remoteGeoLatitude != null) {
                    hLatitude.put("id", remoteGeoLatitude.getInt("id"));
                }

                if (remoteGeoLongitude != null) {
                    hLongitude.put("id", remoteGeoLongitude.getInt("id"));
                }

                if (remoteGeoPublic != null) {
                    hPublic.put("id", remoteGeoPublic.getInt("id"));
                }

                if (post.hasLocation()) {
                    PostLocation location = post.getPostLocation();
                    hLatitude.put("key", "geo_latitude");
                    hLongitude.put("key", "geo_longitude");
                    hPublic.put("key", "geo_public");
                    hLatitude.put("value", location.getLatitude());
                    hLongitude.put("value", location.getLongitude());
                    hPublic.put("value", 1);
                }
            } catch (JSONException e) {
                AppLog.e(T.EDITOR, e);
            }

            if (!hLatitude.isEmpty() && !hLongitude.isEmpty() && !hPublic.isEmpty()) {
                Object[] geo = {hLatitude, hLongitude, hPublic};
                contentStruct.put("custom_fields", geo);
            }
        }

        // Featured images
        if (uploadMode.equals(UploadMode.MEDIA_WITH_POST)) {
            // Support for legacy editor - images are identified as featured as they're being uploaded with the post
            if (mFeaturedImageId != -1) {
                contentStruct.put("wp_post_thumbnail", mFeaturedImageId);
            }
        } else if (post.featuredImageHasChanged()) {
            if (post.getFeaturedImageId() < 1 && !post.isLocalDraft()) {
                // The featured image was removed from a live post
                contentStruct.put("wp_post_thumbnail", "");
            } else {
                contentStruct.put("wp_post_thumbnail", post.getFeaturedImageId());
            }
        }

        contentStruct.put("wp_password", post.getPassword());

        List<Object> params = new ArrayList<>(5);
        if (post.isLocalDraft()) {
            params.add(site.getDotOrgSiteId());
        } else {
            params.add(post.getRemotePostId());
        }
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(contentStruct);
        params.add(false);

        final XMLRPC method = (post.isLocalDraft() ? XMLRPC.NEW_POST : XMLRPC.EDIT_POST);

        // TODO: Send PostUploadStarted event
        //EventBus.getDefault().post(new PostUploadStarted(mPost.getLocalTableBlogId()));

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), method, params, new Listener() {
            @Override
            public void onResponse(Object response) {
                if (method.equals(XMLRPC.NEW_POST) && response instanceof String) {
                    post.setRemotePostId(Integer.valueOf((String) response));
                }
                post.setIsLocalDraft(false);
                post.setIsLocallyChanged(false);

                mDispatcher.dispatch(PostActionBuilder.newPushedPostAction(post));

                // TODO: Move analytics that were dropped to WPAndroid

                // Request a fresh copy of the uploaded post from the server to ensure local copy matches server
                getPost(post, site);
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Implement lower-level catching in BaseXMLRPCClient
            }
        });

        add(request);
    }

    public void deletePost(final PostModel post, final SiteModel site) {
        List<Object> params = new ArrayList<>(4);
        params.add(site.getDotOrgSiteId());
        params.add(site.getUsername());
        params.add(site.getPassword());
        params.add(post.getRemotePostId());

        XMLRPC method = (post.isPage() ? XMLRPC.DELETE_PAGE : XMLRPC.DELETE_POST);

        final XMLRPCRequest request = new XMLRPCRequest(site.getXmlRpcUrl(), method, params, new Listener() {
            @Override
            public void onResponse(Object response) {}
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Implement lower-level catching in BaseXMLRPCClient
            }
        });

        add(request);
    }

    private PostsModel postsResponseToPostsModel(Object[] response, SiteModel site, boolean isPage, int startPosition) {
        List<Map<?, ?>> postsList = new ArrayList<>();
        for (int ctr = startPosition; ctr < response.length; ctr++) {
            Map<?, ?> postMap = (Map<?, ?>) response[ctr];
            postsList.add(postMap);
        }

        PostsModel posts = new PostsModel();
        PostModel post;

        for (Object postObject : postsList) {
            post = postResponseObjectToPostModel(postObject, site, isPage);
            if (post != null) {
                posts.add(post);
            }
        }

        if (posts.isEmpty()) {
            return null;
        }

        return posts;
    }

    private PostModel postResponseObjectToPostModel(Object postObject, SiteModel site, boolean isPage) {
        // Sanity checks
        if (!(postObject instanceof Map)) {
            return null;
        }

        Map<?, ?> postMap = (Map<?, ?>) postObject;
        PostModel post = new PostModel();

        String postID = MapUtils.getMapStr(postMap, (isPage) ? "page_id" : "postid");
        if (TextUtils.isEmpty(postID)) {
            // If we don't have a post or page ID, move on
            return null;
        }

        post.setLocalSiteId(site.getId());
        post.setRemotePostId(Integer.valueOf(postID));
        post.setTitle(MapUtils.getMapStr(postMap, "title"));

        Date dateCreated = MapUtils.getMapDate(postMap, "dateCreated");
        if (dateCreated != null) {
            post.setDateCreated(dateCreated.getTime());
        } else {
            Date now = new Date();
            post.setDateCreated(now.getTime());
        }

        Date dateCreatedGmt = MapUtils.getMapDate(postMap, "date_created_gmt");
        if (dateCreatedGmt != null) {
            post.setDateCreatedGmt(dateCreatedGmt.getTime());
        } else {
            dateCreatedGmt = new Date(post.getDateCreated());
            post.setDateCreatedGmt(dateCreatedGmt.getTime() + (dateCreatedGmt.getTimezoneOffset() * 60000));
        }


        post.setDescription(MapUtils.getMapStr(postMap, "description"));
        post.setLink(MapUtils.getMapStr(postMap, "link"));
        post.setPermaLink(MapUtils.getMapStr(postMap, "permaLink"));

        Object[] postCategories = (Object[]) postMap.get("categories");
        JSONArray jsonCategoriesArray = new JSONArray();
        if (postCategories != null) {
            for (Object postCategory : postCategories) {
                jsonCategoriesArray.put(postCategory.toString());
            }
        }
        post.setCategories(jsonCategoriesArray.toString());

        Object[] custom_fields = (Object[]) postMap.get("custom_fields");
        JSONArray jsonCustomFieldsArray = new JSONArray();
        if (custom_fields != null) {
            PostLocation postLocation = new PostLocation();
            for (Object custom_field : custom_fields) {
                jsonCustomFieldsArray.put(custom_field.toString());
                // Update geo_long and geo_lat from custom fields
                if (!(custom_field instanceof Map))
                    continue;
                Map<?, ?> customField = (Map<?, ?>) custom_field;
                if (customField.get("key") != null && customField.get("value") != null) {
                    if (customField.get("key").equals("geo_longitude"))
                        postLocation.setLongitude(Long.valueOf(customField.get("value").toString()));
                    if (customField.get("key").equals("geo_latitude"))
                        postLocation.setLatitude(Long.valueOf(customField.get("value").toString()));
                }
            }
            post.setPostLocation(postLocation);
        }
        post.setCustomFields(jsonCustomFieldsArray.toString());

        post.setExcerpt(MapUtils.getMapStr(postMap, (isPage) ? "excerpt" : "mt_excerpt"));
        post.setMoreText(MapUtils.getMapStr(postMap, (isPage) ? "text_more" : "mt_text_more"));

        post.setAllowComments((MapUtils.getMapInt(postMap, "mt_allow_comments", 0)) != 0);
        post.setAllowPings((MapUtils.getMapInt(postMap, "mt_allow_pings", 0)) != 0);
        post.setSlug(MapUtils.getMapStr(postMap, "wp_slug"));
        post.setPassword(MapUtils.getMapStr(postMap, "wp_password"));
        post.setAuthorId(MapUtils.getMapStr(postMap, "wp_author_id"));
        post.setAuthorDisplayName(MapUtils.getMapStr(postMap, "wp_author_display_name"));
        post.setFeaturedImageId(MapUtils.getMapInt(postMap, "wp_post_thumbnail"));
        post.setStatus(MapUtils.getMapStr(postMap, (isPage) ? "page_status" : "post_status"));
        post.setUserId(Integer.valueOf(MapUtils.getMapStr(postMap, "userid")));

        if (isPage) {
            post.setIsPage(true);
            post.setPageParentId(MapUtils.getMapStr(postMap, "wp_page_parent_id"));
            post.setPageParentTitle(MapUtils.getMapStr(postMap, "wp_page_parent_title"));
        } else {
            post.setKeywords(MapUtils.getMapStr(postMap, "mt_keywords"));
            post.setPostFormat(MapUtils.getMapStr(postMap, "wp_post_format"));
        }

        return post;
    }
}
