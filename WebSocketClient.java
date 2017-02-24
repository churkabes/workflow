import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketClient {
    private Logger logger = Logger.getLogger("logger");

    private String url;
    private Channel channel;
    private NioEventLoopGroup group;
    private WebSocketVersion version;
    private WebSocketClientHandler handler;
    private WebsocketHandler websocketHandler;
    private DefaultHttpHeaders headers = new DefaultHttpHeaders();
    private HttpHeaders hdr = new DefaultHttpHeaders();


    public WebSocketClient(String url, WebSocketVersion version) {
        this.url = url;
        this.version = version;
    }

    public HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<String, String>();

        if (this.headers.isEmpty()) {
            this.headers.add("Accept", "*/*");
            this.headers.add("Accept-Encoding", "gzip, deflate, sdch, br");
            this.headers.add("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4");
            this.headers.add("Cache-Control", "no-cache");
            this.headers.add("Connection", "Upgrade");
            this.headers.add("Pragma", "no-cache");
            this.headers.add("Upgrade", "websocket");
            this.headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        }

        for (Map.Entry<String, String> key : this.headers.entries()) {
            headers.put(key.getKey(), key.getValue());
        }
        return headers;
    }

    public void setHeaders(HashMap<String, String> headers) {
        for (String key : headers.keySet()) {
            this.headers.add(key, headers.get(key));
        }
    }

    public void setHandler(WebsocketHandler handler) {
        websocketHandler = handler;
    }

    public void connect() throws Exception {

        if (group != null) {
            if (group.isShuttingDown()) {
                return;
            }
            group.shutdownGracefully().sync();
        }

        URI uri = new URI(url);

        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port;
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw (new Exception("Only WS(S) is supported."));
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        group = new NioEventLoopGroup(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "WS EventLoopGroup " + url);
            }
        });
        // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
        // If you change it to V00, ping is not supported and remember to change
        // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
        handler =
                new WebSocketClientHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                                uri, version, null, false, this.headers));

        Bootstrap b = new Bootstrap();

        b.group(group)
                .channel(NioSocketChannel.class);
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {

                ChannelPipeline p = ch.pipeline();
                if (sslCtx != null) {
                    p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                }
                p.addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(16384),
                        new ReadTimeoutHandler(65),
                        handler);
            }
        });

        b.connect(uri.getHost(), port);

        logger.log(Level.ALL, "WS connecting to " + url);

    }

    public void close() {
        try {
            group.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            logger.log(Level.ALL, "error " + e);

        }
    }

    public void send(String msg) {
        if (group != null && !group.isShuttingDown() && handler.handshakeFuture().isSuccess()) {
            channel.writeAndFlush(new TextWebSocketFrame(msg));
            logger.log(Level.ALL, "WS send to " + url + " : " + msg);
        }
    }

    public abstract static class WebsocketHandler {
        public abstract void onOpen();

        public abstract void onRead(TextWebSocketFrame textFrame);

        public abstract void onClose();
    }

    public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        public ChannelFuture handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.log(Level.ALL, "WS disconnected from " + url);
            websocketHandler.onClose();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                handshakeFuture.setSuccess();
                channel = ctx.channel();

                logger.log(Level.ALL, "WS connected to " + url);
                websocketHandler.onOpen();

                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status() +
                                ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {

                websocketHandler.onRead((TextWebSocketFrame) frame);

            } else if (frame instanceof PongWebSocketFrame) {

            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

            logger.log(Level.ALL, "error " + cause);
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }

            ctx.close();
        }
    }
}

