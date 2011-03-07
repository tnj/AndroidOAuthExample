package org.example.android.oauth;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.example.android.network.ApiRequestUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class OAuthClient {
    
    private static final String TAG = "OAuthClient";

    private static final String AUTHORIZE_URL = "https://mixi.jp/connect_authorize.pl";
    private static final String API_ENDPOINT  = "https://secure.mixi-platform.com/2/token";
    
    /** TODO 独自の package name に書き換える */
    private static final String REDIRECT_URL = "org.example.android.oauth://callback";
    /** TODO 必要な scope に書き換える */
    private static final String[] SCOPE = { "r_profile" };

    /** TODO consumer secret はそのまま書かずに難読化する処理を入れる */
    private static class EncodedConsumerKey {
        /** TODO set your consumer key */
        private static final String ENCRYPTED_CONSUMER_KEY = "";
        /** TODO set your consumer secret */
        private static final String ENCRYPTED_CONSUMER_SECRET = "";
        
        public static String getClientId() {
            return decode(ENCRYPTED_CONSUMER_KEY);
        }
        public static String getClientSecret() {
            return decode(ENCRYPTED_CONSUMER_SECRET);
        }
        private static String decode(final String encrypted) {
            // TODO ここに難読化された consumer secret のデコード処理を実装
            return encrypted;
        }
    }

    /** ブラウザを起動し、認証認可手順を開始するための URL へ遷移する。
     * @param context 呼び出し元の {@link Context}
     */
    public static void initiateLoginProcess(Context context) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(buildAuthorizeUri());
        context.startActivity(i);
    }
    
    /** 認可ページの URL を生成する。 */ 
    private static Uri buildAuthorizeUri() {
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", EncodedConsumerKey.getClientId())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", TextUtils.join(" ", SCOPE).toString())
            .build();
    }

    /**
     * Authorization Code から {@link OAuthToken} を取得する。
     * 
     * @param code Authorization Code
     * @return 取得したトークンを返す。
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static OAuthToken getTokenFromAuthorizationCode(String code)
            throws ClientProtocolException, IOException {
        ArrayList<NameValuePair> request = new ArrayList<NameValuePair>();
        request.add(new BasicNameValuePair("grant_type", "authorization_code"));
        request.add(new BasicNameValuePair("client_id", EncodedConsumerKey.getClientId()));
        request.add(new BasicNameValuePair("client_secret", EncodedConsumerKey.getClientSecret()));
        request.add(new BasicNameValuePair("redirect_uri", REDIRECT_URL));
        request.add(new BasicNameValuePair("code", code));
        Log.v(TAG, "Retrieving token");
        return ApiRequestUtils.doPostRequest(API_ENDPOINT, request, new TokenReponseHandler());
    }
    
    /**
     * Refresh Token を用いてトークンのリフレッシュを行う。
     * 
     * @param refreshToken Refresh Token
     * @return 取得したトークンを返す。
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static OAuthToken refreshToken(String refreshToken)
            throws ClientProtocolException, IOException {
        ArrayList<NameValuePair> request = new ArrayList<NameValuePair>();
        request.add(new BasicNameValuePair("grant_type", "refresh_token"));
        request.add(new BasicNameValuePair("client_id", EncodedConsumerKey.getClientId()));
        request.add(new BasicNameValuePair("client_secret", EncodedConsumerKey.getClientSecret()));
        request.add(new BasicNameValuePair("refresh_token", refreshToken));
        Log.v(TAG, "Refreshing token");
        return ApiRequestUtils.doPostRequest(API_ENDPOINT, request, new TokenReponseHandler());
    }
    
    /** エンドポイントからのレスポンスをパースし、 OAuthToken として返す
     */
    private static class TokenReponseHandler
            implements ResponseHandler<OAuthToken> {
        private static final String TAG = "TokenResponseHandler";

        @Override
        public OAuthToken handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            if (response == null)
                throw new NullPointerException("response is null");
            int statusCode = response.getStatusLine().getStatusCode();
            switch (statusCode) {
            	case 401:
            		Log.w(TAG, "authentation failed");
            		throw new TokenInvalidException(getOAuthError(response));
                case 200:
                    Log.v(TAG, "got response successfully");
                    return parseTokenFromResponse(EntityUtils.toString(response.getEntity(),
                            HTTP.UTF_8));
            }
            throw new HttpResponseException(statusCode, "unexpected response: "
                    + response.getStatusLine().toString() + ": "
                    + EntityUtils.toString(response.getEntity()));
        }

        private OAuthToken parseTokenFromResponse(String string) {
            try {
                JSONObject json = new JSONObject(string);
                OAuthToken token = new OAuthToken();
                token.accessToken = json.getString("access_token");
                token.refreshToken = json.getString("refresh_token");
                token.expiresOn = System.currentTimeMillis() + json.getLong("expires_in") * 1000;
                return token;
            } catch (JSONException e) {
                Log.w(TAG, "something went wrong while parsing json", e);
                return null;
            }
        }
    }
    
    /** Access Token の expire による 401 エラーかどうかを判定する。
     * true の場合は refresh 後に再試行が可能。
     * 
     * @param response 判定する {@link HttpResponse}
     * @return expire によるエラーの場合 true を返す。
     */
    public static boolean isTokenExpiredResponse(HttpResponse response) {
        String error = getOAuthError(response);
        return error != null && error.contains("expired_token");
    }
    /** {@link HttpResponse} から OAuth の認証エラーメッセージを取得する。
     * 
     * @param response パースする {@link HttpResponse}
     * @return メッセージがある場合はその文字列、なければ null を返す。
     */
    public static String getOAuthError(HttpResponse response) {
        Header result = response.getFirstHeader("WWW-Authenticate");
        if (result == null) return null;
        for (HeaderElement element : result.getElements()) {
            if (element.getName().equals("OAuth error")) {
                return element.getValue();
            }
        }
        return null;
    }
}
