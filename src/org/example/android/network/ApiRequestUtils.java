package org.example.android.network;

import java.io.IOException;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.example.android.oauth.OAuthClient;
import org.example.android.oauth.OAuthToken;
import org.example.android.oauth.OAuthTokenStore;
import org.example.android.oauth.TokenExpiredException;
import org.example.android.oauth.TokenInvalidException;

import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.util.Log;

/** API リクエストの発行に使用するユーティリティクラス。
 * Access Token を自動的にリクエストに付与するほか、トークンのリフレッシュを自動的に行う。
 */
public class ApiRequestUtils {
    static final String USER_AGENT = "AndroidOAuthExample/0.1";
    private static final String TAG = "ApiRequestUtils";
    
    /** HTTP GET リクエストを発行する。
     * 
     * @param <T> 期待するレスポンスの型
     * @param endpointUrl リクエスト先のURL
     * @param query クエリパラメータを含む{@link NameValuePair}のリスト
     * @param responseHandler レスポンスハンドラ
     * @return リクエストが成功した場合、レスポンスハンドラによって処理されたレスポンスが返る
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static <T> T doGetRequest(String endpointUrl, List<NameValuePair> query,
            ResponseHandler<T> responseHandler)
            throws ClientProtocolException, IOException {
        return doGetRequest(endpointUrl, query, responseHandler, null);
    }

    /** HTTP GET リクエストを発行する。
     * リクエスト時には store に格納された Access Token を Authorization ヘッダに付与する。
     * Access Token の有効期限が既に切れている場合は、自動的にリフレッシュを試みる。
     * リフレッシュできなかった場合は {@link TokenInvalidException} を throw する。
     * 
     * @param <T> 期待するレスポンスの型
     * @param endpointUrl リクエスト先のURL
     * @param query クエリパラメータを含む{@link NameValuePair}のリスト
     * @param responseHandler レスポンスハンドラ
     * @param store OAuth の Access Token を保持している OAuthTokenStore
     * @return リクエストが成功した場合、レスポンスハンドラによって処理されたレスポンスが返る
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static <T> T doGetRequest(String endpointUrl, List<NameValuePair> query,
            ResponseHandler<T> responseHandler, OAuthTokenStore store)
            throws ClientProtocolException, IOException {
        Uri.Builder uriBuilder = Uri.parse(endpointUrl).buildUpon();
        if (query != null) {
            for (NameValuePair nvp : query) {
                uriBuilder.appendQueryParameter(nvp.getName(), nvp.getValue());
            }
        }
        HttpGet request = new HttpGet(uriBuilder.build().toString());
        if (store == null) {
            return executeRequest(request, responseHandler);
        } else {
            return executeRequestWithRefresh(request, responseHandler, store);
        }
    }

    /** HTTP POST リクエストを発行する。
     * 
     * @param <T> 期待するレスポンスの型
     * @param endpointUrl リクエスト先のURL
     * @param query クエリパラメータを含む{@link NameValuePair}のリスト
     * @param responseHandler レスポンスハンドラ
     * @return リクエストが成功した場合、レスポンスハンドラによって処理されたレスポンスが返る
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static <T> T doPostRequest(String endpointUrl, List<NameValuePair> body,
            ResponseHandler<T> responseHandler)
            throws ClientProtocolException, IOException {
        return doPostRequest(endpointUrl, body, responseHandler, null);
    }
    
    /** HTTP POST リクエストを発行する。
     * リクエスト時には store に格納された Access Token を Authorization ヘッダに付与する。
     * Access Token の有効期限が既に切れている場合は、自動的にリフレッシュを試みる。
     * リフレッシュできなかった場合は {@link TokenInvalidException} を throw する。
     * 
     * @param <T> 期待するレスポンスの型
     * @param endpointUrl リクエスト先のURL
     * @param query クエリパラメータを含む{@link NameValuePair}のリスト
     * @param responseHandler レスポンスハンドラ
     * @param store OAuth の Access Token を保持している OAuthTokenStore
     * @return リクエストが成功した場合、レスポンスハンドラによって処理されたレスポンスが返る
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static <T> T doPostRequest(String endpointUrl, List<NameValuePair> body,
            ResponseHandler<T> responseHandler, OAuthTokenStore store)
            throws ClientProtocolException, IOException {
        HttpPost request = new HttpPost(endpointUrl);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(body, HTTP.UTF_8);
        request.setEntity(entity);
        if (store == null) {
            return executeRequest(request, responseHandler);
        } else {
            return executeRequestWithRefresh(request, responseHandler, store);
        }
    }
    
    
    /**
     * {@link OAuthTokenStore} 内の Access Token を使用してリクエストを行う。
     * {@link ResponseHandler} で Access Token が期限切れと判明した場合は、
     * {@link TokenExpiredException} を throw することで、自動的に Refresh Token を用いた
     * トークンの再発行を行い、リクエストを再試行する。トークンのリフレッシュに失敗した場合は
     * {@link TokenExpiredException}, トークンが無効な場合は {@link TokenInvalidException} を throw する。
     * 
     * @param <T> 期待する戻り値の型
     * @param request リクエスト内容
     * @param responseHandler レスポンスを処理して期待した型を返す {@link ResponseHandler}
     * @param store 使用する OAuthTokenStore
     * @return リクエストに成功した場合はそのレスポンスを返す
     * @throws ClientProtocolException
     * @throws IOException
     */
    private static <T> T executeRequestWithRefresh(HttpRequestBase request,
            ResponseHandler<T> responseHandler, OAuthTokenStore store)
            throws ClientProtocolException, IOException {
        return executeRequestWithRefresh(request, responseHandler, store, false);
    }

    /**
     * {@link OAuthTokenStore} 内の Access Token を使用してリクエストを行う。
     * {@link ResponseHandler} で Access Token が期限切れと判明した場合は、
     * {@link TokenExpiredException} を throw することで、自動的に Refresh Token を用いた
     * トークンの再発行を行い、リクエストを再試行する。トークンのリフレッシュに失敗した場合は
     * {@link TokenExpiredException}, トークンが無効な場合は {@link TokenInvalidException} を throw する。
     * 
     * @param <T> 期待する戻り値の型
     * @param request リクエスト内容
     * @param responseHandler レスポンスを処理して期待した型を返す {@link ResponseHandler}
     * @param store 使用する OAuthTokenStore
     * @param isRetry このリクエストが再試行の場合は true を指定する
     * @return リクエストに成功した場合はそのレスポンスを返す
     * @throws ClientProtocolException
     * @throws IOException
     */
    private static <T> T executeRequestWithRefresh(HttpRequestBase request,
            ResponseHandler<T> responseHandler, OAuthTokenStore store, boolean isRetry)
            throws ClientProtocolException, IOException {
        OAuthToken token = null;
        Header authorizationHeader = null;
        if (store != null) {
        	token = getValidAccessToken(store);
        	if (token != null) {
                authorizationHeader = 
                	new BasicHeader("Authorization", "OAuth " + token.accessToken);
                request.addHeader(authorizationHeader);
        	}
        }
        try {
            return executeRequest(request, responseHandler);
        } catch (TokenExpiredException e) {
            if (!e.isRetryable()) {
                Log.w(TAG, "Access token is invalid.");
            } else {
                if (isRetry) {
                    Log.e(TAG, "An error occured while trying to refresh the access token.");
                } else {
                    // try to refresh
                    Log.v(TAG, "Access token has been expired. Trying to refresh.");
                    if (authorizationHeader != null)
                        request.removeHeader(authorizationHeader);
                    store.setToken(OAuthClient.refreshToken(token.refreshToken));
                    return executeRequestWithRefresh(request, responseHandler, store, true);
                }
            }
            throw e;
        }
    }
   
    /** HttpClient のインスタンスを生成し、 HTTP リクエストを行う。
     * 
     * @param <T> 期待するレスポンスの型
     * @param request リクエスト内容
     * @param responseHandler 期待する型を返す {@link ResponseHandler}
     * @return リクエストに成功した場合はその内容を返す。
     * @throws ClientProtocolException
     * @throws IOException
     */
    private static <T> T executeRequest(HttpRequestBase request, ResponseHandler<T> responseHandler)
            throws ClientProtocolException, IOException {
        AndroidHttpClient client = AndroidHttpClient.newInstance(USER_AGENT);
        try {
            return client.execute(request, responseHandler);
        } finally {
            client.close();
        }
    }
    
    /**
     * {@link OAuthTokenStore} から Access Token を取得して返す。既に期限が切れていた場合はリフレッシュを試みる。
     * 
     * @param store OAuthTokenStore のインスタンス
     * @return 現時点で有効な Access Token
     * @throws ClientProtocolException
     * @throws IOException
     */
	private static OAuthToken getValidAccessToken(OAuthTokenStore store)
			throws ClientProtocolException, IOException {
        OAuthToken token = store.getToken();
        if (token != null && token.accessToken != null) {
            if (token.expiresOn < System.currentTimeMillis()) {
                // expired, try to refresh first
                token = OAuthClient.refreshToken(token.refreshToken);
                // save
                store.setToken(token);
            }
        }
        return token;
	}
}
