package org.example.android;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.example.android.api.PeopleApiClient;
import org.example.android.api.PeopleApiResponse;
import org.example.android.api.entity.MixiPerson;
import org.example.android.oauth.OAuthClient;
import org.example.android.oauth.OAuthToken;
import org.example.android.oauth.OAuthTokenStore;
import org.example.android.oauth.TokenInvalidException;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends ListActivity
        implements OnItemClickListener {
    
    private static final String TAG = "MainActivity";
    private static final int DIALOG_PROGRESS = 1;
    
    private ListView mListView;
    private View mFooterView;
    private View mFooterLoadingView;
    
    private ArrayAdapter<MixiPerson> mAdapter;
    private PeopleLoaderTask mLoaderTask;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        setProgressBarIndeterminate(true);

        // リストの末尾要素 (続きを取得/読込中) の構成
        mFooterView = createFooterView();
        mFooterLoadingView = createFooterLoadingView();

        // リストの構成
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.addFooterView(mFooterView, null, true);
        
        // リストにセットするアダプタ
		mAdapter = new ArrayAdapter<MixiPerson>(this,
				android.R.layout.simple_list_item_1, android.R.id.text1);
        mListView.setAdapter(mAdapter);
        
        // まずインテントを処理して、何もなければトークン取得へ遷移
        if (!parseIntent(getIntent()))
            startLoadStoredToken();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        // インスタンスが生きている状態で新しい Intent を受け取った
        Log.v(TAG, "onNewIntent called: " + intent);
        parseIntent(intent);
    }    

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_PROGRESS:
                return ProgressDialog.show(this, getString(R.string.auth_progress_title),
                        getString(R.string.auth_progress_message), true);
        }
        return super.onCreateDialog(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()){
            case R.id.MenuLogout:
                clearLoginState();
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MixiPerson person = (MixiPerson) parent.getItemAtPosition(position);
        if (person != null) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse(person.profileUrl));
            startActivity(i);
        } else {
            // read more
            startLoadFromServer(parent.getCount() - 1);
        }
    }

    /** ListView 末尾の 続きを取得 の View を生成して返す
     * @return {@link ListView#addFooterView(View)} にセットする View
     */
    private View createFooterView() {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        TextView text2 = (TextView) view.findViewById(android.R.id.text2);
        text1.setText(R.string.footer_view_load_next_text);
        text2.setText(R.string.footer_view_load_next_description);
        return view;
    }
    
    /** ListView 末尾の 読み込み中 の View を生成して返す
     * @return {@link ListView#addFooterView(View)} にセットする View
     */
    private View createFooterLoadingView() {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        text1.setText(R.string.footer_view_loading_text);
        return view;
    }
    
    /** 保存済みの Access Token を読み込むタスクを開始する。 */
    private void startLoadStoredToken() {
        new TokenLoadTask().execute();
    }
    
    /** ログインボタンの表示状態を設定する。
     * @param isVisible true で表示、 false で非表示にする
     */
    private void setLoginButtonVisibility(boolean isVisible) {
        findViewById(R.id.login).setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /** ログインボタンが押された
     * @param v ログインボタンの View インスタンス
     */
    public void onLoginClick(View v) {
        initiateLoginProcess();
    }
    
    /** 友人一覧を取得するタスクを開始する。
     * @param start 取得する先頭の index
     */
    private void startLoadFromServer(int start) {
        if (mLoaderTask == null || !mLoaderTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
            mLoaderTask = new PeopleLoaderTask();
            mLoaderTask.execute(start);
        }
    }
    
    /** OAuth の認証フローを開始する。ブラウザで認可ページが開かれる。 */
    public void initiateLoginProcess() {
        OAuthClient.initiateLoginProcess(this);
    }
    
    /** Toast を表示
     * @param string 表示するメッセージ
     */
    private void showToast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    /** Toast を表示
     * @param resId 表示するメッセージのリソース ID
     */
    private void showToast(int resId) {
        showToast(getText(resId).toString());
    }

    /**
     * Intent から Authorization Code を取得し、取得できた場合は
     * {@link MainActivity.TokenRetriever} を呼び出す。
     * 
     * @param intent Intent
     * @return Authorization Code を取得し、 Access Token の取得処理を開始したら true, それ以外は
     *         false.
     */
    private boolean parseIntent(Intent intent) {
        String action = intent.getAction();
        if (action == null || !action.equals(Intent.ACTION_VIEW)){
            return false;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return false;
        }
        String code = uri.getQueryParameter("code");
        if (code == null) {
            showToast(R.string.auth_error_login_failed);
            return false;
        }
        // start token retriever task
        new TokenRetriever().execute(code);
        return true;
    }
    
    /** ログイン状態を破棄してログアウト状態に戻す。リストもクリアする。
     */
    public void clearLoginState() {
        OAuthTokenStore store = new OAuthTokenStore(MainActivity.this);
        store.clearToken();
        setLoginButtonVisibility(true);
        mAdapter.clear();
    }

    /**
     * Authorization Code から Access Token を取得し、 SharedPreference に保存するタスク。 実行中は
     * ProgressDialog を表示する。取得が完了すると {@link MainActivity#startLoadStoredToken()} を呼び出す。
     */
    /*package*/
    class TokenRetriever extends AsyncTask<String, Void, OAuthToken> {
        private static final String TAG = "TokenRetriever";
        @Override
        protected void onPreExecute() {
             super.onPreExecute();
             showDialog(DIALOG_PROGRESS);
        }
        @Override
        protected OAuthToken doInBackground(String... params) {
            if (params == null) {
                return null;
            }
            String code = params[0];
            try {
                return OAuthClient.getTokenFromAuthorizationCode(code);
            } catch (ClientProtocolException e) {
                Log.w(TAG, "failed to request", e);
            } catch (IOException e) {
                Log.w(TAG, "IOException", e);
            }
            return null;
        }
        @Override
        protected void onPostExecute(OAuthToken result) {
            super.onPostExecute(result);
            try {
                if (result == null) {
                    showToast(R.string.failed_to_authorize);
                } else {
                    OAuthTokenStore store = new OAuthTokenStore(MainActivity.this);
                    store.setToken(result);
                    startLoadStoredToken();
                }
            } finally {
                dismissDialog(DIALOG_PROGRESS);
            }
        }
    }

    /** トークンをローカルの SharedPreference から取得するタスク。
     * 取得できた場合はそのまま友人一覧の取得を開始する。
     * 取得できなかった場合は、ログインボタンを表示する。
     */
    /*package*/
    class TokenLoadTask extends AsyncTask<Void,Void,OAuthToken> {
        @Override
        protected OAuthToken doInBackground(Void... params) {
            OAuthTokenStore mTokenStore = new OAuthTokenStore(MainActivity.this);
            if (mTokenStore.hasToken()) {
                return mTokenStore.getToken();
            }
            return null;
        }
        @Override
        protected void onPostExecute(OAuthToken result) {
            if (result == null) {
                setLoginButtonVisibility(true);
            } else {
                setLoginButtonVisibility(false);
                startLoadFromServer(0);
            }
        }
    }
    
    /** 友人一覧を取得するタスク。
     * {@link #execute(Integer...)} の引数に取得開始インデックスを指定する。
     */
    /*package*/
    class PeopleLoaderTask extends AsyncTask<Integer,Void,PeopleApiResponse> {
        private static final int FETCH_COUNT_PER_REQUEST = 20;
        private String errorMessage = null;
        private boolean mNeedRelogin = false;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setProgressBarIndeterminateVisibility(true);
            showToast(R.string.retrieving_friend_list);
            
            // footer view の切り替え
            mListView.removeFooterView(mFooterView);
            mListView.addFooterView(mFooterLoadingView);
        }
        @Override
        protected PeopleApiResponse doInBackground(Integer... params) {
            int startIndex = params[0];
            PeopleApiClient client = new PeopleApiClient(MainActivity.this);
            try {
                return client.getFriends(startIndex, FETCH_COUNT_PER_REQUEST);
            } catch (TokenInvalidException e) {
                Log.w(TAG, "token is no longer valid");
                // トークンが無効になりリフレッシュもできない場合は再ログインが必要
            	mNeedRelogin = true; 
            } catch (ClientProtocolException e) {
                Log.w(TAG, "request failed", e);
                errorMessage = e.getLocalizedMessage();
            } catch (IOException e) {
                Log.w(TAG, "request failed", e);
                errorMessage = e.getLocalizedMessage();
            }
            return null;
        }
        @Override
        protected void onPostExecute(PeopleApiResponse result) {
            super.onPostExecute(result);
            setProgressBarIndeterminateVisibility(false);
            mListView.removeFooterView(mFooterLoadingView);
            
            if (result != null) {
                for (MixiPerson person : result.entry) {
                    mAdapter.add(person);
                }
                // more items?
                int remain = result.totalResults - mAdapter.getCount();
                if (remain > 0) {
                    showToast(String.format(getText(R.string.remaining_list_count).toString(),
                            remain));
                    mListView.addFooterView(mFooterView);
                }
            } else if (mNeedRelogin) {
            	showToast(R.string.need_relogin);
            	clearLoginState();
            } else {
                showToast(String.format(getText(R.string.error_while_retrieving_data).toString(),
                        errorMessage));
            }
        }
    }
}
