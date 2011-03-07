package org.example.android.network;

import org.apache.http.client.ClientProtocolException;

public class TokenExpiredException extends ClientProtocolException {
    private static final long serialVersionUID = -3316415375858098055L;
    private boolean mIsRetryable;
    public TokenExpiredException(String message, boolean retryable) {
        super(message);
        mIsRetryable = retryable;
    }
    public boolean isRetryable() {
        return mIsRetryable;
    }
}
