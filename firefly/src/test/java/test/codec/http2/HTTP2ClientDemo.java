package test.codec.http2;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.firefly.client.http2.HTTP2Client;
import com.firefly.client.http2.HTTP2ClientConnection;
import com.firefly.codec.http2.frame.DataFrame;
import com.firefly.codec.http2.frame.GoAwayFrame;
import com.firefly.codec.http2.frame.HeadersFrame;
import com.firefly.codec.http2.frame.PingFrame;
import com.firefly.codec.http2.frame.PushPromiseFrame;
import com.firefly.codec.http2.frame.ResetFrame;
import com.firefly.codec.http2.frame.SettingsFrame;
import com.firefly.codec.http2.model.HostPortHttpField;
import com.firefly.codec.http2.model.HttpFields;
import com.firefly.codec.http2.model.HttpHeader;
import com.firefly.codec.http2.model.HttpScheme;
import com.firefly.codec.http2.model.HttpVersion;
import com.firefly.codec.http2.model.MetaData;
import com.firefly.codec.http2.stream.HTTP2Configuration;
import com.firefly.codec.http2.stream.Session;
import com.firefly.codec.http2.stream.Session.Listener;
import com.firefly.codec.http2.stream.Stream;
import com.firefly.utils.concurrent.Callback;
import com.firefly.utils.concurrent.FuturePromise;

public class HTTP2ClientDemo {
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		final HTTP2Configuration http2Configuration = new HTTP2Configuration();
		http2Configuration.setFlowControlStrategy("simple");
		http2Configuration.setTcpIdleTimeout(10 * 60 * 1000);
		HTTP2Client client = new HTTP2Client(http2Configuration);
		

		FuturePromise<HTTP2ClientConnection> promise = new FuturePromise<>();
		client.connect("127.0.0.1", 6677, promise, new Listener() {

			@Override
			public Map<Integer, Integer> onPreface(Session session) {
				System.out.println("client preface: " + session);
				Map<Integer, Integer> settings = new HashMap<>();
				settings.put(SettingsFrame.HEADER_TABLE_SIZE, http2Configuration.getMaxDynamicTableSize());
				settings.put(SettingsFrame.INITIAL_WINDOW_SIZE, http2Configuration.getInitialStreamSendWindow());
				return settings;
			}

			@Override
			public com.firefly.codec.http2.stream.Stream.Listener onNewStream(Stream stream, HeadersFrame frame) {
				return null;
			}

			@Override
			public void onSettings(Session session, SettingsFrame frame) {
				System.out.println("client settings frame:" + frame);
			}

			@Override
			public void onPing(Session session, PingFrame frame) {
			}

			@Override
			public void onReset(Session session, ResetFrame frame) {
			}

			@Override
			public void onClose(Session session, GoAwayFrame frame) {
			}

			@Override
			public void onFailure(Session session, Throwable failure) {
			}
		});

		HTTP2ClientConnection connection = promise.get();
//		HttpFields fields = new HttpFields();
//		fields.put(HttpHeader.ACCEPT, "text/html");
//		fields.put(HttpHeader.USER_AGENT, "Firefly Client 1.0");
//		fields.put(HttpHeader.CONTENT_LENGTH, "72");
//		MetaData.Request metaData = new MetaData.Request("POST", HttpScheme.HTTP,
//				new HostPortHttpField("127.0.0.1:6677"), "/data", HttpVersion.HTTP_2, fields);
//
//		HeadersFrame headersFrame = new HeadersFrame(5, metaData, null, false);
//		FuturePromise<Stream> streamPromise = new FuturePromise<>();
//		connection.getHttp2Session().newStream(headersFrame, streamPromise, new Stream.Listener() {
//
//			@Override
//			public void onHeaders(Stream stream, HeadersFrame frame) {
//				System.out.println("client receives headers: " + frame);
//			}
//
//			@Override
//			public com.firefly.codec.http2.stream.Stream.Listener onPush(Stream stream, PushPromiseFrame frame) {
//				return null;
//			}
//
//			@Override
//			public void onData(Stream stream, DataFrame frame, Callback callback) {
//				System.out.println("client receives data:" + frame);
//			}
//
//			@Override
//			public void onReset(Stream stream, ResetFrame frame) {
//			}
//
//			@Override
//			public void onTimeout(Stream stream, Throwable x) {
//			}
//		});
//
//		Stream clientStream = streamPromise.get();
//		System.out.println("client stream id: " + clientStream.getId());
//
//		final byte[] smallContent = new byte[22];
//		final byte[] bigContent = new byte[50];
//		Random random = new Random();
//		random.nextBytes(smallContent);
//		random.nextBytes(bigContent);
//
//		DataFrame smallDataFrame = new DataFrame(clientStream.getId(), ByteBuffer.wrap(smallContent), false);
//		DataFrame bigDateFrame = new DataFrame(clientStream.getId(), ByteBuffer.wrap(bigContent), true);
//
//		Callback callback = new Callback() {
//
//			@Override
//			public void succeeded() {
//				System.out.println("client sents data success");
//			}
//
//			@Override
//			public void failed(Throwable x) {
//				System.out.println("client sents data failure");
//			}
//		};
//		clientStream.data(smallDataFrame, callback);
//		clientStream.data(bigDateFrame, callback);
	}
}
