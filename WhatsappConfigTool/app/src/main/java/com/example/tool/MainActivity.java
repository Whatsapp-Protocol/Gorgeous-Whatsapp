package com.example.tool;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.env.DeviceEnv;
import com.google.protobuf.ByteString;
import com.stericson.RootShell.RootShell;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import android.util.Base64;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    static {
        System.loadLibrary("native-lib");
    }

    public static native String stringFromJNI(String source);
    public static native String getCountryInfo(String cc);


    private static final int REQUSET_CODE_STORAGE = 1;
    private String[] PERMISSION_STORAGE ={
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    String mainDbDir_;

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermission(this);
        if (RootShell.isRootAvailable())
        {
            Toast.makeText(this,"手机已经root", Toast.LENGTH_LONG).show();
        }
        else
        {
            Toast.makeText(this,"没有root", Toast.LENGTH_SHORT).show();
        }
        ((Button)findViewById(R.id.start)).setOnClickListener(this);
        ((Button)findViewById(R.id.export)).setOnClickListener(this);
        ((Button)findViewById(R.id.start_yowsup)).setOnClickListener(this);
    }

    private void verifyStoragePermission(Activity activity){
        //1检测权限
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission!= PermissionChecker.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity,PERMISSION_STORAGE,REQUSET_CODE_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0]== PermissionChecker.PERMISSION_GRANTED){
            //申请权限成功
            Toast.makeText(this,"授权SD卡权限成功",Toast.LENGTH_SHORT).show();
        }else {
            //申请权限失败
            Toast.makeText(this,"授权SD卡权限失败，可能会影响应用的使用",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View v) {
        if (!checkAppInstalled("com.whatsapp")) {
            Toast.makeText(this, "没有安装Whatsapp", Toast.LENGTH_LONG).show();
            return;
        }

        if (v.getId() == R.id.start){
            startCopyEnv();
        } else if (v.getId() == R.id.export) {
            ExportDb();
        } else if (v.getId() == R.id.start_yowsup) {
            StartCopyYowsConfig();
        }
    }


    void ExportDb() {
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_GET_CONTENT);
        File file = new File(mainDbDir_);
        intent.setDataAndType(Uri.fromFile(file), "*/*");
        startActivity(intent);
    }

    boolean checkAppInstalled(String pkgName) {
        if (pkgName== null || pkgName.isEmpty()) {
            return false;
        }
        PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            packageInfo = null;
            e.printStackTrace();
        }
        if(packageInfo == null) {
            return false;
        } else {
            return true;//true为安装了，false为未安装
        }
    }


    void StartCopyYowsConfig() {
        //创建目录
        String randomDir = String.format("/sdcard/Gorgeous/%d/", System.currentTimeMillis() / 1000);
        new File(randomDir).mkdirs();
        CopyDatabasesAndExport(randomDir, 1);
    }

    void startCopyEnv() {
        //创建目录
        String randomDir = String.format("/sdcard/Gorgeous/%d/", System.currentTimeMillis() / 1000);
        new File(randomDir).mkdirs();
        CopyDatabasesAndExport(randomDir, 0);
    }

    void CopyDatabasesAndExport(String random_dir, int exportType) {
        //拷贝
        String dbDir = "/data/data/com.whatsapp/databases";
        try
        {
            String command = String.format("cp -r %s %s", dbDir, random_dir);
            Shell shell = RootShell.getShell(true);
            Command cmd = new Command(
                    0,
                    command)
            {
                @Override
                public void commandCompleted(int id, int exitcode) {
                    //拷贝完成 database ，再拷贝shared_pref
                    CopyShared(random_dir, exportType);
                }
            };
            shell.add(cmd);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    void CopyShared(String random_dir, int exportType){
        String dbDir = "/data/data/com.whatsapp/shared_prefs";
        try
        {
            String command = String.format("cp -r %s %s", dbDir, random_dir);
            Shell shell = RootShell.getShell(true);
            Command cmd = new Command(
                    0,
                    command)
            {
                @Override
                public void commandCompleted(int id, int exitcode) {
                    //JNI 拷贝文件
                    if (exportType == 0) {
                        ConvertDb(random_dir);
                    } else {
                        ExportYowsupConfig(random_dir);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "转换完成",Toast.LENGTH_LONG).show();
                            ((EditText)findViewById(R.id.dest_path)).setText(mainDbDir_);
                        }
                    });
                }
            };
            shell.add(cmd);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static boolean fileCopy(String oldFilePath,String newFilePath) throws IOException {
        //如果原文件不存在
        if(!new File(oldFilePath).exists()){
            return false;
        }
        //获得原文件流
        FileInputStream inputStream = new FileInputStream(new File(oldFilePath));
        byte[] data = new byte[1024];
        //输出流
        FileOutputStream outputStream =new FileOutputStream(new File(newFilePath));
        //开始处理流
        while (inputStream.read(data) != -1) {
            outputStream.write(data);
        }
        inputStream.close();
        outputStream.close();
        return true;
    }

    void ParseKeyPair(String dir, JSONObject config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(new FileInputStream(new File(dir, "shared_prefs/keystore.xml")));
            Element root = dom.getDocumentElement();
            NodeList items = root.getElementsByTagName("string");
            for (int i = 0; i < items.getLength(); i++) {
                Element personNode = (Element) items.item(i);
                String key = personNode.getAttribute("name");
                if (key.equals("client_static_keypair_pwd_enc")){
                    String base64Decode = stringFromJNI(personNode.getTextContent());
                    config.put("client_static_keypair", base64Decode);
                } else if (key.equals("server_static_public")) {
                    config.put("server_static_public", personNode.getTextContent());
                } else if (key.equals("client_static_keypair")) {
                    config.put("client_static_keypair", personNode.getTextContent());
                }
            }
        }
        catch (Exception e){
            Log.e("keystore.xm", e.getMessage());
        }
    }

    void ParsePref(String dir,  JSONObject config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(new FileInputStream(new File(dir, "shared_prefs/com.whatsapp_preferences_light.xml")));
            Element root = dom.getDocumentElement();
            NodeList items = root.getElementsByTagName("string");
            for (int i = 0; i < items.getLength(); i++) {
                Element personNode = (Element) items.item(i);
                String key = personNode.getAttribute("name");
                String content = personNode.getTextContent();
                if (key.equals("registration_jid")){
                    config.put("phone", content);
                } else if (key.equals("version")) {

                } else if (key.equals("cc")) {
                    config.put("cc", content);
                    String countryInfo =  getCountryInfo(content);
                    JSONObject jsonObject = new JSONObject(countryInfo);
                    config.put("mcc", jsonObject.getString("mcc"));
                    config.put("mnc", jsonObject.getString("mnc"));
                    config.put("sim_mcc", jsonObject.getString("mcc"));
                    config.put("sim_mnc", jsonObject.getString("mnc"));
                } else if (key.equals("routing_info")) {
                    byte[] noWrapper = Base64.decode(content, Base64.NO_WRAP);
                    config.put("edge_routing_info", Base64.encodeToString(noWrapper, Base64.NO_WRAP));
                } else if (key.equals("phoneid_id")) {
                   config.put("expid", content);
                } else if (key.equals("perf_device_id")) {
                   config.put("fdid", content);
                    String exPid = content.substring(0, 20);
                   config.put("id", Base64.encodeToString(exPid.getBytes(),Base64.NO_WRAP));
                } else if (key.equals("push_name")) {
                }
            }
        }
        catch (Exception e){
            Log.e("keystore.xm", e.getMessage());
        }
    }

    void ExportYowsupConfig(String dir) {
        /*
                * {
            "__version__": 1,
            "cc": "<cc>",
            "client_static_keypair": "base64-client_static_keypair",
            "edge_routing_info": "base64-edge_routing_info",
            "expid": "base64-expid",
            "fdid": "uuid-fdid",
            "id": "base64-id-20bytes",
            "login": "<cc+in>",
            "mcc": "<mcc>",
            "mnc": "<mnc>",
            "phone": "<cc+in>",
            "server_static_public": "base64-server_static_public",
            "sim_mcc": "<sim_mcc>",
            "sim_mnc": "<sim_mnc>"
        }```
        * */
        try {
            JSONObject config = new JSONObject();
            config.put("__version__", 1);
            ParseKeyPair(dir, config);
            ParsePref(dir, config);
            mainDbDir_ = dir;
            FileOutputStream file = new FileOutputStream(new File(dir, "config.json"));
            file.write(config.toString().getBytes());
            file.close();

            try {
                String srcPath = new File(dir, "databases/axolotl.db").getAbsolutePath();
                if (!new File(srcPath).exists()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "拷贝数据库失败",Toast.LENGTH_LONG).show();
                        }
                    });
                    return ;
                }
                String destPath = new File(dir, "axolotl.db").getAbsolutePath();
                fileCopy(srcPath, destPath);
            }
            catch (Exception e){

            }
        }
        catch (Exception e) {

        }
    }

    void ConvertDb(String dir) {
        DeviceEnv.AndroidEnv.Builder envBuild = DeviceEnv.AndroidEnv.newBuilder();
        //Build.VERSION.RELEASE,Build.MANUFACTURER,Build.DEVICE,Build.DISPLAY
        envBuild.setChatDnsDomain("fb");
        DeviceEnv.UserAgent.Builder useragentBuild = DeviceEnv.UserAgent.newBuilder();
        useragentBuild.setPlatform(DeviceEnv.Platform.ANDROID);
        useragentBuild.setReleaseChannel(DeviceEnv.ReleaseChannel.RELEASE);
        useragentBuild.setOsVersion(Build.VERSION.RELEASE);
        useragentBuild.setManufacturer(Build.MANUFACTURER);
        useragentBuild.setDevice(Build.DEVICE);
        useragentBuild.setOsBuildNumber(Build.DISPLAY);
        envBuild.setUserAgent(useragentBuild);
        ParseKeyPair(dir, envBuild);
        ParsePref(dir, envBuild);
        try {
            String srcPath = new File(dir, "databases/axolotl.db").getAbsolutePath();
            if (!new File(srcPath).exists()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "拷贝数据库失败",Toast.LENGTH_LONG).show();
                    }
                });
                return ;
            }
            String destPath = new File(dir, "axolotl.db").getAbsolutePath();
            fileCopy(srcPath, destPath);
        }
        catch (Exception e){

        }

        try {
            String srcPath = new File(dir, "databases/axolotl.db-shm").getAbsolutePath();
            String destPath = new File(dir, "axolotl.db-shm").getAbsolutePath();
            fileCopy(srcPath, destPath);
        }
        catch (Exception e){

        }

        try {
            String srcPath = new File(dir, "databases/axolotl.db-wal").getAbsolutePath();
            String destPath = new File(dir, "axolotl.db-wal").getAbsolutePath();
            fileCopy(srcPath, destPath);
        }
        catch (Exception e){

        }


        //写入账号信息
        mainDbDir_ = dir;
        AxolotlSQLiteOpenHelper srcDb = new AxolotlSQLiteOpenHelper(this, new File(dir, "axolotl.db").getAbsolutePath());
        SQLiteDatabase writeDb = srcDb.getWritableDatabase();
        writeDb.beginTransaction();
        //创建config 表
        try {
            writeDb.execSQL("CREATE TABLE IF NOT EXISTS settings(`key` text PRIMARY KEY,value text)");
            ContentValues values = new ContentValues();
            values.put("key", "env");
            values.put("value", Base64.encodeToString(envBuild.build().toByteArray(), Base64.NO_WRAP));
            writeDb.insert("settings", null, values);
            writeDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("TAG", e.getMessage());
        } finally {
            writeDb.endTransaction();
            writeDb.close();
        }
    }

    void ParsePref(String dir, DeviceEnv.AndroidEnv.Builder envBuild) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(new FileInputStream(new File(dir, "shared_prefs/com.whatsapp_preferences_light.xml")));
            Element root = dom.getDocumentElement();
            NodeList items = root.getElementsByTagName("string");
            for (int i = 0; i < items.getLength(); i++) {
                Element personNode = (Element) items.item(i);
                String key = personNode.getAttribute("name");
                String content = personNode.getTextContent();
                if (key.equals("registration_jid")){
                   envBuild.setFullphone(content);
                } else if (key.equals("routing_info")){
                    envBuild.setEdgeRoutingInfo(ByteString.copyFrom(Base64.decode(content, 0)));
                } else if (key.equals("version")) {
                    String[] versions = content.split("\\.");
                    DeviceEnv.AppVersion.Builder appVersionOrBuilder = envBuild.getUserAgentBuilder().getAppVersionBuilder();
                    appVersionOrBuilder.setPrimary(Integer.valueOf(versions[0]));
                    appVersionOrBuilder.setSecondary(Integer.valueOf(versions[1]));
                    appVersionOrBuilder.setTertiary(Integer.valueOf(versions[2]));
                    if (versions.length >= 4) {
                        appVersionOrBuilder.setQuaternary(Integer.valueOf(versions[3]));
                    }
                } else if (key.equals("cc")) {
                     String countryInfo =  getCountryInfo(content);
                    JSONObject jsonObject = new JSONObject(countryInfo);
                    DeviceEnv.UserAgent.Builder  userAgentBuilder = envBuild.getUserAgentBuilder();
                    userAgentBuilder.setMcc(jsonObject.getString("mcc"));
                    userAgentBuilder.setMnc(jsonObject.getString("mnc"));
                    userAgentBuilder.setLocaleLanguageIso6391(jsonObject.getString("iso639"));
                    userAgentBuilder.setLocaleCountryIso31661Alpha2(jsonObject.getString("iso3166"));
                } else if (key.equals("routing_info")) {
                    envBuild.setEdgeRoutingInfo(ByteString.copyFrom(Base64.decode(content, 0)));
                } else if (key.equals("phoneid_id")) {
                    envBuild.getUserAgentBuilder().setPhoneId(content);
                } else if (key.equals("perf_device_id")) {
                    envBuild.setFdid(content);
                    String exPid = content.substring(0, 20);
                    envBuild.setExpid(ByteString.copyFrom(exPid, "UTF-8"));
                } else if (key.equals("push_name")) {
                    envBuild.setPushname(content);
                }
            }
        }
        catch (Exception e){
            Log.e("keystore.xm", e.getMessage());
        }
    }


    void ParseKeyPair(String dir, DeviceEnv.AndroidEnv.Builder envBuild) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(new FileInputStream(new File(dir, "shared_prefs/keystore.xml")));
            Element root = dom.getDocumentElement();
            NodeList items = root.getElementsByTagName("string");
            for (int i = 0; i < items.getLength(); i++) {
                Element personNode = (Element) items.item(i);
                String key = personNode.getAttribute("name");
                if (key.equals("client_static_keypair_pwd_enc")){
                    String base64Decode = stringFromJNI(personNode.getTextContent());
                    byte[] decrpyt = Base64.decode(base64Decode, 0);
                    com.env.DeviceEnv.KeyPair.Builder keyBuild = envBuild.getClientStaticKeyPairBuilder();
                    keyBuild.setStrPrivateKey(ByteString.copyFrom(decrpyt,0 ,32));
                    keyBuild.setStrPubKey(ByteString.copyFrom(decrpyt,32,32));
                } else if (key.equals("server_static_public")) {
                    envBuild.setServerStaticPublic(ByteString.copyFrom(Base64.decode(personNode.getTextContent(), 0)));
                } else if (key.equals("client_static_keypair")) {
                    byte[] b64Keypair = Base64.decode(personNode.getTextContent(), 0);
                    com.env.DeviceEnv.KeyPair.Builder keyBuild = envBuild.getClientStaticKeyPairBuilder();
                    keyBuild.setStrPrivateKey(ByteString.copyFrom(b64Keypair, 0, 32));
                    keyBuild.setStrPubKey(ByteString.copyFrom(b64Keypair, 32, 32));
                }
            }
        }
        catch (Exception e){
            Log.e("keystore.xm", e.getMessage());
        }
    }
}