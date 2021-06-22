package Gorgeous;

import Env.DeviceEnv;
import Handshake.NoiseHandshake;
import Message.WhatsMessage;
import ProtocolTree.ProtocolTreeNode;
import ProtocolTree.StanzaAttribute;
import Util.GorgeousLooper;
import Util.StringUtil;
import axolotl.AxolotlManager;
import cn.hutool.core.img.Img;
import cn.hutool.core.io.FileUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import jni.MediaCipher;
import okhttp3.*;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GorgeousEngine implements NoiseHandshake.HandshakeNotify {
    public interface GorgeousEngineDelegate {
        public void OnLogin(int code, String fullPhone, ProtocolTreeNode desc);
        public void OnDisconnect(String desc);
        public void OnSync(ProtocolTreeNode content);
        public void OnPacketResponse(String type, ProtocolTreeNode content);
    }

    interface NodeCallback {
        void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result);
    }


    public static class NodeHandleInfo {
        NodeCallback callbackRunnable;
        ProtocolTreeNode srcNode;
        NodeHandleInfo(NodeCallback callback, ProtocolTreeNode srcNode) {
            this.callbackRunnable = callback;
            this.srcNode = srcNode;
        }
    }


    static final String TAG = GorgeousEngine.class.getName();
    public  GorgeousEngine(String configPath, GorgeousEngineDelegate delegate, NoiseHandshake.Proxy proxy, String tmpDir) {
        configPath_ = configPath;
        delegate_ = delegate;
        proxy_ = proxy;
        tmpDir_ = tmpDir;
    }

    public boolean StartEngine() {
        axolotlManager_ = new AxolotlManager(configPath_);
        try {
            byte[] envBuffer =  axolotlManager_.GetBytesSetting("env");
            envBuilder_ = DeviceEnv.AndroidEnv.parseFrom(envBuffer).toBuilder();
            Env.DeviceEnv.AppVersion.Builder useragentBuilder = envBuilder_.getUserAgentBuilder().getAppVersionBuilder();
            useragentBuilder.setPrimary(2);
            useragentBuilder.setSecondary(21);
            useragentBuilder.setTertiary(5);
            useragentBuilder.setQuaternary(14);
            noiseHandshake_ = new NoiseHandshake(this, proxy_);
            noiseHandshake_.StartNoiseHandShake( envBuilder_.build());
            return true;
        }
        catch (Exception e){
            Log.e(TAG, "parse env failed:" + e.getLocalizedMessage());
            return false;
        }
    }

    public void StopEngine() {
        if (noiseHandshake_ != null) {
            noiseHandshake_.StopNoiseHandShake();
            noiseHandshake_ = null;
        }
        if (null != axolotlManager_) {
            axolotlManager_.Close();
        }
    }

    private NoiseHandshake noiseHandshake_ = null;
    private String configPath_;
    NoiseHandshake.Proxy proxy_;
    AxolotlManager axolotlManager_;
    DeviceEnv.AndroidEnv.Builder envBuilder_;
    Timer pingTimer_;
    String tmpDir_;

    static class MessageInfo {
        String jid;
        byte[] serialData;
        String messageType;
        String mediaType;
    }
    LinkedHashMap<String, MessageInfo> msgMap_ = new LinkedHashMap<>(100);

    @Override
    public void OnConnected(byte[] serverPublicKey) {
        if (serverPublicKey != null) {
            envBuilder_.setServerStaticPublic(ByteString.copyFrom(serverPublicKey));
        } else {
            envBuilder_.clearServerStaticPublic();
        }
        axolotlManager_.SetBytesSetting("env", envBuilder_.build().toByteArray());
    }

    @Override
    public void OnDisconnected(String desc) {
        if (null != delegate_) {
            delegate_.OnDisconnect(desc);
        }
        if (null != pingTimer_) {
            pingTimer_.cancel();
        }
    }

    @Override
    public void OnPush(ProtocolTreeNode node) {
        if (HandleRegisterNode(node)) {
            return;
        }
        switch (node.GetTag()){
            case "iq":{
                HandleIq(node);
                break;
            }
            case "call":{
                HandleCall(node);
                break;
            }
            case "stream:error":{
                HandleStreamError(node);
                break;
            }
            case "failure":{
                HandleFailure(node);
                break;
            }
            case "success":{
                HandleSuccess(node);
                break;
            }
            case "receipt":{
                HandleAeceipt(node);
                break;
            }
            case "message":{
                HandleRecvMessage(node);
                break;
            }
            case "ack":{
                HandleAck(node);
                break;
            }
            case "notification":{
                HandleNotification(node);
                break;
            }
            case "presence":{
                HandlePresence(node);
                break;
            }
            default:
            {
                break;
            }
        }
    }

    void  HandlePresence(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnSync(node);
        }
    }


    void  HandleIq(ProtocolTreeNode node) {

    }

    void HandleCall(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("type");
        if (type.equals("offer")) {
            ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
            receipt.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            LinkedList<ProtocolTreeNode> offer = node.GetChildren("offer");
            if (!offer.isEmpty()) {
                receipt.AddChild(offer.get(0));
            }
            AddTask(receipt);
        } else {
            ProtocolTreeNode ack = new ProtocolTreeNode("ack");
            ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            ack.AddAttribute(new StanzaAttribute("class", "call"));
            ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            AddTask(ack);
        }
    }

    void HandleStreamError(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnDisconnect(node.toString());
        }
    }

    void HandleFailure(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnLogin(-1 , envBuilder_.getFullphone(),  node);
        }
    }

    void SendPing() {
        ProtocolTreeNode ping = new ProtocolTreeNode("iq");
        ping.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        ping.AddAttribute(new StanzaAttribute("xmlns", "w:p"));
        ping.AddAttribute(new StanzaAttribute("type", "get"));
        ping.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        ping.AddChild(new ProtocolTreeNode("ping"));
        AddTask(ping);
    }

    void HandleSuccess(ProtocolTreeNode node) {
        if (null != delegate_) {
            delegate_.OnLogin(0 ,envBuilder_.getFullphone(), node);
        }

        GetCdnInfo();
        pingTimer_ = new Timer();
        pingTimer_.schedule(new TimerTask() {
            @Override
            public void run() {
                SendPing();
            }
        }, 100, 60 *1000);
        {
            //available
            ProtocolTreeNode available = new ProtocolTreeNode("presence");
            available.AddAttribute(new StanzaAttribute("type", "available"));
            AddTask(available);
        }
    }

    void Test() {
        SendMedia("","E:\\v2rayN\\vpoint_vmess_freedom.json","document");
    }



    boolean GetImageThumb(String path, JSONObject mediaInfo) {
        //生成缩略图
        String fileName = new File(path).getName();
        File thumbnailFile =  new File(tmpDir_,  "thumbnail." + fileName);
        Img srcImage =  Img.from(new File(path));
        mediaInfo.put("width", srcImage.getImg().getWidth(null));
        mediaInfo.put("height", srcImage.getImg().getHeight(null));
        srcImage.setTargetImageType(FileUtil.extName(thumbnailFile))//
                .scale(120, 120, null)//
                .write(thumbnailFile);
        mediaInfo.put( "thumbnail_path", thumbnailFile.getAbsolutePath());
        return true;
    }

    boolean GetVideoThumb(String path, JSONObject mediaInfo) {
        try {
            FFmpegFrameGrabber ff = new FFmpegFrameGrabber(path);
            ff.start();
            mediaInfo.put("play_time", ff.getLengthInTime()/(1000*1000) + 1);
            mediaInfo.put("width", ff.getImageWidth());
            mediaInfo.put("height", ff.getImageHeight());
            //这里取第一帧，有可能是黑的，可以自己调
            Frame frame = null;
            for (int i=0; i< ff.getLengthInFrames(); i++) {
                frame = ff.grabFrame();
                if (frame.image != null) {
                    break;
                }
            }
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bi = converter.getBufferedImage(frame);
            Image scaledImage = bi.getScaledInstance(224, 224, Image.SCALE_DEFAULT);
            BufferedImage scaledImageBuffer = new BufferedImage(224, 224, BufferedImage.TYPE_3BYTE_BGR);
            scaledImageBuffer.getGraphics().drawImage(scaledImage,0, 0, null);

            String fileName = new File(path).getName();
            File thumbnailFile =  new File(tmpDir_,  "thumbnail." + fileName + ".png");
            try {
                ImageIO.write(scaledImageBuffer, "png", thumbnailFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ff.close();
            mediaInfo.put("thumbnail_path" ,thumbnailFile.getAbsolutePath());
            return true;
        } catch (FrameGrabber.Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    String GetUserAgent() {
        return String.format("WhatsApp/%d.%d.%d.%d Android/%s Device/%s-%s",
                envBuilder_.getUserAgent().getAppVersion().getPrimary(),
                envBuilder_.getUserAgent().getAppVersion().getSecondary(),
                envBuilder_.getUserAgent().getAppVersion().getTertiary(),
                envBuilder_.getUserAgent().getAppVersion().getQuaternary(),
                envBuilder_.getUserAgent().getOsVersion(),
                envBuilder_.getUserAgent().getManufacturer().replace("-", ""),
                envBuilder_.getUserAgent().getDevice().replace("-", ""));
    }

    public String SendMedia(String jid, String path, String mediaType) {
        if (!new File(path).exists()) {
            return "";
        }

        String id = GenerateIqId();
        MediaCipher.MediaEncryptInfo encryptInfo = MediaCipher.Encrypt(path, mediaType);
        StringBuilder sb = new StringBuilder("https://");
        sb.append(cdnHost_);
        String mime;
        JSONObject mediaInfo = new JSONObject();

        switch (mediaType) {
            case "image":{
                mime = "image/jpeg";
                sb.append("/mms/image");
                GetImageThumb(path, mediaInfo);
                break;
            }
            case "video" :{
                mime = "video/mp4";
                sb.append("/mms/video");
                GetVideoThumb(path, mediaInfo);
                break;
            }
            case "ptt": {
                mime = "audio/ogg; codecs=opus";
                sb.append("/mms/audio");
                break;
            }
            case "document" :{
                sb.append("/mms/document");
                String ext = StringUtil.GetFileExt(path);
                if (StringUtil.isEmpty(ext)) {
                    mime = "text/txt";
                } else {
                    mime = "text/" + ext;
                }
                break;
            }
            default:{
             return "";
            }
        }

        mediaInfo.put("media_type", mediaType);
        mediaInfo.put("mime", mime);
        mediaInfo.put("encrypt_hash", Base64.getEncoder().encodeToString(encryptInfo.contentHash));
        mediaInfo.put("orign_hash", Base64.getEncoder().encodeToString(encryptInfo.origHash));
        mediaInfo.put("file_len", new File(path).length());
        mediaInfo.put("media_key", encryptInfo.mediaKey);
        mediaInfo.put("file_name",  new File(path).getName());
        mediaInfo.put("title",  StringUtil.GetFileBaseName(path));


            sb.append("/").append(encryptInfo.token).append("?")
                    .append("auth=").append(cdnAuthKey_)
                    .append("&token=").append(encryptInfo.token);
        OkHttpClient client = new OkHttpClient().newBuilder().hostnameVerifier((s, sslSession) -> true).build();
        RequestBody requestBody = RequestBody.create(encryptInfo.data);
        Request request = new Request.Builder()
                .url(sb.toString())
                .post(requestBody) //添加请求体
                .addHeader("User-Agent", GetUserAgent())
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "upload failed :" + e.getLocalizedMessage());
                GorgeousLooper.Instance().PostTask(() -> {
                    if (delegate_ != null) {
                        ProtocolTreeNode failedNode = new ProtocolTreeNode("iq");
                        failedNode.AddAttribute(new StanzaAttribute("id", id));
                        ProtocolTreeNode error = new ProtocolTreeNode("error");
                        error.AddAttribute(new StanzaAttribute("desc", e.getLocalizedMessage()));
                        failedNode.AddChild(error);
                        delegate_.OnPacketResponse("SendMedia", failedNode);
                    }
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d(TAG, response.toString());
                if (response.code() != 200) {
                    GorgeousLooper.Instance().PostTask(() -> {
                        if (delegate_ != null) {
                            ProtocolTreeNode failedNode = new ProtocolTreeNode("iq");
                            failedNode.AddAttribute(new StanzaAttribute("id", id));
                            ProtocolTreeNode error = new ProtocolTreeNode("error");
                            error.AddAttribute(new StanzaAttribute("desc", response.toString()));
                            failedNode.AddChild(error);
                            delegate_.OnPacketResponse("SendMedia", failedNode);
                        }
                    });
                } else {
                    JSONObject res = new JSONObject(response.body().string());
                    mediaInfo.put("url", res.getString("url"));
                    mediaInfo.put("direct_path", res.getString("direct_path"));
                    InnerSendMedia(jid, mediaInfo, id);
                }
            }
        });
        return id;
    }

    void GenerateImageMessage(WhatsMessage.WhatsAppImageMessage.Builder builder, JSONObject mediaInfo) {
        try {
            builder.setUrl(mediaInfo.getString("url"));
            builder.setMimetype(mediaInfo.getString("mime"));
            builder.setFileEncSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("encrypt_hash"))));
            builder.setFileSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("orign_hash"))));
            builder.setFileLength(mediaInfo.getInt("file_len"));
            builder.setWidth(mediaInfo.getInt("width"));
            builder.setHeight(mediaInfo.getInt("height"));
            builder.setJpegThumbnail(ByteString.readFrom(new FileInputStream(mediaInfo.getString("thumbnail_path"))));
            builder.setMediaKey(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("media_key"))));
            builder.setDirectPath(mediaInfo.getString("direct_path"));
            builder.setFirstScanLength(0);
        }
        catch (Exception e) {
            Log.e(TAG, "image:" + e.getLocalizedMessage());
        }
    }

    void  GenerateVideoMessage(WhatsMessage.WhatsAppVideoMessage.Builder builder, JSONObject mediaInfo) {
        try {
            builder.setUrl(mediaInfo.getString("url"));
            builder.setMimetype(mediaInfo.getString("mime"));
            builder.setFileEncSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("encrypt_hash"))));
            builder.setFileSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("orign_hash"))));
            builder.setFileLength(mediaInfo.getInt("file_len"));
            builder.setWidth(mediaInfo.getInt("width"));
            builder.setHeight(mediaInfo.getInt("height"));
            builder.setJpegThumbnail(ByteString.readFrom(new FileInputStream(mediaInfo.getString("thumbnail_path"))));
            builder.setMediaKey(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("media_key"))));
            builder.setDirectPath(mediaInfo.getString("direct_path"));
            builder.setSeconds(mediaInfo.getInt("play_time"));
        }
        catch (Exception e) {
            Log.e(TAG, "video:" + e.getLocalizedMessage());
        }
    }

    void  GeneratePPtMessage(WhatsMessage.WhatsAppAudioMessage.Builder builder, JSONObject mediaInfo) {
        builder.setUrl(mediaInfo.getString("url"));
        builder.setMimetype(mediaInfo.getString("mime"));
        builder.setFileEncSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("encrypt_hash"))));
        builder.setFileSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("orign_hash"))));
        builder.setFileLength(mediaInfo.getInt("file_len"));
        builder.setMediaKey(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("media_key"))));
        builder.setDirectPath(mediaInfo.getString("direct_path"));
        builder.setPtt(true);
        //硬编码
        builder.setSeconds(5);
    }


    void  GenerateDocMessage(WhatsMessage.WhatsAppDocumentMessage.Builder builder, JSONObject mediaInfo) {
        builder.setUrl(mediaInfo.getString("url"));
        builder.setMimetype(mediaInfo.getString("mime"));
        builder.setFileEncSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("encrypt_hash"))));
        builder.setFileSha256(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("orign_hash"))));
        builder.setFileLength(mediaInfo.getInt("file_len"));
        builder.setMediaKey(ByteString.copyFrom(Base64.getDecoder().decode(mediaInfo.getString("media_key"))));
        builder.setDirectPath(mediaInfo.getString("direct_path"));
        builder.setFileName(mediaInfo.getString("file_name"));
        builder.setTitle(mediaInfo.getString("title"));
        builder.setPageCount(0);
    }

    void InnerSendMedia(String jid, JSONObject mediaInfo, String id) {
        GorgeousLooper.Instance().PostTask(() -> {
            String mediaType = mediaInfo.getString("media_type");
            WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
            switch (mediaType) {
                case "image":{
                    GenerateImageMessage(builder.getImageMessageBuilder(), mediaInfo);
                    break;
                }
                case "video":{
                    GenerateVideoMessage(builder.getVideoMessageBuilder(), mediaInfo);
                    break;
                }
                case "ptt": {
                    GeneratePPtMessage(builder.getAudioMessageBuilder(), mediaInfo);
                    break;
                }
                case "document": {
                    GenerateDocMessage(builder.getDocumentMessageBuilder(), mediaInfo);
                    break;
                }
            }
            SendSerialData(jid, builder.build().toByteArray(), "media", mediaType, id);
        });
    }


    public String SendText(String jid, String content) {
        String id = GenerateIqId();
        GorgeousLooper.Instance().PostTask(() -> {
            WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
            builder.setConversation(content);
            SendSerialData(jid, builder.build().toByteArray(), "text", "", id);
        });
        //序列化数据
        return id;
    }

    public String SendVCard(String jid, String showName, String vcard) {
        String id = GenerateIqId();
        GorgeousLooper.Instance().PostTask(() -> {
            WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
            WhatsMessage.WhatsAppContactMessage.Builder contactMessage =   builder.getContactMessageBuilder();
            contactMessage.setDisplayName(showName);
            contactMessage.setVcard(vcard);
            SendSerialData(jid, builder.build().toByteArray(), "media", "contact", id);
        });
        //序列化数据
        return id;
    }

    public String SendLocation(String jid, double latitude, double longtitude, String name, String address, String comment) {
        String id = GenerateIqId();
        GorgeousLooper.Instance().PostTask(() -> {
            WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
            WhatsMessage.WhatsAppLocationMessage.Builder contactMessage =   builder.getLocationMessageBuilder();
            contactMessage.setAddress(address);
            contactMessage.setDegreesLatitude(latitude);
            contactMessage.setDegreesLongitude(longtitude);
            contactMessage.setName(name);
            contactMessage.setComment(comment);
            SendSerialData(jid, builder.build().toByteArray(), "media", "location", id);
        });
        //序列化数据
        return id;
    }


    //电话号码 + 开头
    public String SyncContact(List<String> phones) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("xmlns", "usync"));
        iq.AddAttribute(new StanzaAttribute("type", "get"));

        ProtocolTreeNode usync = new ProtocolTreeNode("usync");
        usync.AddAttribute(new StanzaAttribute("index", "0"));
        usync.AddAttribute(new StanzaAttribute("last", "true"));
        usync.AddAttribute(new StanzaAttribute("mode", "full"));
        usync.AddAttribute(new StanzaAttribute("context", "registration"));
        usync.AddAttribute(new StanzaAttribute("sid", "sync_sid_full_" + UUID.randomUUID().toString()));

        // query 子节点
        ProtocolTreeNode query = new ProtocolTreeNode("query");
        query.AddChild(new ProtocolTreeNode("contact"));
        query.AddChild(new ProtocolTreeNode("status"));

        ProtocolTreeNode business = new ProtocolTreeNode("business");
        business.AddChild(new ProtocolTreeNode("verified_name"));
        ProtocolTreeNode profile = new ProtocolTreeNode("profile");
        profile.AddAttribute(new StanzaAttribute("v", "4"));
        business.AddChild(profile);

        query.AddChild(business);
        usync.AddChild(query);
        //列表
        ProtocolTreeNode list = new ProtocolTreeNode("list");
        for (String phone : phones) {
            ProtocolTreeNode user = new ProtocolTreeNode("user");
            ProtocolTreeNode contact = new ProtocolTreeNode("contact");
            contact.SetData(phone.getBytes(StandardCharsets.UTF_8));
            user.AddChild(contact);
            list.AddChild(user);
        }

        usync.AddChild(list);
        iq.AddChild(usync);
        return AddTask(iq, new HandleResult("SyncContact"));
    }

    //640*640
    public String SetHDHead(String path) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("xmlns", "w:profile:picture"));
        iq.AddAttribute(new StanzaAttribute("type", "set"));
        iq.AddAttribute(new StanzaAttribute("to", JidNormalize(envBuilder_.getFullphone())));

        ProtocolTreeNode picture = new ProtocolTreeNode("picture");
        picture.AddAttribute(new StanzaAttribute("type", "image"));
        picture.SetData(StringUtil.ReadFileContent(path));

        iq.AddChild(picture);
        return AddTask(iq, new HandleResult("SetHDHead"));
    }


    public String Subscribe(String jid) {
        ProtocolTreeNode presence = new ProtocolTreeNode("presence");
        presence.AddAttribute(new StanzaAttribute("type", "subscribe"));
        presence.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        return AddTask(presence);
    }


    public String UnSubscribe(String jid) {
        ProtocolTreeNode presence = new ProtocolTreeNode("presence");
        presence.AddAttribute(new StanzaAttribute("type", "unsubscribe"));
        presence.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        return AddTask(presence);
    }


    public String SetStatue(String status) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("xmlns", "status"));
        iq.AddAttribute(new StanzaAttribute("type", "set"));
        iq.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));

        ProtocolTreeNode statusNode = new ProtocolTreeNode("status");
        statusNode.SetData(status.getBytes(StandardCharsets.UTF_8));

        iq.AddChild(statusNode);
        return AddTask(iq, new HandleResult("SetStatue"));
    }

    public String SetPushName(String pushName) {
        ProtocolTreeNode presence = new ProtocolTreeNode("presence");
        presence.AddAttribute(new StanzaAttribute("type", "available"));
        presence.AddAttribute(new StanzaAttribute("name", pushName));
        return AddTask(presence);
    }

    public String GetHDHead(String jid) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("xmlns", "w:profile:picture"));
        iq.AddAttribute(new StanzaAttribute("type", "get"));
        iq.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode picture = new ProtocolTreeNode("picture");
        picture.AddAttribute(new StanzaAttribute("type", "image"));

        iq.AddChild(picture);
        return AddTask(iq, new HandleResult("GetHDHead"));
    }




    void GetCdnInfo() {
        ProtocolTreeNode cdnNode = new ProtocolTreeNode("iq");
        cdnNode.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        cdnNode.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        cdnNode.AddAttribute(new StanzaAttribute("xmlns", "w:m"));
        cdnNode.AddAttribute(new StanzaAttribute("type", "set"));


        ProtocolTreeNode media = new ProtocolTreeNode("media_conn");
        cdnNode.AddChild(media);
        AddTask(cdnNode, (srcNode, result) -> {
            String type = result.GetAttributeValue("type");
            if (!type.equals("result")) {
                Log.e(TAG, "cdn 失败:" + result.toString());
                return;
            }
            ProtocolTreeNode media_conn = result.GetChild("media_conn");
            if (null == media_conn) {
                Log.e(TAG, "media_conn 节点:" + result.toString());
                return;
            }
            cdnAuthKey_ = media_conn.GetAttributeValue("auth");
            LinkedList<ProtocolTreeNode> children = media_conn.GetChildren();
            for (ProtocolTreeNode child : children) {
                if (child.GetAttributeValue("type").equals("primary")) {
                    cdnHost_ = child.GetAttributeValue("hostname");
                    break;
                }
            }
        });
    }


    class HandleRetryGetKeysFor implements NodeCallback {
        String msgId_;
        HandleRetryGetKeysFor(String msgId) {
            msgId_ = msgId;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {

        }
    }


    void HandleAeceipt(ProtocolTreeNode node) {
        {
            //发送确认
            ProtocolTreeNode ack = new ProtocolTreeNode("ack");
            ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
            ack.AddAttribute(new StanzaAttribute("class", "receipt"));
            ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            {
                //type
                String value = node.GetAttributeValue("type");
                if (!StringUtil.isEmpty(value)) {
                    ack.AddAttribute(new StanzaAttribute("type", value));
                }
            }

            {
                //participant
                String value = node.GetAttributeValue("participant");
                if (!StringUtil.isEmpty(value)) {
                    ack.AddAttribute(new StanzaAttribute("participant", value));
                }
            }

            AddTask(ack);
        }
        String type = node.GetAttributeValue("type");
        if (type.equals("retry")) {
            LinkedList<String> jidList = new LinkedList<>();
            String jid = node.GetAttributeValue("participant");
            if (StringUtil.isEmpty(jid)) {
                jidList.add(node.GetAttributeValue("from"));
            } else {
                jidList.add(jid);
            }
            GetKeysFor(jidList, new HandleRetryGetKeysFor(node.GetAttributeValue("id")));
        }
        if (delegate_ != null) {
            delegate_.OnSync(node);
        }
    }


    byte[]  HandlePreKeyWhisperMessage(String recepid, ProtocolTreeNode encNode) throws UntrustedIdentityException, LegacyMessageException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, InvalidKeyException, InvalidKeyIdException {
        byte[] result =  axolotlManager_.DecryptPKMsg(recepid, encNode.GetData());
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleWhisperMessage(String recepid, ProtocolTreeNode encNode) throws NoSessionException, DuplicateMessageException, InvalidMessageException, UntrustedIdentityException, LegacyMessageException {
        byte[] result =  axolotlManager_.DecryptMsg(recepid, encNode.GetData());
        if (encNode.GetAttributeValue("v").equals("2")) {
            ParseAndHandleMessageProto(recepid, result);
        }
        return result;
    }

    byte[] HandleSenderKeyMessage(String recepid, ProtocolTreeNode messageNode) throws DuplicateMessageException, InvalidMessageException, LegacyMessageException {
        LinkedList<ProtocolTreeNode> encNodes = messageNode.GetChildren("enc");
        ProtocolTreeNode encNode = GetEncNode(encNodes, "skmsg");
        try {
            byte[] result =  axolotlManager_.GroupDecrypt(messageNode.GetAttributeValue("from"), recepid, encNode.GetData());
            ParseAndHandleMessageProto(recepid, result);
            return result;
        } catch (NoSessionException e) {
            SendRetry(messageNode);
        }
        return null;
    }

    void ParseAndHandleMessageProto(String recepid, byte[] serialData) {
        try {
            WhatsMessage.WhatsAppMessage message = WhatsMessage.WhatsAppMessage.parseFrom(serialData);
            if (message.hasSenderKeyDistributionMessage()) {
                axolotlManager_.GroupCreateSession(message.getSenderKeyDistributionMessage().getGroupId(), recepid, message.getSenderKeyDistributionMessage().getAxolotlSenderKeyDistributionMessage().toByteArray());
            }
        }
        catch (Exception e) {

        }
    }

    ProtocolTreeNode GetEncNode(LinkedList<ProtocolTreeNode> encNodes, String encType) {
        for (ProtocolTreeNode encNode : encNodes) {
            if (encNode.GetAttributeValue("type").equals(encType)) {
                return encNode;
            }
        }
        return null;
    }


    byte[] CombineDecodePoint(byte[] buffer) {
        byte[] result = new byte[buffer.length + 1];
        result[0] = 5;
        System.arraycopy(buffer, 0, result, 1, buffer.length);
        return result;
    }

    class HandleGetKeysFor implements  NodeCallback{
        NodeCallback resultCallback_;
        HandleGetKeysFor(NodeCallback resultCallback) {
            resultCallback_ = resultCallback;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            LinkedList<ProtocolTreeNode> listNode = result.GetChildren("list");
            if (listNode.isEmpty()) {
                Log.w(TAG, "GetKeysFor list empty");
                return;
            }

            LinkedList<ProtocolTreeNode> users = listNode.get(0).GetChildren("user");
            for (ProtocolTreeNode user : users) {
                try {
                    //保存会话
                    String jid = user.GetAttributeValue("jid");
                    String recepid = jid;
                    int index =recepid.indexOf("@");
                    if (index != -1) {
                        recepid = recepid.substring(0, index);
                    }
                     ProtocolTreeNode registration = user.GetChild("registration");
                    //pre key
                    ProtocolTreeNode preKeyNode = user.GetChild("key");
                    if (null == preKeyNode) {
                        Log.e(TAG, "没有获取到 prekey:" + user.toString());
                        continue;
                    }
                    ECPublicKey preKeyPublic = Curve.decodePoint(CombineDecodePoint(preKeyNode.GetChild("value").GetData()), 0);

                    //skey
                    ProtocolTreeNode skey = user.GetChild("skey");
                    ECPublicKey signedPreKeyPub = Curve.decodePoint(CombineDecodePoint(skey.GetChild("value").GetData()), 0);

                    //identity
                    ProtocolTreeNode identity = user.GetChild("identity");
                    IdentityKey identityKey = new IdentityKey(Curve.decodePoint(CombineDecodePoint(identity.GetData()),0));

                    PreKeyBundle preKeyBundle = new PreKeyBundle(DeAdjustId(registration.GetData()),0,DeAdjustId(preKeyNode.GetChild("id").GetData()),preKeyPublic,
                            DeAdjustId(skey.GetChild("id").GetData()),signedPreKeyPub,skey.GetChild("signature").GetData(),identityKey);

                    axolotlManager_.CreateSession(recepid, preKeyBundle);
                }
                catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
            resultCallback_.Run(srcNode, result);
        }
    }


    void GetKeysFor(List<String> jids, NodeCallback callback) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("xmlns", "encrypt"));
        iq.AddAttribute(new StanzaAttribute("type", "get"));
        iq.AddAttribute(new StanzaAttribute("to", "s.whatsapp.net"));
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));

        ProtocolTreeNode key = new ProtocolTreeNode("key");
        for (String jid: jids) {
            ProtocolTreeNode user = new ProtocolTreeNode("user");
            user.AddAttribute(new StanzaAttribute("jid", jid));
            key.AddChild(user);
        }
        iq.AddChild(key);
        AddTask(iq, new HandleGetKeysFor(callback));
    }

    void SendRetry(ProtocolTreeNode node) {
        String iqid = node.IqId();
         Integer count = retries_.get(iqid);
         if (count == null) {
             count = new Integer(1);
         } else {
             count++;
         }
         retries_.put(iqid, count);
         if (count >= 3) {
             retries_.remove(iqid);
             return;
         }
         ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
         receipt.AddAttribute(new StanzaAttribute("id", iqid));
        receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
        receipt.AddAttribute(new StanzaAttribute("type", "retry"));
        String participant = node.GetAttributeValue("participant");
        if (!StringUtil.isEmpty(participant)) {
            receipt.AddAttribute(new StanzaAttribute("participant", participant));
        }
        receipt.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));

        //retry
        ProtocolTreeNode retry = new ProtocolTreeNode("retry");
        retry.AddAttribute(new StanzaAttribute("count", String.valueOf(count)));
        retry.AddAttribute(new StanzaAttribute("v", "1"));
        retry.AddAttribute(new StanzaAttribute("id", iqid));
        retry.AddAttribute(new StanzaAttribute("t", node.GetAttributeValue("t")));
        receipt.AddChild(retry);

        //register
        ProtocolTreeNode registration = new ProtocolTreeNode("registration");
        registration.SetData(AdjustId(axolotlManager_.getLocalRegistrationId()));

        receipt.AddChild(registration);
        AddTask(receipt);
    }


    class HandlePendingMessage implements NodeCallback {
        ProtocolTreeNode pendingMessage_;
        HandlePendingMessage(ProtocolTreeNode pendingMessage) {
            pendingMessage_ = pendingMessage;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            HandleRecvMessage(pendingMessage_);
        }
    }

    void HandleRecvMessage(ProtocolTreeNode node) {
        LinkedList<ProtocolTreeNode> encNodes = node.GetChildren("enc");
        if (encNodes.isEmpty()) {
            return;
        }
        String participant = node.GetAttributeValue("participant");
        boolean isGroup = !StringUtil.isEmpty(participant);
        String senderJid = isGroup? participant :  node.GetAttributeValue("from");
        String recepid = senderJid;
        byte[] plainText = null;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        try {
            ProtocolTreeNode pkMsgEncNode = GetEncNode(encNodes, "pkmsg");
            if (pkMsgEncNode != null) {
                plainText = HandlePreKeyWhisperMessage(recepid, pkMsgEncNode);
            } else {
                ProtocolTreeNode whisperEncNode = GetEncNode(encNodes, "msg");
                if (whisperEncNode != null) {
                    plainText = HandleWhisperMessage(recepid, whisperEncNode);
                }
            }

            ProtocolTreeNode skMsgEncNode = GetEncNode(encNodes, "skmsg");
            if (skMsgEncNode != null) {
                plainText = HandleSenderKeyMessage(recepid, node);
            }

            retries_.remove(node.IqId());
        }
        catch (InvalidKeyIdException e) {
            Log.e(TAG, e.getLocalizedMessage());
            SendRetry(node);
        } catch (LegacyMessageException e) {
            e.printStackTrace();
        } catch (InvalidMessageException e) {
            SendRetry(node);
            Log.e(TAG, e.getLocalizedMessage());
        } catch (UntrustedIdentityException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (InvalidKeyException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (DuplicateMessageException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (InvalidVersionException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (NoSessionException e) {
            LinkedList<String> jidList = new LinkedList<>();
            jidList.add(senderJid);
            GetKeysFor(jidList, new HandlePendingMessage(node));
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        if (plainText != null) {
            try {
				if (null != delegate_) {
                	WhatsMessage.WhatsAppMessage msg = WhatsMessage.WhatsAppMessage.parseFrom(plainText);              
                    ProtocolTreeNode plainTextNode = new ProtocolTreeNode("plain");
                    plainTextNode.SetCustomParams(msg);
                    node.AddChild(plainTextNode);
                    delegate_.OnSync(node);
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        {
            //发送一个receipt
            ProtocolTreeNode receipt = new ProtocolTreeNode("receipt");
            receipt.AddAttribute(new StanzaAttribute("id", node.IqId()));
            receipt.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
            if (!participant.isEmpty()) {
                receipt.AddAttribute(new StanzaAttribute("participant", participant));
            }
            AddTask(receipt);
        }
    }

    void HandleAck(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("class");
        if (type.equals("message")) {
            if (delegate_ != null) {
                delegate_.OnPacketResponse("ack", node);
            }
        }
    }

    void HandleNotification(ProtocolTreeNode node) {
        String type = node.GetAttributeValue("type");
        if (type.equals("encrypt")) {
            ProtocolTreeNode child = node.GetChild("count");
            if (child != null) {
                axolotlManager_.LevelPreKeys(true);
                LinkedList<PreKeyRecord>  unsentPreKeys = axolotlManager_.LoadUnSendPreKey();
                FlushKeys(axolotlManager_.LoadLatestSignedPreKey(false),  unsentPreKeys);
            }
        }

        ProtocolTreeNode ack = new ProtocolTreeNode("ack");
        ack.AddAttribute(new StanzaAttribute("id", node.GetAttributeValue("id")));
        ack.AddAttribute(new StanzaAttribute("class", "notification"));
        ack.AddAttribute(new StanzaAttribute("to", node.GetAttributeValue("from")));
        if (!StringUtil.isEmpty(type)) {
            ack.AddAttribute(new StanzaAttribute("type", type));
        }
        String participant = node.GetAttributeValue("participant");
        if (!StringUtil.isEmpty(participant)) {
            ack.AddAttribute(new StanzaAttribute("participant", participant));
        }
        AddTask(ack);
        if (null != delegate_) {
            delegate_.OnSync(node);
        }
    }


    class HandleResult implements NodeCallback{
        String type_;
        HandleResult(String type) {
            type_ = type;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            if (null != delegate_) {
                delegate_.OnPacketResponse(type_, result);
            }
    }
    }

    public String CreateGroup(String subjectName, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", "g.us"));

        ProtocolTreeNode create = new ProtocolTreeNode("create");
        create.AddAttribute(new StanzaAttribute("subject", subjectName));
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            create.AddChild(participant);
        }
        node.AddChild(create);
        return AddTask(node, new HandleResult("CreateGroup"));
    }

    public String AcceptInviteToGroup(String token) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", "g.us"));

        ProtocolTreeNode invite = new ProtocolTreeNode("invite");
        invite.AddAttribute(new StanzaAttribute("code", token));
        node.AddChild(invite);
        return AddTask(node, new HandleResult("AcceptInviteToGroup"));
    }


    public String ModifyGroupSubject(String jid, String subjectName) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode subject = new ProtocolTreeNode("subject");
        subject.SetData(subjectName.getBytes(StandardCharsets.UTF_8));
        node.AddChild(subject);

        return AddTask(node, new HandleResult("ModifyGroupSubject"));
    }

    public String InviteGroupMembers(String jid, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode add = new ProtocolTreeNode("add");
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            add.AddChild(participant);
        }
        node.AddChild(add);
        return AddTask(node, new HandleResult("InviteGroupMembers"));
    }

    public String RemoveGroupMembers(String jid, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode remove = new ProtocolTreeNode("remove");
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            remove.AddChild(participant);
        }
        node.AddChild(remove);
        return AddTask(node, new HandleResult("RemoveGroupMembers"));
    }

    public String PromoteGroupMember(String jid, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode remove = new ProtocolTreeNode("promote");
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            remove.AddChild(participant);
        }
        node.AddChild(remove);
        return AddTask(node, new HandleResult("PromoteGroupMember"));
    }

    public String DemoteGroupMember(String jid, List<String> members) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", JidNormalize(jid)));

        ProtocolTreeNode demote = new ProtocolTreeNode("demote");
        for (String member : members) {
            ProtocolTreeNode participant = new ProtocolTreeNode("participant");
            participant.AddAttribute(new StanzaAttribute("jid", JidNormalize(member)));
            demote.AddChild(participant);
        }
        node.AddChild(demote);
        return AddTask(node, new HandleResult("DemoteGroupMember"));
    }


    public String LeaveGroup(List<String> groupJids) {
        ProtocolTreeNode node = new ProtocolTreeNode("iq");
        node.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        node.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));
        node.AddAttribute(new StanzaAttribute("type", "set"));
        node.AddAttribute(new StanzaAttribute("to", "g.us"));

        ProtocolTreeNode leave = new ProtocolTreeNode("leave");
        for (String groupJid : groupJids) {
            ProtocolTreeNode group = new ProtocolTreeNode("group");
            group.AddAttribute(new StanzaAttribute("id", JidNormalize(groupJid)));
            leave.AddChild(group);
        }
        node.AddChild(leave);
        return AddTask(node, new HandleResult("LeaveGroup"));
    }

    String GetGroupInfo(String jid) {
        return InnerGetGroupInfo(jid, new HandleResult("GetGroupInfo"));
    }

    public static byte[] AdjustId(int id) {
        String hex = Integer.toHexString(id);
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        byte[] baKeyword = new byte[hex.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                baKeyword[i] = (byte) (0xff & Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return baKeyword;
    }

    public int DeAdjustId(byte[] data) {
        //转成16进制
        String hex = StringUtil.BytesToHex(data);
        return new BigInteger(hex, 16).intValue();
    }


    void  FlushKeys(SignedPreKeyRecord signedPreKeyRecord, List<PreKeyRecord> unsentPreKeys) {
        StanzaAttribute[] attributes = new StanzaAttribute[4];
        attributes[0] = new StanzaAttribute("id", GenerateIqId());
        attributes[1] = new StanzaAttribute("xmlns", "encrypt");
        attributes[2] = new StanzaAttribute("type", "set");
        attributes[3] = new StanzaAttribute("to", "s.whatsapp.net");
        ProtocolTreeNode iqNode = new ProtocolTreeNode("iq", attributes);
        {
            //identify
            ProtocolTreeNode identity = new ProtocolTreeNode("identity");
            identity.SetData(axolotlManager_.GetIdentityKeyPair().getPublicKey().serialize(), 1, 32);
            iqNode.AddChild(identity);
        }

        {
            //registration
            ProtocolTreeNode registration = new ProtocolTreeNode("registration");
            registration.SetData(AdjustId(axolotlManager_.getLocalRegistrationId()));
            iqNode.AddChild(registration);
        }

        {
            //type
            ProtocolTreeNode type = new ProtocolTreeNode("type");
            type.SetData(new byte[]{5});
            iqNode.AddChild(type);
        }
        {
            // prekey 节点
            ProtocolTreeNode list = new ProtocolTreeNode("list");
            LinkedList<Integer> sentPrekeyIds = new LinkedList<>();
            for (int i=0; i< unsentPreKeys.size(); i++) {
                PreKeyRecord record = unsentPreKeys.get(i);
                sentPrekeyIds.add(record.getId());
                ProtocolTreeNode key = new ProtocolTreeNode("key");
                {
                    ProtocolTreeNode idNode = new ProtocolTreeNode("id");
                    idNode.SetData(AdjustId(record.getId()));
                    key.AddChild(idNode);
                }
                {
                    ProtocolTreeNode value = new ProtocolTreeNode("value");
                    value.SetData(record.getKeyPair().getPublicKey().serialize(), 1, 32);
                    key.AddChild(value);
                }
                list.AddChild(key);
            }
            iqNode.AddChild(list);
            iqNode.SetCustomParams(sentPrekeyIds);
        }

        {
            //signature  key skey
            ProtocolTreeNode skey = new ProtocolTreeNode("skey");
            {
                ProtocolTreeNode id = new ProtocolTreeNode("id");
                id.SetData(AdjustId(signedPreKeyRecord.getId()));
                skey.AddChild(id);
            }
            {
                ProtocolTreeNode value = new ProtocolTreeNode("value");
                value.SetData(signedPreKeyRecord.getKeyPair().getPublicKey().serialize(), 1, 32);
                skey.AddChild(value);
            }

            {
                ProtocolTreeNode signature = new ProtocolTreeNode("signature");
                signature.SetData(signedPreKeyRecord.getSignature());
                skey.AddChild(signature);
            }
            iqNode.AddChild(skey);
        }
        AddTask(iqNode, (srcNode, result) -> HandleFlushKey(srcNode,result));
    }

    String AddTask(ProtocolTreeNode node, NodeCallback callback) {
        if (null != callback) {
        	registerHandleMap_.put(node.IqId(), new NodeHandleInfo(callback, node));
        }
        return noiseHandshake_.SendNode(node);
    }

    String AddTask(ProtocolTreeNode node) {
        return AddTask(node, null);
    }

    boolean  HandleRegisterNode(ProtocolTreeNode node) {
        NodeHandleInfo  handleInfo = registerHandleMap_.remove(node.IqId());
        if (handleInfo == null) {
            return  false;
        }
        if (handleInfo.callbackRunnable == null) {
            return false;
        }
        handleInfo.callbackRunnable.Run(handleInfo.srcNode, node);
        return true;
    }

    ConcurrentHashMap<String, NodeHandleInfo> registerHandleMap_ = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Integer> retries_ = new ConcurrentHashMap<>();


    void HandleFlushKey(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
        StanzaAttribute type = result.GetAttribute("type");
        if ((type != null) && (type.value_.equals("result"))) {
            //更新db
            LinkedList<Integer> sentPrekeyIds = (LinkedList<Integer>)srcNode.GetCustomParams();
            axolotlManager_.SetAsSent(sentPrekeyIds);
        }
    }

    public static String JidNormalize(String jid) {
        int pos = jid.indexOf("@");
        if (pos != -1) {
            return jid;
        }
        pos = jid.indexOf("-");
        if (pos != -1) {
            return jid + "@g.us";
        }
        return jid + "@s.whatsapp.net";
    }


    void SaveSentMessage(String jid, byte[] serialData, String messageType, String mediaType, String iqId) {
        MessageInfo info = new MessageInfo();
        info.jid = jid;
        info.serialData = serialData;
        info.messageType = messageType;
        info.mediaType = mediaType;
        msgMap_.put(iqId, info);
    }

    String SendSerialData(String jid, byte[] serialData, String messageType, String mediaType, String iqId) {
        if (StringUtil.isEmpty(iqId)) {
            iqId = GenerateIqId();
        }
        jid = JidNormalize(jid);
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        boolean isGroup = jid.indexOf("-") != -1 ? true : false;
        if (isGroup) {
            return SendToGroup(jid, serialData, messageType, mediaType, iqId);
        } else if (axolotlManager_.ContainsSession(recepid)) {
            return SendToContact(jid, serialData, messageType, mediaType, iqId);
        } else {
            LinkedList<String> jids = new LinkedList<>();
            jids.add(jid);
            String finalJid = jid;
            String finalIqId = iqId;
            GetKeysFor(jids, (srcNode, result) -> SendToContact(finalJid, serialData,messageType, mediaType, finalIqId));
            return iqId;
        }
    }


    class HandleGetGroupInfo implements NodeCallback{
        byte[] message_;
        String messageType_;
        String mediaType_;
        String iqId_;
        HandleGetGroupInfo(byte[] message, String messageType, String mediaType, String iqId) {
            message_ = message;
            messageType_ = messageType;
            mediaType_ = mediaType;
            iqId_ = iqId;
        }
        @Override
        public void Run(ProtocolTreeNode srcNode, ProtocolTreeNode result) {
            ProtocolTreeNode group = result.GetChild("group");
            if (group == null) {
                Log.e(TAG, "获取群失败:" + srcNode.toString());
                return;
            }
            String selfJid = JidNormalize(envBuilder_.getFullphone());
            List<String> sessionJids = new LinkedList<>();
            LinkedList<ProtocolTreeNode>  participants = group.GetChildren("participant");
            for (ProtocolTreeNode participant : participants) {
                String jid = participant.GetAttributeValue("jid");
                if (jid.equals(selfJid)) {
                    continue;
                }
                sessionJids.add(jid);
            }
            EnsureSessionsAndSendToGroup(srcNode.GetAttributeValue("to"), sessionJids, message_, messageType_, mediaType_, iqId_);
        }
    }


    void EnsureSessionsAndSendToGroup(String groupId, List<String> sessionJids, byte[] message, String messageType, String mediaType, String iqId) {
        List<String> jidsNoSession = new LinkedList<>();
        for (String jid : sessionJids) {
            String recepid = jid;
            int index =recepid.indexOf("@");
            if (index != -1) {
                recepid = recepid.substring(0, index);
            }
            if (!axolotlManager_.ContainsSession(recepid)) {
                jidsNoSession.add(jid);
            }
        }
        if (jidsNoSession.isEmpty()) {
            try {
                SendToGroupWithSessions(groupId, sessionJids, message, messageType, mediaType, iqId);
            } catch (UntrustedIdentityException e) {
                e.printStackTrace();
            } catch (NoSessionException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        } else {
            GetKeysFor(jidsNoSession, (srcNode, result) -> {
                try {
                    SendToGroupWithSessions(groupId, jidsNoSession, message, messageType, mediaType, iqId);
                } catch (UntrustedIdentityException e) {
                    e.printStackTrace();
                } catch (NoSessionException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    void SendToGroupWithSessions(String groupId, List<String> sessionJids, byte[] message, String messageType, String mediaType, String iqId) throws UntrustedIdentityException, NoSessionException, InvalidKeyException {
        ProtocolTreeNode participants = new ProtocolTreeNode("participants");
        if (!sessionJids.isEmpty()) {
            SenderKeyDistributionMessage senderKeyDistributionMessage = axolotlManager_.GroupCreateSKMsg(groupId);
            ByteString senderKeySerial = ByteString.copyFrom(senderKeyDistributionMessage.serialize());
            for (String jid :  sessionJids) {
                String recepid = jid;
                int index =recepid.indexOf("@");
                if (index != -1) {
                    recepid = recepid.substring(0, index);
                }
                WhatsMessage.WhatsAppMessage.Builder sendKeyMessage = SerializeSenderKeyDistributionMessageToProtobuf(groupId, senderKeySerial);
                CiphertextMessage cipherText = axolotlManager_.Encrypt(recepid, sendKeyMessage.build().toByteArray());
                ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
                encNode.AddAttribute(new StanzaAttribute("v", "2"));
                encNode.SetData(cipherText.serialize());
                if (!StringUtil.isEmpty(mediaType)) {
                    encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
                }
                switch (cipherText.getType()) {
                    case CiphertextMessage.PREKEY_TYPE:
                    {
                        encNode.AddAttribute(new StanzaAttribute("type", "pkmsg"));
                    }
                    break;
                    case CiphertextMessage.SENDERKEY_TYPE:
                    {
                        encNode.AddAttribute(new StanzaAttribute("type", "skmsg"));
                    }
                    break;
                    default:
                    {
                        encNode.AddAttribute(new StanzaAttribute("type", "msg"));
                    }
                }
                ProtocolTreeNode to = new ProtocolTreeNode("to");
                to.AddAttribute(new StanzaAttribute("jid", jid));
                to.AddChild(encNode);
                participants.AddChild(to);
            }
        }
        //组装消息
        ProtocolTreeNode msg = new ProtocolTreeNode("message");
        msg.AddAttribute(new StanzaAttribute("id", iqId));
        msg.AddAttribute(new StanzaAttribute("type", messageType));
        msg.AddAttribute(new StanzaAttribute("to", groupId));
        msg.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));


        //添加group 消息
        ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
        encNode.AddAttribute(new StanzaAttribute("v", "2"));
        encNode.AddAttribute(new StanzaAttribute("type", "skmsg"));
        if (!StringUtil.isEmpty(mediaType)) {
            encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
        }
        encNode.SetData(axolotlManager_.GroupEncrypt(groupId, message));
        msg.AddChild(encNode);
        if (participants.GetChildren() != null) {
            msg.AddChild(participants);
        }
        AddTask(msg);
    }

    WhatsMessage.WhatsAppMessage.Builder SerializeSenderKeyDistributionMessageToProtobuf(String groupId, ByteString senderKeySerial) {
        WhatsMessage.WhatsAppMessage.Builder builder = WhatsMessage.WhatsAppMessage.newBuilder();
        WhatsMessage.WhatsAppSenderKeyDistributionMessage.Builder senderKeyBuilder = builder.getSenderKeyDistributionMessageBuilder();
        senderKeyBuilder.setGroupId(groupId);
        senderKeyBuilder.setAxolotlSenderKeyDistributionMessage(senderKeySerial);
        return builder;
    }

    String SendToGroup(String jid, byte[] message, String messageType, String mediaType, String iqId) {
        SenderKeyRecord senderKeyRecord = axolotlManager_.LoadSenderKey(jid);
        if (senderKeyRecord.isEmpty()) {
            //获取群信息
            InnerGetGroupInfo(jid, new HandleGetGroupInfo(message, messageType, mediaType,iqId));
        } else {
            try {
                SendToGroupWithSessions(jid, new LinkedList<>(), message, messageType, mediaType, iqId);
            } catch (UntrustedIdentityException e) {
                e.printStackTrace();
            } catch (NoSessionException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        return iqId;
    }

    String InnerGetGroupInfo(String jid, NodeCallback callback) {
        ProtocolTreeNode iq = new ProtocolTreeNode("iq");
        iq.AddAttribute(new StanzaAttribute("id", GenerateIqId()));
        iq.AddAttribute(new StanzaAttribute("type", "get"));
        iq.AddAttribute(new StanzaAttribute("to", jid));
        iq.AddAttribute(new StanzaAttribute("xmlns", "w:g2"));

        ProtocolTreeNode query = new ProtocolTreeNode("query");
        query.AddAttribute(new StanzaAttribute("request", "interactive"));
        iq.AddChild(query);
        return AddTask(iq, callback);
    }


    String SendToContact(String jid, byte[] message, String messageType, String mediaType, String iqId) {
        String recepid = jid;
        int index =recepid.indexOf("@");
        if (index != -1) {
            recepid = recepid.substring(0, index);
        }
        CiphertextMessage ciphertextMessage = null;
        try {
            ciphertextMessage = axolotlManager_.Encrypt(recepid, message);
            return SendEncMessage(jid ,ciphertextMessage.serialize(), ciphertextMessage.getType(),messageType, mediaType, iqId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return iqId;
    }

    String SendEncMessage(String jid, byte[] cipherText, int encType, String messageType, String mediaType, String iqId) {
        ProtocolTreeNode msg = new ProtocolTreeNode("message");
        if (StringUtil.isEmpty(iqId)) {
            iqId = GenerateIqId();
        }
        msg.AddAttribute(new StanzaAttribute("id", iqId));
        msg.AddAttribute(new StanzaAttribute("type", messageType));
        msg.AddAttribute(new StanzaAttribute("to", jid));
        msg.AddAttribute(new StanzaAttribute("t", String.valueOf(System.currentTimeMillis() / 1000)));

        //enc node
        ProtocolTreeNode encNode = new ProtocolTreeNode("enc");
        encNode.AddAttribute(new StanzaAttribute("v", "2"));
        encNode.SetData(cipherText);
        if (!StringUtil.isEmpty(mediaType)) {
            encNode.AddAttribute(new StanzaAttribute("mediatype", mediaType));
        }
        switch (encType) {
            case CiphertextMessage.PREKEY_TYPE:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "pkmsg"));
            }
            break;
            case CiphertextMessage.SENDERKEY_TYPE:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "skmsg"));
            }
            break;
            default:
            {
                encNode.AddAttribute(new StanzaAttribute("type", "msg"));
            }
        }
        msg.AddChild(encNode);
        return AddTask(msg);
    }

    private GorgeousEngineDelegate delegate_ = null;
    String GenerateIqId() {
        return String.format("%04d:%s", iqidIndex_.incrementAndGet(), idPrex_);
    }
    String idPrex_ = UUID.randomUUID().toString().replaceAll("-", "").substring(0,27);
    AtomicInteger iqidIndex_ = new AtomicInteger(0);
    String cdnAuthKey_;
    String cdnHost_;
}
