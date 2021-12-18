# this is help file, MD format
# ws://8.210.124.139:16090
# task_id must be unique.
The websocket maximum frame supports 10M, and the media file sent cannot exceed 10M, otherwise the websocket will be disconnected.

# register whatsapp account
## 1) check account exist

````java
    command.put("command", "CheckAccountExist");
    command.put("token", "test");
    command.put("cc", "91");
    command.put("phone", "9897409751");

    command.put("proxy_type", 0); // -1 no proxy, 0 http proxy, 1 socks5 proxy
    command.put("proxy_server", "192.168.56.1");
    command.put("proxy_port", 1080);
    command.put("proxy_username", ""); //optional
    command.put("proxy_password", ""); //optional

````
## 2) code request
````java
    command.put("command", "CodeRequest");
````

## 3) register
````java
    command.put("command", "Register");
    command.put("code", "1234");
    //If the registration is successful, decode the response field base64 and save it to a file, and then you can use this file to log in


    //after you get response, use code below, and save config to file, then you can login with the config file
    JSONObject json = JSONObject.parseObject(response);
    String command = json.getString("command");
    if ("Register".equals(command) && json.getIntValue("code") == 0) {
        //save config
        String dataDir = System.getProperty("user.dir") + "/register";
        new File(dataDir).mkdirs();
        String configPath = new File(dataDir, "config").getAbsolutePath();
        FileUtil.WriteFileContent(Base64.getDecoder().decode(json.getString("content")), configPath);
        System.out.println("register ok, You can log in using :" + configPath);
    }
````


# Login
````java
    //if     
    command.put("token", "test");
    command.put("command", "login");
    command.put("userName", "xxxx");
    command.put("token", "test");
    //config If you have logged in, you can leave it out.
    command.put("config", Base64.getEncoder().encodeToString(StringUtil.ReadFileContent("xxxx")));
    command.put("proxy_type",-1); // -1 no proxy, 0 http proxy, 1 socks5 proxy
    command.put("proxy_server", "xxx");
    command.put("proxy_port", 1234);
    command.put("proxy_username", ""); //optional
    command.put("proxy_password", ""); //optional
    command.put("reset", false); //optional if true  Will use the config you passed in.
````

# SyncContact
````java
    JSONObject command = new JSONObject();
    command.put("command", "SyncContact");
    JSONArray phones = new JSONArray();
    phones.add("+66xxxx6");
     command.put("task_id", 1);
    command.put("phones", phones);


````

# GetHDHead
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 3);
    command.put("command", "GetHDHead");
    command.put("jid", "xxxx");
````

# SetHDHead
````java
    // 192 * 192
    JSONObject command = new JSONObject();
    command.put("task_id", 4);
    command.put("command", "SetHDHeadData");
    command.put("content", "xxxx"); // base64(file content)
````
# SetPushName
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SetPushName");
    command.put("pushname", "xxxx");
````

# SetStatus
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SetStatus");
    command.put("status", "xxxx");
````

# CreateGroup
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "CreateGroup");
    command.put("subject", "subject");

    
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("members", members);
````

# ModifyGroupSubject
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "ModifyGroupSubject");
    command.put("jid", "xxxx");
    command.put("subject", "xxxx");
````

# ModifyGroupDesc
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "ModifyGroupDesc");
    command.put("jid", "xxxx");
    command.put("desc", "xxxx");

````

# GetInviteLink
````java
    // //https://chat.whatsapp.com/ + "code"
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "GetInviteLink");
    command.put("jid", "xxxx");
    command.put("reset", false); 

````

# AcceptInviteToGroup
````java
    // //https://chat.whatsapp.com/ + "code"
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "AcceptInviteToGroup");
    command.put("token", "xxxx");

````

# InviteGroupMember
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "InviteGroupMembers");
    command.put("jid", "xxx");

    
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("members", members);
````

# RemoveGroupMember
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "RemoveGroupMembers");
    command.put("jid", "xxx");

    
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("members", members);
````

# PromoteGroupMembers
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "PromoteGroupMembers");
    command.put("jid", "xxx");

    
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("members", members);
````


# DemoteGroupMembers
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "DemoteGroupMembers");
    command.put("jid", "xxx");

    
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("members", members);
````

# GetGroupInfo
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "GetGroupInfo");
    command.put("jid", "xxxx");
````

# LeaveGroups
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "LeaveGroups");
    
    
        
    JSONArray members = new JSONArray();
    members.put("111");
    members.put("2222");
    command.put("groups", members);
````


# SendText
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 2);
    command.put("command", "SendText");
    command.put("jid", "xxxx");
    command.put("content", "xxx");
````

# SendImage
````java
     command.put("task_id", 4);
    command.put("command", "SendImage");
    command.put("jid", "xxx");
    command.put("data", Base64.getEncoder().encodeToString(xx.ReadFileContent("xx")));
    command.put("thumbnail", Base64.getEncoder().encodeToString(xx.ReadFileContent("xxx")));
    command.put("caption", "hellokg");
````

# SendDocument
````java
    command.put("task_id", 4);
    command.put("command", "SendDocument");
    command.put("jid", "xxxx@g.us");
    command.put("data", Base64.getEncoder().encodeToString(xx.ReadFileContent("xxxx")));
    command.put("filename", "xxxx");
````


# SendVideo
````java
     command.put("task_id", 4);
    command.put("command", "SendVideo");
    command.put("jid", "xxx@s.whatsapp.net");
    command.put("data", Base64.getEncoder().encodeToString(StringUtil.ReadFileContent("xxx.mp4")));
    command.put("thumbnail", Base64.getEncoder().encodeToString(StringUtil.ReadFileContent("main.jpg")));
    command.put("caption", "hellokg");
    command.put("duration", 13000);
    command.put("width", 465);
    command.put("height", 892);
````


# SendPTT
````java
    // AV_CODEC_ID_OPUS, 48000, 64 * 1000, 1
    command.put("task_id", 4);
    command.put("command", "SendPTT");
    command.put("jid", "xxx@s.whatsapp.net");
    command.put("data", Base64.getEncoder().encodeToString(StringUtil.ReadFileContent("ptt.ogg")));
                  
````

# SendVcard
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SendVcard");
    command.put("jid", "xxx@s.whatsapp.net");
    command.put("showname", "abc");
    command.put("vcard", "BEGIN:VCARD\nVERSION:3.0\nN:;;;;\nFN:681330000000\nitem1.TEL:+68 133 000 0000\nitem1.X-ABLabel:xxx\nEND:VCARD");
````

# SendLocation
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SendLocation");
    command.put("jid", "sendvcard");
    command.put("latitude", 98.032313465);
    command.put("longitude", 10.202121212);
    command.put("name", "xx");
    command.put("address", "xx");
    command.put("comment", "xx");
````

# Subscribe
````java
    JSONObject command = new JSONObject();
    command.put("command", "Subscribe");
    command.put("jid", "xxx");
````
