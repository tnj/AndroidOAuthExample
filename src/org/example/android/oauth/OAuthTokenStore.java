package org.example.android.oauth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Do not call methods in this class from UI thread. Every get/set methods using
 * SharedPreferences to read/save the tokens and sometimes they stuck for a
 * while unexpectedly that causes ANR.
 */
public class OAuthTokenStore {
    private final Context mApplicationContext;
    private OAuthToken mToken;
    public OAuthTokenStore(Context context) {
        mApplicationContext = context.getApplicationContext();
        mToken = readTokenFromSharedPreferences();
    }
    public OAuthToken getToken() {
        return mToken;
    }
    public boolean hasToken() {
        return mToken.accessToken != null;
    }
    public boolean isTokenExpired() {
        return mToken.expiresOn < System.currentTimeMillis();
    }
    public void setToken(OAuthToken token) {
        if (token == null)
            throw new NullPointerException("token must not be null");
        mToken = token;
        saveToken();
    }
    public void setToken(String accessToken, String refreshToken, long expiresIn) {
        if (accessToken == null || refreshToken == null)
            throw new NullPointerException("token must not be null");
        mToken.accessToken = accessToken;
        mToken.refreshToken = refreshToken;
        mToken.expiresOn = System.currentTimeMillis() + expiresIn * 1000;
        saveToken();
    }
    public void updateAccessToken(String accessToken) {
        if (accessToken == null)
            throw new NullPointerException("access token must not be null");
        mToken.accessToken = accessToken;
        saveToken();
    }
    private OAuthToken readTokenFromSharedPreferences() {
        SharedPreferences pref = getPreferences();
        OAuthToken token = new OAuthToken();
        token.accessToken = pref.getString("accessToken", null);
        token.refreshToken = pref.getString("refreshToken", null);
        token.expiresOn = pref.getLong("expiredOn", 0);
        return token;
    }
    private void saveToken() {
        SharedPreferences pref = getPreferences();
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("accessToken", mToken.accessToken);
        editor.putString("refreshToken", mToken.refreshToken);
        editor.putLong("expiredOn", mToken.expiresOn);
        editor.commit();
    }
    public void clearToken() {
        SharedPreferences pref = getPreferences();
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();
        mToken = new OAuthToken();
    }
    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
    }
}
