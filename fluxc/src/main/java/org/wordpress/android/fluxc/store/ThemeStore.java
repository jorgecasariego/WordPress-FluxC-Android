package org.wordpress.android.fluxc.store;

import android.support.annotation.NonNull;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.Payload;
import org.wordpress.android.fluxc.action.ThemeAction;
import org.wordpress.android.fluxc.annotations.action.Action;
import org.wordpress.android.fluxc.annotations.action.IAction;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.ThemeModel;
import org.wordpress.android.fluxc.network.rest.wpcom.theme.ThemeRestClient;
import org.wordpress.android.fluxc.persistence.ThemeSqlUtils;
import org.wordpress.android.util.AppLog;

import java.util.List;

import javax.inject.Inject;

public class ThemeStore extends Store {
    // Payloads
    public static class FetchedCurrentThemePayload extends Payload {
        public SiteModel site;
        public ThemeModel theme;
        public FetchThemesError error;

        public FetchedCurrentThemePayload(FetchThemesError error) {
            this.error = error;
        }

        public FetchedCurrentThemePayload(@NonNull SiteModel site, @NonNull ThemeModel theme) {
            this.site = site;
            this.theme = theme;
        }
    }

    public static class FetchedThemesPayload extends Payload {
        public SiteModel site;
        public List<ThemeModel> themes;
        public FetchThemesError error;

        public FetchedThemesPayload(FetchThemesError error) {
            this.error = error;
        }

        public FetchedThemesPayload(@NonNull SiteModel site, @NonNull List<ThemeModel> themes) {
            this.site = site;
            this.themes = themes;
        }
    }

    public static class ActivateThemePayload extends Payload {
        public SiteModel site;
        public ThemeModel theme;
        public ActivateThemeError error;

        public ActivateThemePayload(ActivateThemeError error) {
            this.error = error;
        }

        public ActivateThemePayload(SiteModel site, ThemeModel theme) {
            this.site = site;
            this.theme = theme;
        }
    }

    public enum ThemeErrorType {
        GENERIC_ERROR,
        UNAUTHORIZED,
        NOT_AVAILABLE
    }

    public static class FetchThemesError implements OnChangedError {
        public ThemeErrorType type;
        public String message;
        public FetchThemesError(ThemeErrorType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    public static class ActivateThemeError implements OnChangedError {
        public ThemeErrorType type;
        public String message;

        public ActivateThemeError(ThemeErrorType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    public static class OnThemesChanged extends OnChanged<FetchThemesError> {
        public SiteModel site;

        public OnThemesChanged(SiteModel site) {
            this.site = site;
        }
    }

    public static class OnCurrentThemeFetched extends OnChanged<FetchThemesError> {
        public SiteModel site;
        public ThemeModel theme;

        public OnCurrentThemeFetched(SiteModel site, ThemeModel theme) {
            this.site = site;
            this.theme = theme;
        }
    }

    public static class OnThemeActivated extends OnChanged<ActivateThemeError> {
        public SiteModel site;
        public ThemeModel theme;

        public OnThemeActivated(SiteModel site, ThemeModel theme) {
            this.site = site;
            this.theme = theme;
        }
    }

    private final ThemeRestClient mThemeRestClient;

    @Inject
    public ThemeStore(Dispatcher dispatcher, ThemeRestClient themeRestClient) {
        super(dispatcher);
        mThemeRestClient = themeRestClient;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Override
    public void onAction(Action action) {
        IAction actionType = action.getType();
        if (!(actionType instanceof ThemeAction)) {
            return;
        }
        switch ((ThemeAction) actionType) {
            case FETCH_WP_COM_THEMES:
                fetchWpThemes();
                break;
            case FETCHED_WP_COM_THEMES:
                handleWpThemesFetched((FetchedThemesPayload) action.getPayload());
                break;
            case FETCH_INSTALLED_THEMES:
                fetchInstalledThemes((SiteModel) action.getPayload());
                break;
            case FETCHED_INSTALLED_THEMES:
                handleInstalledThemesFetched((FetchedThemesPayload) action.getPayload());
                break;
            case FETCH_CURRENT_THEME:
                fetchCurrentTheme((SiteModel) action.getPayload());
                break;
            case FETCHED_CURRENT_THEME:
                handleCurrentThemeFetched((FetchedCurrentThemePayload) action.getPayload());
                break;
            case ACTIVATE_THEME:
                activateTheme((ActivateThemePayload) action.getPayload());
                break;
            case ACTIVATED_THEME:
                handleThemeActivated((ActivateThemePayload) action.getPayload());
                break;
        }
    }

    @Override
    public void onRegister() {
        AppLog.d(AppLog.T.API, "ThemeStore onRegister");
    }

    public List<ThemeModel> getWpThemes() {
        return ThemeSqlUtils.getThemesForSite(null);
    }

    public List<ThemeModel> getThemesForSite(@NonNull SiteModel site) {
        return ThemeSqlUtils.getThemesForSite(site);
    }

    private void fetchWpThemes() {
        mThemeRestClient.fetchWpComThemes();
    }

    private void handleWpThemesFetched(FetchedThemesPayload payload) {
        OnThemesChanged event = new OnThemesChanged(payload.site);
        if (payload.isError()) {
            event.error = payload.error;
        } else {
            ThemeSqlUtils.insertOrReplaceWpThemes(payload.themes);
        }
        emitChange(event);
    }

    private void fetchInstalledThemes(@NonNull SiteModel site) {
        if (site.isJetpackConnected() && site.isUsingWpComRestApi()) {
            mThemeRestClient.fetchJetpackInstalledThemes(site);
        } else {
            FetchThemesError error = new FetchThemesError(ThemeErrorType.NOT_AVAILABLE, null);
            FetchedThemesPayload payload = new FetchedThemesPayload(error);
            handleInstalledThemesFetched(payload);
        }
    }

    private void handleInstalledThemesFetched(FetchedThemesPayload payload) {
        OnThemesChanged event = new OnThemesChanged(payload.site);
        if (payload.isError()) {
            event.error = payload.error;
        } else {
            ThemeSqlUtils.insertOrReplaceInstalledThemes(payload.site, payload.themes);
        }
        emitChange(event);
    }

    private void fetchCurrentTheme(@NonNull SiteModel site) {
        if (site.isUsingWpComRestApi()) {
            mThemeRestClient.fetchCurrentTheme(site);
        } else {
            FetchThemesError error = new FetchThemesError(ThemeErrorType.NOT_AVAILABLE, null);
            FetchedCurrentThemePayload payload = new FetchedCurrentThemePayload(error);
            handleCurrentThemeFetched(payload);
        }
    }

    private void handleCurrentThemeFetched(FetchedCurrentThemePayload payload) {
        OnCurrentThemeFetched event = new OnCurrentThemeFetched(payload.site, payload.theme);
        if (payload.isError()) {
            event.error = payload.error;
        } else {
            ThemeSqlUtils.insertOrUpdateTheme(payload.theme);
        }
        emitChange(event);
    }

    private void activateTheme(@NonNull ActivateThemePayload payload) {
        if (payload.site.isUsingWpComRestApi()) {
            mThemeRestClient.activateTheme(payload.site, payload.theme);
        } else {
            payload.error = new ActivateThemeError(ThemeErrorType.NOT_AVAILABLE, null);
            handleThemeActivated(payload);
        }
    }

    private void handleThemeActivated(@NonNull ActivateThemePayload payload) {
        OnThemeActivated event = new OnThemeActivated(payload.site, payload.theme);
        if (payload.isError()) {
            event.error = payload.error;
        } else {
            // TODO update local db?
        }
        emitChange(event);
    }
}
