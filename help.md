# this is help file, MD format

# task_id must be unique.

# Login
````java
    //must set socks5 proxy
    JSONObject command = new JSONObject();
    command.put("command", "login");
    command.put("config", "xxxx"); // upload config first
    command.put("proxy_server", "xxx");
    command.put("proxy_port", 123);
    command.put("proxy_username", ""); //optional
    command.put("proxy_password", ""); //optional
````

# LoginWithPhone
````java
    //must set socks5 proxy
    JSONObject command = new JSONObject();
    command.put("command", "loginphone");
    command.put("fullphone	", "xxxx"); 
    command.put("proxy_server", "xxx");
    command.put("proxy_port", 123);
    command.put("proxy_username", ""); //optional
    command.put("proxy_password", ""); //optional
````


# SendText
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SendText");
    command.put("jid", "xxxx");
    command.put("content", "xxx");
````

# GetHDHead
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "GetHDHead");
    command.put("jid", "xxxx");
````

# SetHDHead
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SetHDHead");
    command.put("path", "xxxx");
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

# SendVcard
````java
    JSONObject command = new JSONObject();
    command.put("task_id", 111);
    command.put("command", "SendVcard");
    command.put("jid", "sendvcard");
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
    command.put("command", "subscribe");
    command.put("jid", "xxx");
````


# Exist
````java
    JSONObject command = new JSONObject();
    command.put("command", "exist");
    command.put("cc", "xxx");
    command.put("phone", "xxx");
    command.put("proxy_server", "xxx");
    command.put("proxy_port", 123);
    command.put("proxy_username", ""); //optional
    command.put("proxy_password", ""); //optional
````