package com.firefly.server.http2;

import com.firefly.codec.http2.decode.Parser;
import com.firefly.codec.http2.decode.ServerParser;
import com.firefly.codec.http2.stream.AbstractHTTP2Connection;
import com.firefly.codec.http2.stream.FlowControlStrategy;
import com.firefly.codec.http2.stream.HTTP2Configuration;
import com.firefly.codec.http2.stream.Session.Listener;
import com.firefly.net.Session;
import com.firefly.net.tcp.ssl.SSLSession;

public class HTTP2ServerConnection extends AbstractHTTP2Connection {

	public HTTP2ServerConnection(HTTP2Configuration config, Session tcpSession, SSLSession sslSession,
			ServerSessionListener serverSessionListener) {
		super(config, tcpSession, sslSession, serverSessionListener);
	}

	protected Parser initParser(HTTP2Configuration config, FlowControlStrategy flowControl, Listener listener) {
		HTTP2ServerSession http2ServerSession = new HTTP2ServerSession(scheduler, this.tcpSession, this.generator,
				(ServerSessionListener) listener, flowControl, config.getStreamIdleTimeout());
		http2ServerSession.setMaxLocalStreams(config.getMaxConcurrentStreams());
		http2ServerSession.setMaxRemoteStreams(config.getMaxConcurrentStreams());
		return new ServerParser(http2ServerSession, config.getMaxDynamicTableSize(), config.getMaxRequestHeadLength());
	}

}
