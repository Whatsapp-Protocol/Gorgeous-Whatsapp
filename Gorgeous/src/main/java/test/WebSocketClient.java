/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package test;

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

import java.io.*;
import java.net.URI;

import java.util.Base64;

/**
 * This is an example of a WebSocket client.
 * <p>
 * In order to run this example you need a compatible WebSocket server.
 * Therefore you can either start the WebSocket server from the examples
 * by running {@link io.netty.example.http.websocketx.server.WebSocketServer}
 * or connect to an existing WebSocket server such as
 * <a href="https://www.websocket.org/echo.html">ws://echo.websocket.org</a>.
 * <p>
 * The client will attempt to connect to the URI passed to it as the first argument.
 * You don't have to specify any arguments if you want to connect to the example WebSocket server,
 * as this is the default.
 */
public final class WebSocketClient {
   static final String URL = System.getProperty("url", "ws://8.210.124.139:16090");
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



            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                JSONObject command = new JSONObject();
                switch (msg) {
                    case "login" :{
                        //you can use whatsapp_config_tool.apk to get config
                        String configPath = new File(System.getProperty("user.dir"), "154254845214.db").getAbsolutePath();
                        command.put("command", "login");
                        command.put("userName", "154254845214");
                        command.put("config", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent(configPath)));
                        command.put("proxy_type",-1); // -1 no proxy, 0 http proxy, 1 socks5 proxy
                        command.put("proxy_server", "192.168.56.1");
                        command.put("proxy_port", 1080);
                        command.put("proxy_username", ""); //optional
                        command.put("proxy_password", ""); //optional
                       //command.put("reset", true); //optional
                    }
                    break;
                    case "SyncContact":{
                        command.put("command", "SyncContact");
                        JSONArray phones = new JSONArray();
                        phones.add("+666xxx");
                        command.put("phones", phones);
                        command.put("task_id", 2);
                    }
                    break;
                    case "SendText": {
                        command.put("command", "SendText");
                        command.put("jid", "8541264751254");
                        command.put("content", "test");
                        command.put("task_id", 2);
                    }
                    break;
                    case "SetHDHeadData" :{
                        command.put("task_id", 4);
                        command.put("command", "SetHDHeadData");
                        command.put("content", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("c:\\code\\Gorgeous\\main.jpg")));
                    }
                    break;
                    case "GetHDHead" : {
                        command.put("task_id", 3);
                        command.put("command", "GetHDHead");
                        command.put("jid", "8541264751254");
                    }
                    break;
                    case "SetPushName" :{
                        command.put("task_id", 20);
                        command.put("command", "SetPushName");
                        command.put("pushname", "we again");
                    }
                    break;
                    case "SetStatus" : {
                        command.put("task_id", 19);
                        command.put("command", "SetStatus");
                        command.put("status", "i am free");
                    }
                    break;
                    case "CreateGroup" :{
                        command.put("task_id", 18);
                        command.put("command", "CreateGroup");
                        command.put("subject", "subject");


                        JSONArray members = new JSONArray();
                        members.add("8541264751254");
                        command.put("members", members);
                    }
                    break;
                    case "ModifyGroupSubject" :{
                        command.put("task_id", 17);
                        command.put("command", "ModifyGroupSubject");
                        command.put("jid", "782111231d31213d@g.us");
                        command.put("subject", "new subject");
                    }
                    break;
                    case "ModifyGroupDesc" :{
                        command.put("task_id", 20);
                        command.put("command", "ModifyGroupDesc");
                        command.put("jid", "782111231d31213d@g.us");
                        command.put("desc", "this is my desc");
                    }
                    break;
                    case  "InviteGroupMembers": {
                        command.put("task_id", 16);
                        command.put("command", "InviteGroupMembers");
                        command.put("jid", "782111231d31213d@g.us");


                        JSONArray members = new JSONArray();
                        members.add("8541264751254");
                        command.put("members", members);
                    }
                    break;
                    case "RemoveGroupMembers" :{
                        command.put("task_id", 15);
                        command.put("command", "RemoveGroupMembers");
                        command.put("jid", "782111231d31213d@g.us");


                        JSONArray members = new JSONArray();
                        members.add("8541264751254");
                        command.put("members", members);
                    }
                    break;
                    case "PromoteGroupMembers": {
                        command.put("task_id", 14);
                        command.put("command", "PromoteGroupMembers");
                        command.put("jid", "782111231d31213d@g.us");


                        JSONArray members = new JSONArray();
                        members.add("8541264751254");
                        command.put("members", members);
                    }
                    break;
                    case "DemoteGroupMembers": {

                        command.put("task_id", 13);
                        command.put("command", "DemoteGroupMembers");
                        command.put("jid", "782111231d31213d@g.us");


                        JSONArray members = new JSONArray();
                        members.add("8541264751254");
                        command.put("members", members);
                    }
                    break;
                    case "GetGroupInfo" :{
                        command.put("task_id", 12);
                        command.put("command", "GetGroupInfo");
                        command.put("jid", "782111231d31213d@g.us");
                    }
                    break;
                    case "GetInviteLink": {
                        command.put("task_id", 14);
                        command.put("command", "GetInviteLink");
                        command.put("jid", "782111231d31213d@g.us");
                        command.put("reset", false);
                    }
                    break;
                    case "AcceptInviteToGroup": {
                        command.put("task_id", 16);
                        command.put("command", "AcceptInviteToGroup");
                        command.put("token", "IX5uHPKwdLBJlH43EqgaYT");
                    }
                    break;
                    case "LeaveGroups":{
                        command.put("task_id", 111);
                        command.put("command", "LeaveGroups");

                        JSONArray members = new JSONArray();
                        members.add("782111231d31213d@g.us");
                        command.put("groups", members);
                    }
                    break;
                    case "Subscribe": {
                        command.put("command", "Subscribe");
                        command.put("jid", "8541264751254");
                    }
                    break;
                    case "UnSubscribe": {
                        command.put("command", "UnSubscribe");
                        command.put("jid", "8541264751254");
                    }
                    break;
                    case "test": {
                        command.put("task_id", 4);
                        command.put("command", "SetHDHeadData");
                        command.put("content", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\main.jpg")));
                    }
                    break;
                    case "SendDocument" :{
                        command.put("task_id", 4);
                        command.put("command", "SendDocument");
                        command.put("jid", "8541264751254");
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\main.jpg")));
                        command.put("filename", "main12.jpg");
                    }
                    break;
                    case "SendImage" :{
                        command.put("task_id", 4);
                        command.put("command", "SendImage");
                        command.put("jid", "8541264751254");
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\metalunderqml-example.jpg")));
                        command.put("thumbnail", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\main.jpg")));
                        command.put("caption", "hellokg");
                    }
                    break;
                    case "SendVideo": {
                        command.put("task_id", 4);
                        command.put("command", "SendVideo");
                        command.put("jid", "8541264751254");
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\Walk_to_image_room.mp4")));
                        command.put("thumbnail", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\main.jpg")));
                        command.put("caption", "hellokg");
                        command.put("duration", 13000);
                        command.put("width", 465);
                        command.put("height", 892);
                    }
                    break;
                    case "SendPTT" :{
                        command.put("task_id", 4);
                        command.put("command", "SendPTT");
                        command.put("jid", "8541264751254");
                        command.put("data", Base64.getEncoder().encodeToString(FileUtil.ReadFileContent("e:\\Gorgeous\\ptt.ogg")));
                    }
                    break;
                    case "SendLocation": {
                        command.put("task_id", 111);
                        command.put("command", "SendLocation");
                        command.put("jid", "8541264751254@s.whatsapp.net");
                        command.put("latitude", 22.59202003479004);
                        command.put("longitude", 113.96762084960938);
                        command.put("name", "xx");
                        command.put("address", "xx");
                        command.put("comment", "xx");
                    }
                    break;
                    case "SendVcard": {
                        command.put("task_id", 111);
                        command.put("command", "SendVcard");
                        command.put("jid", "1246542154@s.whatsapp.net");
                        command.put("showname", "abc");
                        command.put("vcard", "BEGIN:VCARD\nVERSION:3.0\nN:;;;;\nFN:681330000000\nitem1.TEL:+68 133 000 0000\nitem1.X-ABLabel:xxx\nEND:VCARD");
                    }
                    break;
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