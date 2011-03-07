package org.example.android.api;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.example.android.api.entity.MixiPerson;
import org.example.android.network.ApiRequestUtils;
import org.example.android.oauth.OAuthClient;
import org.example.android.oauth.OAuthTokenStore;
import org.example.android.oauth.TokenExpiredException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

/** mixi Graph API の People API クライアント実装。 (サンプル)
 * 自分自身の友人一覧を取得可能。
 */
public class PeopleApiClient {
    
    private static final String ENDPOINT_URL = "http://api.mixi-platform.com/2/people/@me/@friends";
    private final OAuthTokenStore mTokenStore;
    
    /** People API クライアントのコンストラクタ。
     * @param context Context
     */
    public PeopleApiClient(Context context) {
        mTokenStore = new OAuthTokenStore(context);
    }
    
    /**
     * 認可ユーザー自身の友人一覧を取得する。 /@me/@friends を指定されたクエリで呼び出す。
     * @param startIndex 取得開始するインデックス
     * @param count 取得件数
     * @return {@link PeopleApiResponse}
     * @throws ClientProtocolException
     * @throws IOException
     */
    public PeopleApiResponse getFriends(int startIndex, int count)
            throws ClientProtocolException, IOException {
        ArrayList<NameValuePair> request = new ArrayList<NameValuePair>();
        request.add(new BasicNameValuePair("startIndex", String.valueOf(startIndex)));
        request.add(new BasicNameValuePair("count", String.valueOf(count)));
        return ApiRequestUtils.doGetRequest(ENDPOINT_URL, request,
                new PeopleApiReponseHandler(), mTokenStore);
    }
    
    /** People API のレスポンスをパースし、 PeopleApiResponse として返す
     */
    private static class PeopleApiReponseHandler
            implements ResponseHandler<PeopleApiResponse> {
        private static final String TAG = "PeopleApiResponseHandler";

        @Override
        public PeopleApiResponse handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
            if (response == null)
                throw new NullPointerException("response is null");
            int statusCode = response.getStatusLine().getStatusCode();
            switch (statusCode) {
                case 401:   // Authorization Required
                    boolean retryable = OAuthClient.isTokenExpiredResponse(response);
                    throw new TokenExpiredException("invalid token", retryable);
                case 200:   // OK
                    return parsePeopleFromResponse(EntityUtils.toString(response.getEntity(),
                            HTTP.UTF_8));
            }
            throw new HttpResponseException(statusCode, "unexpected response: "
                    + response.getStatusLine().toString() + ": "
                    + EntityUtils.toString(response.getEntity()));
        }

        /** String の JSON レスポンスをパースして {@link PeopleApiResponse} を返す。
         * 
         * @param string JSON を含む文字列
         * @return {@link PeopleApiResponse}
         */
        private PeopleApiResponse parsePeopleFromResponse(String string) {
            PeopleApiResponse res = new PeopleApiResponse();
            ArrayList<MixiPerson> people = new ArrayList<MixiPerson>();
            try {
                JSONObject json = new JSONObject(string);
                res.itemsPerPage = json.getInt("itemsPerPage");
                res.startIndex = json.getInt("startIndex");
                res.totalResults = json.getInt("totalResults");
                JSONArray entries = json.getJSONArray("entry");
                int count = entries.length();
                for (int i = 0; i < count; i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    MixiPerson person = new MixiPerson();
                    person.displayName = entry.getString("displayName");
                    person.profileUrl  = entry.getString("profileUrl");
                    people.add(person);
                }
                res.entry = people;
                return res;
            } catch (JSONException e) {
                Log.w(TAG, "something went wrong while parsing json", e);
            }
            return null;
        }
    }
}
