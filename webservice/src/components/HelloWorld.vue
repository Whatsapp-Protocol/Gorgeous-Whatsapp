<template>
  <div class="hello">
    <h1>{{ msg }}</h1>
	<el-button slot="append" type="primary" @click = 'drawer = true' icon="el-icon-question">Help</el-button>
   
   <el-button type="primary" @click="proxyVisible = true">设置代理</el-button>
   
   <el-link type="primary" href="https://github.com/lovethiscode/Gorgeous-Whatsapp">ContactMe</el-link>
   
   <el-input v-model="host" placeholder="websocket server">
   </el-input>
   
    <el-divider content-position="left">Login/Register</el-divider>
	<div >
	  <el-input placeholder="Country Code" v-model="cc">
	    <template slot="prepend">cc:</template>
	  </el-input>
	  
	  <el-input placeholder="Phone" v-model="phone" style="margin-top: 5px;">
	    <template slot="prepend">phone:</template>	

		 <template slot="append">
			 <div v-show="showCode">			 
				  <el-radio v-model="codetype" label="1">SMS</el-radio>
				  <el-radio v-model="codetype" label="2">Voice</el-radio>
				 <el-button type="primary" @click="CodeRequest">CodeRequest</el-button>
			 </div>
		 </template>
	  </el-input>
	  
	  <el-input placeholder="SMS code or Voice code" v-model="code" style="margin-top: 5px;" v-show = "showCode">
	    <template slot="prepend">code:</template>		
		<template slot="append">
			  <el-button type="primary" @click="Verify">Verify</el-button>
		</template>
	  </el-input>
	</div>
	
   <el-row :gutter="20" style="margin-top: 5px;">
		<el-button type="primary" @click="Login">Login</el-button>		
		<el-button type="primary" @click="Register">Register</el-button>
   </el-row>
   
   <el-divider content-position="left">Command</el-divider>
   <el-upload
     class="upload-demo"
	 action=""
     :on-success="HandleSendFile"
     multiple
     :limit="1">
     <el-button size="small" type="primary">发送文件</el-button>
   </el-upload>
   
   
   <el-input style="margin-top: 5px;"
     placeholder="please input command"
     v-model="command"
     clearable>
	  <el-button slot="append" type="primary" @click = 'Send' icon="el-icon-s-promotion"></el-button>
   </el-input>
   
   <el-input style="margin-top: 5px;"
     type="textarea"
     :rows="5"
     placeholder=""
     v-model="log">
   </el-input>
   
   
   <el-dialog
     title="提示"
     :visible.sync="errorDialogVisible"
     width="80%"
     center>
     <span>{{errorContent}}</span>
     <span slot="footer" class="dialog-footer">
       <el-button type="primary" @click="errorDialogVisible = false">确 定</el-button>
     </span>
   </el-dialog>
   
   <el-dialog title="设置代理" :visible.sync="proxyVisible">
     <el-form :model="proxyInfo">
       <el-form-item label="服务器" :label-width="formLabelWidth">
         <el-input v-model="proxyInfo.server" autocomplete="off"></el-input>
       </el-form-item>
	   <el-form-item label="端口" :label-width="formLabelWidth">
	     <el-input v-model="proxyInfo.port" autocomplete="off"></el-input>
	   </el-form-item>
       <el-form-item label="代理类型" :label-width="formLabelWidth">
         <el-select v-model="proxyInfo.proxyType" placeholder="选择代理类型">
           <el-option label="http" value="http"></el-option>
           <el-option label="socks5" value="socks"></el-option>
         </el-select>
       </el-form-item>
	   <el-form-item label="用户名(可选)" :label-width="formLabelWidth">
	     <el-input v-model="proxyInfo.username" autocomplete="off"></el-input>
	   </el-form-item>
	   
	   <el-form-item label="密码(可选)" :label-width="formLabelWidth">
	     <el-input v-model="proxyInfo.password" autocomplete="off"></el-input>
	   </el-form-item>
     </el-form>
     <div slot="footer" class="dialog-footer">
       <el-button @click="HandleCancelProxy">取 消</el-button>
       <el-button type="primary" @click="HandleSetProxy">确 定</el-button>
     </div>
	  <el-checkbox v-model="enableProxy">启用代理</el-checkbox>
   </el-dialog>
   
   
   
   <el-drawer
     :visible.sync="drawer"
     :with-header="false">
    <div>
		<el-table
		      :data="commandTable"
		      style="width: 100%">
		      <el-table-column
		        prop="id"
		        label="功能"
		        width="180">
		      </el-table-column>
		      <el-table-column
		        prop="command"
		        label="命令">
		      </el-table-column>
			  
			   <el-table-column
				fixed="right"
				label="操作"
				width="100">
				<template slot-scope="scope">
				  <el-button @click="Copy(scope.row)" type="text" size="small">复制命令</el-button>
				</template>
			</el-table-column>
		</el-table>
	</div>
   </el-drawer>
   
  </div>
</template>

<script>
export default {
  name: 'HelloWorld',
  props: {
    msg: String
  },
  data() {
      return {
        host: 'ws://8.210.124.139:16090',
		cc: '55',
		phone: '11931514584',
		code: '',
		log : '',
		codetype: '1',
		command: '',
		errorDialogVisible : false,
		errorContent: '',
		websocket : null,
		showCode : false,
		taskId : 0,
		isLogin : false,
		drawer : false,
		heartBeatTimer : null,
		proxyVisible : false,
		enableProxy : false,
		proxyInfo: {
		          server: '',
				  port: 80,
		          proxyType: 'http',
		          username: '',
		          password: ''
		        },
		 formLabelWidth: '120px',
		commandTable: [{
			id: '测试账号',
			command: 'cc: 48  phone: 515420137'
		   },{
			id: '同步联系人',
			command: 'SyncContact +661212 +123218 +819822'
		  },{
			id: '发送文本消息',
			command: 'SendText 8541264751254 hello'
		  },{
			id: '获取头像',
			command: 'GetHDHead 6645824511'
		  },{
			id: '设置显示名',
			command: 'SetPushName myname'
		  },{
			id: '设置状态',
			command: 'SetStatus mystatus'
		  },{
			id: '创建群',
			command: 'CreateGroup mygroup 686564512 78121545 7832154981'
		  },{
			id: '修改群主题',
			command: 'ModifyGroupSubject 123122132@g.us newsubject'
		  },{
			id: '修改群描述',
			command: 'ModifyGroupDesc 123122132@g.us newdesc'
		  },{
			id: '邀请加入群',
			command: 'ModifyGroupDesc 123122132@g.us 6541567412 872131571 654024645'
		  },{
			id: '踢出群成员',
			command: 'RemoveGroupMembers 123122132@g.us 6541567412 872131571 654024645'
		  },{
			id: '设置管理员',
			command: 'PromoteGroupMembers 123122132@g.us 6541567412 872131571 654024645'
		  },{
			id: '删除管理员',
			command: 'DemoteGroupMembers 123122132@g.us 6541567412 872131571 654024645'
		  },{
			id: '获取群信息',
			command: 'GetGroupInfo 123122132@g.us'
		  },{
			id: '接受群邀请',
			command: 'AcceptInviteToGroup afjjfasdfnsdfl'
		  },{
			id: '获取群链接',
			command: 'GetInviteLink 123122132@g.us'
		  },{
			id: '离开群',
			command: 'LeaveGroups 123122132@g.us 16546132165@g.us'
		  },{
			id: '订阅状态',
			command: 'Subscribe 54567121654'
		  },{
			id: '发送位置',
			command: 'SendLocation 54567121654 22.59202003479004 113.96762084960938 name address comment'
		  },{
			id: '发送名片',
			command: 'SendVcard 54567121654 myname BEGIN:VCARD\nVERSION:3.0\nN:;;;;\nFN:681330000000\nitem1.TEL:+681330000000\nitem1.X-ABLabel:xxx\nEND:VCARD'
		  }]
      }
    },
	mounted(){
	      this.InitWebsocket()
		  this.InitLocalStorage()
		  if(this.enableProxy) {
			  this.PrintfLog("Proxy enabled")
		  }
	    }, 

	methods:{
		HandleSendFile: function(file, fileList) {
			var reader = new FileReader()
			var data =  reader.readAsBinaryString(file.raw)
			this.PrintfLog("")
		},
		HandleCancelProxy: function() {
			this.InitLocalStorage()
			this.proxyVisible = false
		},
		InitLocalStorage() {
			var proxyInfo = localStorage.getItem("proxyInfo")
			if(proxyInfo) {
				this.proxyInfo = JSON.parse(proxyInfo)
			}
			this.enableProxy = localStorage.getItem("enableProxy") == "true" ?  true : false
		},
		HandleSetProxy: function() {
			if(this.proxyInfo.server == "" || this.proxyInfo.port == "") {
				if(this.enableProxy) {
					return
				}
			}

			localStorage.setItem("proxyInfo", JSON.stringify(this.proxyInfo))
			localStorage.setItem("enableProxy", this.enableProxy)
			this.proxyVisible = false
		},
		InitWebsocket: function() {
			this.websocket = new WebSocket(this.host);
			this.websocket.onopen = (evt) => {
				this.PrintfLog("connect server success")
				 this.heartBeatTimer = setInterval(() => { 
					 var command = {}
					 command["command"] =  "HeartBeat";
					this.SendCommand(command)
				 }, 1000 * 60);
			};
			this.websocket.onclose = (evt) =>{
				this.PrintfLog("disconnect:" + evt.reason)
				this.websocket = null
				clearInterval(this.heartBeatTimer);
			};
			this.websocket.onmessage = (evt) =>{
				this.HandleMessage(evt.data)
			};
			this.websocket.onerror = (evt) =>{
				this.PrintfLog("error")
				this.websocket = null
				clearInterval(this.heartBeatTimer);
			};
		}, 
		HandleMessage: function(response) {
			this.PrintfLog(response)
			var json = JSON.parse(response);
			var command = json["command"] 
			if(command == "CheckAccountExist") {
				this.HandleCheckAccountExist(json)
			} else if(command == "CodeRequest") {
				this.HandleCodeRequest(json)
			} else if(command == "Register") {
				this.HandleRegister(json)
			} else if (command == "login") {
				this.HandleLogin(json)
			}
		},
		HandleLogin : function(json) {
			if(json["code"] == 0) {
				this.isLogin = true
			} else {
				this.isLogin = false
			}
		},
		HandleRegister : function() {
			
		}, 
		Verify: function(event) {
			if(this.code == "") {
				this.errorContent = "验证码不能为空"
				this.errorDialogVisible = true
				return
			}
			this.PrintfLog("正在注册...")
			var command = {}
			command["command"] =  "Register";
			command["code"] =  this.code;
			this.SendCommand(command)
		},
		HandleCodeRequest : function(json) {
			var content  = JSON.parse(json["content"])
			if(content["status"] == "fail") {
				this.PrintfLog("发送验证码请求失败")
			} else {
				this.PrintfLog("发送验证码请求成功，等待输入验证码...")
			}
		},
		
		HandleCheckAccountExist : function(json) {
			if(json["code"] == 0) {
				this.PrintfLog("可以开始登录了")
			} else {
				this.PrintfLog("正在请求验证码")
				//发送验证码
				var command = {}
				command["command"] =  "CodeRequest";
				if(this.codetype == "1") {
					command["method"] =  "sms";
				} else {
					command["method"] =  "voice";
				}
				this.SendCommand(command)
			}
		},
		CodeRequest: function(event) {
			//先检查账号
			if ((this.cc == '') || (this.phone == '')) {
				this.errorContent = "cc or phone can't be empty"
				this.errorDialogVisible = true
				return
			}
			this.log += "检查账号...\n"
			var command = {}
			command["command"] =  "CheckAccountExist";
			command["token"] =  "test";
			command["cc"] =  this.cc;
			command["phone"] =  this.phone;
			if(this.enableProxy && this.proxyInfo.server != "") {
				if(this.proxyInfo.proxyType == "http") {
					command["proxy_type"] = 0
				} else if(this.proxyInfo.proxyType == "socks5") {
					command["proxy_type"] = 1
				} else {
					command["proxy_type"] = -1
				}
				
				command["proxy_server"] = this.proxyInfo.server
				command["proxy_port"] = Number(this.proxyInfo.port)
				if(this.proxyInfo.username != "") {
					command["proxy_username"] = this.proxyInfo.username
					command["proxy_password"] = this.proxyInfo.password
				}
			}
			this.SendCommand(command)
		},
		
		Login: function(event) {
			if(this.showCode) {
				this.showCode = false
				return
			}
			if ((this.cc == '') || (this.phone == '')) {
				this.errorContent = "cc or phone can't be empty"
				this.errorDialogVisible = true
				return
			}
			var command = {}
			command["command"] =  "login";
			command["token"] =  "test";
			command["userName"] =  this.cc + this.phone;
			command["config"] = "none"
			if(this.enableProxy && this.proxyInfo.server != "") {
				if(this.proxyInfo.proxyType == "http") {
					command["proxy_type"] = 0
				} else if(this.proxyInfo.proxyType == "socks5") {
					command["proxy_type"] = 1
				} else {
					command["proxy_type"] = -1
				}
				
				command["proxy_server"] = this.proxyInfo.server
				command["proxy_port"] = Number(this.proxyInfo.port)
				if(this.proxyInfo.username != "") {
					command["proxy_username"] = this.proxyInfo.username
					command["proxy_password"] = this.proxyInfo.password
				}
			}
			this.SendCommand(command)
		},
		SendCommand: function(command) {
			if(this.websocket == null) {
				this.PrintfLog("需要先连接服务器， 或者刷新一下页面重连")
				return
			}
			var stringCommand = JSON.stringify(command)
			this.PrintfLog("发送命令: " + stringCommand)
			this.websocket.send(stringCommand);
		},
		Register : function(event) {
			this.showCode = true
		},
		PrintfLog: function(log) {
			this.log += log
			this.log += "\n"
		},
		Copy: function(row) {
			this.command = row.command
		},
		Send: function() {
			if(this.command == "") {
				return
			}
			if(!this.isLogin) {
				this.errorContent = "需要先登录"
				this.errorDialogVisible = true
				return;
			}
			var args = this.command.split(" ")
			var commands = {}
			commands["command"] = args[0]
			commands["task_id"] = ++this.taskId
			
			var commandId = args[0]
			switch(commandId) {
				case "SyncContact":{
					 //SyncContact +661212 +123218 +819822
					var phones = []
					for(var i=1; i< args.length;i++) {
						phones.push(args[i])
					}
					commands["phones"] = phones
					break
				}
				case "SendText" :{
					//SendText 8541264751254 hello
					commands["jid"] = args[1]
					commands["content"] = args[2]
					break
				}
				case "SetHDHeadData": {
					// jpg resolution = 192 * 192
					//SetHDHeadData D:\abc.jpg
					break
				}
				case "GetHDHead": {		
					//GetHDHead 6645824511
					commands["jid"] = args[1]
					break
				}
				case "SetPushName": {
					//SetPushName myname
					commands["pushname"] = args[1]
					break
				}
				case "SetStatus": {
					 //SetStatus mystatus
					commands["status"] = args[1]
					break
				}
				case "CreateGroup": {
					 //CreateGroup mygroup 686564512 78121545 7832154981
					commands["subject"] = args[1]
					var members = []
					for(var i=2; i< args.length;i++) {
						members.push(args[i])
					}
					commands["members"] = members
					break
				}
				case "ModifyGroupSubject": {
					//ModifyGroupSubject 123122132@g.us newsubject
					commands["jid"] = args[1]
					commands["subject"] = args[2]
					break
				}
				case "ModifyGroupDesc": {
					//ModifyGroupDesc 123122132@g.us newdesc
					commands["jid"] = args[1]
					commands["desc"] = args[2]
					break
				}
				case "InviteGroupMembers":
				case "RemoveGroupMembers":
				case "PromoteGroupMembers":
				case "DemoteGroupMembers":
				{
					commands["jid"] = args[1]
					var members = []
					for(var i=2; i< args.length;i++) {
						members.push(args[i])
					}
					commands["members"] = members
					break
				}
				case "GetGroupInfo": {
					commands["jid"] = args[1]
					break
				}
				case "GetInviteLink": {
					commands["jid"] = args[1]
					commands["reset"] = false
					break
				}
				case "AcceptInviteToGroup": {
					commands["token"] = args[1]
					break
				}
				case "LeaveGroups": {
					var groups = []
					for(var i=1; i< args.length;i++) {
						groups.push(args[i])
					}
					commands["groups"] = groups
					break
				}
				case "Subscribe": {
					commands["jid"] = args[1]
					break
				}
				case "SendDocument": {
					break
				}
				case "SendImage": {
					break
				}
				case "SendVideo": {
					break
				}
				case "SendPTT": {
					 //SendImage 54567121654 c:\abc.ogg
					break
				}
				case "SendLocation": {
					//SendLocation 54567121654 22.59202003479004 113.96762084960938 name address comment
					commands["jid"] = args[1]
					commands["latitude"] = Number(args[2])
					commands["longitude"] = Number(args[3])
					commands["name"] = args[4]
					commands["address"] = args[5]
					commands["comment"] = args[6]
					break
				}
				case "SendVcard": {
					//SendVcard 54567121654 myname BEGIN:VCARD\nVERSION:3.0\nN:;;;;\nFN:681330000000\nitem1.TEL:+681330000000\nitem1.X-ABLabel:xxx\nEND:VCARD
					commands["jid"] = args[1]
					commands["showname"] = args[2]
					commands["vcard"] = args[3]
					break
				}
			}
				
			this.SendCommand(commands)
		},	
	}
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
