package org.example.android.oauth;

import org.apache.http.client.ClientProtocolException;

public class TokenInvalidException extends ClientProtocolException {
	private static final long serialVersionUID = 7376197889809828488L;
	public TokenInvalidException(String message) {
		super(message);
	}
}
