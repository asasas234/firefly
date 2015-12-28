package test.codec.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.firefly.codec.http2.model.HttpHeader;
import com.firefly.codec.http2.model.HttpHeaderValue;
import com.firefly.codec.http2.model.HttpURI;
import com.firefly.codec.http2.stream.HTTP2Configuration;
import com.firefly.server.http2.HTTP1ServerConnection;
import com.firefly.server.http2.HTTP1ServerConnectionListener;
import com.firefly.server.http2.HTTP1ServerRequestHandler;
import com.firefly.server.http2.HTTP2Server;
import com.firefly.server.http2.HTTPServerRequest;
import com.firefly.server.http2.HTTPServerResponse;
import com.firefly.server.http2.HTTPServerResponse.HTTP1ServerResponseOutputStream;
import com.firefly.server.http2.ServerSessionListener;
import com.firefly.utils.io.BufferUtils;

public class HTTP1ServerDemo3 {

	public static void main(String[] args) {
		final HTTP2Configuration http2Configuration = new HTTP2Configuration();
		http2Configuration.setTcpIdleTimeout(10 * 60 * 1000);

		HTTP2Server server = new HTTP2Server("10.62.68.195", 6678, http2Configuration,
				new ServerSessionListener.Adapter(), new HTTP1ServerConnectionListener() {

					@Override
					public HTTP1ServerRequestHandler onNewConnectionIsCreating() {
						return new HTTP1ServerRequestHandler.Adapter() {

							@Override
							public void earlyEOF(HTTPServerRequest request, HTTPServerResponse response,
									HTTP1ServerConnection connection) {
								System.out.println(
										"the server connection " + connection.getSessionId() + " is early EOF");
							}

							@Override
							public void badMessage(int status, String reason, HTTPServerRequest request,
									HTTPServerResponse response, HTTP1ServerConnection connection) {
								System.out.println("the server received a bad message, " + status + "|" + reason);

								if (response != null) {
									response.setStatus(status);
									response.setReason(reason);

									try (HTTP1ServerResponseOutputStream output = response.getOutputStream()) {
										ByteBuffer data = BufferUtils.toBuffer("http message error",
												StandardCharsets.UTF_8);
										response.getFields().put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);

										output.writeAndClose(data);
									} catch (IOException e) {
										e.printStackTrace();
									}
								} else {
									try {
										connection.close();
									} catch (IOException e) {
										e.printStackTrace();
									}
								}

							}

							@Override
							public boolean messageComplete(HTTPServerRequest request, HTTPServerResponse response,
									HTTP1ServerConnection connection) {
								HttpURI uri = request.getURI();
								System.out.println("current path is " + uri.getPath());
								System.out.println("current http headers are " + request.getFields());
								response.setStatus(200);

								List<ByteBuffer> list = new ArrayList<>();
								list.add(BufferUtils.toBuffer("hello the server demo ", StandardCharsets.UTF_8));
								list.add(BufferUtils.toBuffer("test chunk 1 ", StandardCharsets.UTF_8));
								list.add(BufferUtils.toBuffer("test chunk 2 ", StandardCharsets.UTF_8));
								list.add(BufferUtils.toBuffer("中文的内容，哈哈 ", StandardCharsets.UTF_8));
								list.add(BufferUtils.toBuffer("靠！！！ ", StandardCharsets.UTF_8));

								// long contentLength = 0;
								// for (ByteBuffer buffer : list) {
								// contentLength += buffer.remaining();
								// }
								// response.getFields().put(HttpHeader.CONTENT_LENGTH,
								// String.valueOf(contentLength));
								// response.getFields().put(HttpHeader.CONNECTION,
								// HttpHeaderValue.CLOSE);

								try (HTTP1ServerResponseOutputStream output = response.getOutputStream()) {
									for (ByteBuffer buffer : list) {
										output.write(buffer);
									}
								} catch (IOException e) {
									e.printStackTrace();
								}
								return true;
							}

						};
					}
				});
		server.start();
	}

}
