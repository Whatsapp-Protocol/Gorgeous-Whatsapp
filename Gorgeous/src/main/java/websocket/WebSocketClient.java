package websocket;

import Util.FileUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;

import java.util.Base64;
import java.util.Scanner;


public final class WebSocketClient {
    public static int task_id = 1;
   static final String URL = System.getProperty("url", "ws://8.210.124.139:16090");


   static void ShowHelp(){
       System.out.println(
               "login 87819378 c:\\87819378.db\n" +
                       "SyncContact +661212 +123218 +819822\n" +
                       "SendText 8541264751254 hello\n"  +
                       "SetHDHeadData D:\\abc.jpg\n" +
                       "GetHDHead 6645824511@s.whatsapp.net\n" +
                       "CreateGroup mygroup 686564512@s.whatsapp.net 78121545@s.whatsapp.net 7832154981@s.whatsapp.net\n");
   }

   public static void main(String[] args) throws Exception {
        URI uri = new URI(URL);
        String scheme = uri.getScheme() == null? "ws" : uri.getScheme();
        final String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
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
            System.err.println("Only WS(S) is supported.");
            return;
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            final WebSocketClientHandler handler =
                    new WebSocketClientHandler(
                            WebSocketClientHandshakerFactory.newHandshaker(
                                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 10 * 1024 * 1024));

            Bootstrap b = new Bootstrap();
            b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        if (sslCtx != null) {
                            p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        }
                        p.addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(65536),
                                WebSocketClientCompressionHandler.INSTANCE,
                                handler);
                    }
                });

            Channel ch = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();
            System.out.println("input help\n");
            Scanner scanner = new Scanner(System.in);

            while (true) {
                String msg = scanner.nextLine();
                String[] inputArgs = msg.split(" ");
                if (inputArgs.length < 1) {
                    continue;
                }
                JSONObject command = new JSONObject();
                switch (inputArgs[0]) {
                    case "login" :{
                        //you can use whatsapp_config_tool.apk to get config
                        if (inputArgs.length >= 3)
                        {
                            //login 6666dsfasdfasdfsdf D:\\6666dsfasdfasdfsdf.db
                            command.put("command", "login");
                            command.put("userName", inputArgs[1]);
                            command.put("config", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[2])));

                            //set proxy
                            if (inputArgs.length >= 6) {
                                command.put("proxy_type", Integer.valueOf(inputArgs[3])); // -1 no proxy, 0 http proxy, 1 socks5 proxy
                                command.put("proxy_server", inputArgs[4]);
                                command.put("proxy_port", Integer.valueOf(inputArgs[5]));
                                if (inputArgs.length >= 8) {
                                    command.put("proxy_username", inputArgs[6]); //optional
                                    command.put("proxy_password", inputArgs[7]); //optional
                                }
                            }
                        }
                    }
                    break;
                    case "SyncContact":{
                        //SyncContact +661212 +123218 +819822
                        command.put("command", "SyncContact");
                        JSONArray phones = new JSONArray();
                        for (int i=1; i< inputArgs.length;i++) {
                            phones.add(inputArgs[i]);
                        }

                        command.put("phones", phones);
                        command.put("task_id", task_id++);
                    }
                    break;
                    case "SendText": {
                        //SendText 8541264751254 hello
                        command.put("command", "SendText");
                        command.put("jid", inputArgs[1]);
                        command.put("content", inputArgs[2]);
                        command.put("task_id", task_id++);
                    }
                    break;
                    case "SetHDHeadData" :{
                        // jpg resolution = 192 * 192
                        //SetHDHeadData D:\abc.jpg
                        command.put("task_id", task_id++);
                        command.put("command", "SetHDHeadData");
                        command.put("content", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[1])));
                    }
                    break;
                    case "GetHDHead" : {
                        //GetHDHead 6645824511
                        command.put("task_id", task_id++);
                        command.put("command", "GetHDHead");
                        command.put("jid", inputArgs[1]);
                    }
                    break;
                    case "SetPushName" :{
                        //SetPushName myname
                        command.put("task_id", task_id++);
                        command.put("command", "SetPushName");
                        command.put("pushname", inputArgs[1]);
                    }
                    break;
                    case "SetStatus" : {
                        //SetStatus mystatus
                        command.put("task_id", task_id++);
                        command.put("command", "SetStatus");
                        command.put("status", inputArgs[1]);
                    }
                    break;
                    case "CreateGroup" :{
                        //CreateGroup mygroup 686564512 78121545 7832154981
                        command.put("task_id", task_id++);
                        command.put("command", "CreateGroup");
                        command.put("subject", inputArgs[1]);


                        JSONArray members = new JSONArray();
                        for (int i=2; i< inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("members", members);
                    }
                    break;
                    case "ModifyGroupSubject" :{
                        //ModifyGroupSubject 123122132@g.us newsubject
                        command.put("task_id", task_id++);
                        command.put("command", "ModifyGroupSubject");
                        command.put("jid", inputArgs[1]);
                        command.put("subject", inputArgs[2]);
                    }
                    break;
                    case "ModifyGroupDesc" :{
                        //ModifyGroupDesc 123122132@g.us newdesc
                        command.put("task_id", task_id++);
                        command.put("command", "ModifyGroupDesc");
                        command.put("jid", inputArgs[1]);
                        command.put("desc", inputArgs[2]);
                    }
                    break;
                    case  "InviteGroupMembers": {
                        //ModifyGroupDesc 123122132@g.us 6541567412 872131571 654024645
                        command.put("task_id", task_id++);
                        command.put("command", "InviteGroupMembers");
                        command.put("jid", inputArgs[2]);


                        JSONArray members = new JSONArray();
                        for (int i=2;i<inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("members", members);
                    }
                    break;
                    case "RemoveGroupMembers" :{
                        //RemoveGroupMembers 123122132@g.us 6541567412 872131571 654024645
                        command.put("task_id", task_id++);
                        command.put("command", "RemoveGroupMembers");
                        command.put("jid", inputArgs[2]);


                        JSONArray members = new JSONArray();
                        for (int i=2;i<inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("members", members);
                    }
                    break;
                    case "PromoteGroupMembers": {
                        //PromoteGroupMembers 123122132@g.us 6541567412 872131571 654024645
                        command.put("task_id", task_id++);
                        command.put("command", "PromoteGroupMembers");
                        command.put("jid", inputArgs[1]);


                        JSONArray members = new JSONArray();
                        for (int i=2;i<inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("members", members);
                    }
                    break;
                    case "DemoteGroupMembers": {
                        //DemoteGroupMembers 123122132@g.us 6541567412 872131571 654024645
                        command.put("task_id", task_id++);
                        command.put("command", "DemoteGroupMembers");
                        command.put("jid", inputArgs[1]);


                        JSONArray members = new JSONArray();
                        for (int i=2;i<inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("members", members);
                    }
                    break;
                    case "GetGroupInfo" :{
                        //GetGroupInfo 123122132@g.us
                        command.put("task_id", task_id++);
                        command.put("command", "GetGroupInfo");
                        command.put("jid", inputArgs[1]);
                    }
                    break;
                    case "GetInviteLink": {
                        //GetInviteLink 123122132@g.us
                        command.put("task_id", task_id++);
                        command.put("command", "GetInviteLink");
                        command.put("jid", inputArgs[1]);
                        command.put("reset", false);
                    }
                    break;
                    case "AcceptInviteToGroup": {
                        //AcceptInviteToGroup afjjfasdfnsdfl
                        command.put("task_id", task_id++);
                        command.put("command", "AcceptInviteToGroup");
                        command.put("token", inputArgs[1]);
                    }
                    break;
                    case "LeaveGroups":{
                        //LeaveGroups 123122132@g.us 16546132165@g.us
                        command.put("task_id", task_id++);
                        command.put("command", "LeaveGroups");

                        JSONArray members = new JSONArray();
                        for (int i=1; i<inputArgs.length;i++) {
                            members.add(inputArgs[i]);
                        }
                        command.put("groups", members);
                    }
                    break;
                    case "Subscribe": {
                        //Subscribe 54567121654
                        command.put("command", "Subscribe");
                        command.put("jid", inputArgs[1]);
                    }
                    break;
                    case "SendDocument" :{
                        //SendDocument 54567121654 c:\abc.txt filename
                        command.put("task_id", task_id++);
                        command.put("command", "SendDocument");
                        command.put("jid", inputArgs[1]);
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[2])));
                        command.put("filename", inputArgs[3]);
                    }
                    break;
                    case "SendImage" :{
                        //SendImage 54567121654 c:\abc.jpg c:\abc_thumbnail.jpg yourcaption
                        command.put("task_id", task_id++);
                        command.put("command", "SendImage");
                        command.put("jid", inputArgs[1]);
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[2])));
                        command.put("thumbnail", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[3])));
                        command.put("caption", inputArgs[4]);
                    }
                    break;
                    case "SendVideo": {
                        //SendImage 54567121654 c:\abc.mp4 c:\abc_thumbnail.jpg yourcaption 10000 480 240
                        command.put("task_id", task_id++);
                        command.put("command", "SendVideo");
                        command.put("jid", inputArgs[1]);
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[2])));
                        command.put("thumbnail", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[3])));
                        command.put("caption", inputArgs[4]);
                        command.put("duration", Integer.valueOf(inputArgs[5]));
                        command.put("width", Integer.valueOf(inputArgs[6]));
                        command.put("height", Integer.valueOf(inputArgs[7]));
                    }
                    break;
                    case "SendPTT" :{
                        //SendImage 54567121654 c:\abc.ogg
                        command.put("task_id", task_id++);
                        command.put("command", "SendPTT");
                        command.put("jid", inputArgs[1]);
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(inputArgs[2])));
                    }
                    break;
                    case "SendLocation": {
                        //SendLocation 54567121654 22.59202003479004 113.96762084960938 name address comment
                        command.put("task_id", task_id++);
                        command.put("command", "SendLocation");
                        command.put("jid", inputArgs[1]);
                        command.put("latitude", Double.valueOf(inputArgs[2]));
                        command.put("longitude", Double.valueOf(inputArgs[3]));
                        command.put("name", inputArgs[4]);
                        command.put("address", inputArgs[5]);
                        command.put("comment", inputArgs[6]);
                    }
                    break;
                    case "SendVcard": {
                        //SendVcard 54567121654 myname BEGIN:VCARD\nVERSION:3.0\nN:;;;;\nFN:681330000000\nitem1.TEL:+681330000000\nitem1.X-ABLabel:xxx\nEND:VCARD
                        command.put("task_id", task_id++);
                        command.put("command", "SendVcard");
                        command.put("jid", inputArgs[1]);
                        command.put("showname", inputArgs[2]);
                        command.put("vcard", inputArgs[3]);
                    }
                    break;
                    case "help" :{
                        ShowHelp();
                    }
                }
                if (null != command.get("command")) {
                    SendMessage(ch, command);
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }
    static void SendMessage(Channel ch, JSONObject object) {
        WebSocketFrame frame = new TextWebSocketFrame(object.toString());
        ch.writeAndFlush(frame);
    }
}