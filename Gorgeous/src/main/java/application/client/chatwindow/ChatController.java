package application.client.chatwindow;

import Gorgeous.GorgeousEngine;
import Message.WhatsMessage;
import ProtocolTree.ProtocolTreeNode;
import ProtocolTree.StanzaAttribute;
import Util.StringUtil;
import application.GorgeousConfig;
import application.client.StageFactory;
import application.UserConfig;
import application.client.login.LoginController;
import application.client.login.MainLauncher;
import application.client.util.ToastUtil;
import application.messages.MessageBean;
import application.messages.User;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.logging.Log;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.net.URL;
import java.util.*;


public class ChatController implements Initializable {
    public TextField usernameEdit;



    HashMap<String, Tab> chatTabMap = new HashMap<>();
    @FXML private TabPane chatPans;
    @FXML private Label usernameLabel;
    @FXML private Label onlineCountLabel;


    List<User> friends = new LinkedList<>();
    @FXML private ListView userList;
    @FXML private ImageView userImageView;
    @FXML BorderPane borderPane;

    UserConfig userConfig;
    String selfFullPhone;
    String selfJid;

    private double xOffset;
    private double yOffset;
    static ChatController instance;
    public ChatController() {
        instance = this;
    }

    public static ChatController getInstance() {
        return instance;
    }

    
    public void setUsernameLabel(String fullPhone) {
        selfFullPhone = fullPhone;
        selfJid = GorgeousEngine.JidNormalize(fullPhone);

        File dataDir = new File(System.getProperty("user.dir"), "database");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        String fullPath = new File(dataDir, fullPhone + "_user.db").getAbsolutePath();
        userConfig = new UserConfig(fullPath);
        this.usernameLabel.setText(userConfig.GetPushName());

        try {
            setImageLabel(userConfig.GetHead());
        } catch (IOException e) {
            e.printStackTrace();
        }
        userList.getItems().addAll(userConfig.GetContacts());
        for (User user : friends) {
            LoginController.getInstance().GetEngine().Subscribe(user.jid);
        }
        UpdateOnlineFriendCount();
    }

    public void setImageLabel(byte[] buffer) throws IOException {
        if (null == buffer) {
            this.userImageView.setImage(new Image(getClass().getClassLoader().getResource("images/default.png").toString()));
        } else {
            InputStream buffin = new ByteArrayInputStream(buffer);
            this.userImageView.setImage(new Image(buffin));
        }
    }

    public void setUserList() {
        ObservableList<User> users = FXCollections.observableList(friends);
        userList.setItems(users);
        userList.setCellFactory(new CellRender());
    }

    @FXML
    public void closeApplication() {
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            setImageLabel(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
                /* Drag and Drop */
        borderPane.setOnMousePressed(event -> {
            xOffset = MainLauncher.getPrimaryStage().getX() - event.getScreenX();
            yOffset = MainLauncher.getPrimaryStage().getY() - event.getScreenY();
            borderPane.setCursor(Cursor.CLOSED_HAND);
        });

        borderPane.setOnMouseDragged(event -> {
            MainLauncher.getPrimaryStage().setX(event.getScreenX() + xOffset);
            MainLauncher.getPrimaryStage().setY(event.getScreenY() + yOffset);

        });

        borderPane.setOnMouseReleased(event -> {
            borderPane.setCursor(Cursor.DEFAULT);
        });

        setUserList();
        chatPans.selectionModelProperty().addListener(new ChangeListener<SingleSelectionModel<Tab>>() {
            @Override
            public void changed(ObservableValue<? extends SingleSelectionModel<Tab>> observable, SingleSelectionModel<Tab> oldValue, SingleSelectionModel<Tab> newValue) {

            }
        });
    }

    public void logoutScene() {
        Platform.runLater(() -> {
            FXMLLoader fmxlLoader = new FXMLLoader(getClass().getResource("/views/LoginView.fxml"));
            Parent window = null;
            try {
                window = (Pane) fmxlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Stage stage = MainLauncher.getPrimaryStage();
            Scene scene = new Scene(window);
            stage.setMaxWidth(350);
            stage.setMaxHeight(420);
            stage.setResizable(false);
            stage.setScene(scene);
            stage.centerOnScreen();
        });
    }

    public void OnAddContact(ActionEvent actionEvent) {
        Stage addContact = StageFactory.Create("/views/AddContact.fxml", 369, 216, "Add Contact");
        addContact.setResizable(false);
        addContact.show();
    }


    public void OnSync(ProtocolTreeNode content) {
        Platform.runLater(() -> {
            String tag = content.GetTag();
            switch (tag) {
                case "presence" :{
                    HandlePresence(content);
                }
                break;
                case "message" : {
                    HandleRecvMessage(content);
            }
                break;
            }
        });
    }

    private void HandleRecvMessage(ProtocolTreeNode content) {
        ProtocolTreeNode plain = content.GetChild("plain");
        if (null == plain) {
            return;
        }
        Message.WhatsMessage.WhatsAppMessage msg = (Message.WhatsMessage.WhatsAppMessage)plain.GetCustomParams();
        String jid = content.GetAttributeValue("from");
        Tab chatTab = chatTabMap.get(jid);
        if (chatTab == null) {
            User userItem = GetFriend(jid);
            if (userItem == null) {
                String name = jid;
                int index = jid.indexOf("@");
                if (index != -1) {
                    name = jid.substring(0, index);
                }

                userItem = new User();
                userItem.jid =  jid;
                userItem.name = name;
                userItem.status =  "";

                userConfig.SaveContact(jid, name, "");
                userList.getItems().add(userItem);

                LoginController.getInstance().GetEngine().Subscribe(jid);
            }

            //add chat pane
            AddChatPane(userItem);
            chatTab = chatTabMap.get(jid);
        }
        ChatPaneController paneController =  (ChatPaneController)chatTab.getUserData();
        paneController.AddMessage(msg);
    }

    public void OnPacketResponse(String type, ProtocolTreeNode content) {
        Platform.runLater(() -> {
            switch (type) {
                case "SyncContact": {
                    HandleAddContact(content);
                }
                break;
                case "GetHDHead": {
                    HandleGetHead(content);
                }
                break;
                case "SetHDHead" : {
                    HandleSetHead(content);
                }
                break;
            }
        });
    }

    private void HandleSetHead(ProtocolTreeNode content) {
        String type = content.GetAttributeValue("type");
        if (!type.equals("result")) {
            ToastUtil.toast("set head failed:  only support(640*640)");
        }
    }

    User GetFriend(String jid) {
        for (User user : friends) {
            if (user.jid.equals(jid)) {
                return user;
            }
        }
        return null;
    }

    void UpdateSelfHead(ProtocolTreeNode content) {
        ProtocolTreeNode picture = content.GetChild("picture");
        userConfig.UpdateHead(picture.GetData());
        InputStream buffin = new ByteArrayInputStream(picture.GetData());
        userImageView.setImage(new Image(buffin));
        GorgeousConfig.Instance().UpdateHead(selfFullPhone, picture.GetData());
    }

    void HandleGetHead(ProtocolTreeNode content) {
        ProtocolTreeNode picture = content.GetChild("picture");
        if (null == picture) {
            return;
        }

        String jid = content.GetAttributeValue("from");
        if (selfJid.equals(jid)) {
            UpdateSelfHead(content);
            return;
        }

        userConfig.UpdateContactHead(jid, picture.GetData());
        User user = GetFriend(jid);
        if (user != null) {
            user.picture = picture.GetData();
            userList.refresh();
        }
    }

    void  HandlePresence(ProtocolTreeNode content) {
        String jid = content.GetAttributeValue("from");
        User user = GetFriend(jid);
        if (user != null) {
            String type = content.GetAttributeValue("type");
            if (type.equals("unavailable")) {
                user.online = false;
                user.last = content.GetAttributeValue("last");
            } else {
                user.online = true;
            }
        }
        userList.refresh();
        UpdateOnlineFriendCount();
    }

    void HandleAddContact(ProtocolTreeNode content) {
        ProtocolTreeNode usync = content.GetChild("usync");
        if (null == usync) {
            return;
        }
        ProtocolTreeNode lists = usync.GetChild("list");
        if (lists == null) {
            return;
        }
        List<ProtocolTreeNode> users = lists.GetChildren();
        for (ProtocolTreeNode user : users) {
            ProtocolTreeNode contact = user.GetChild("contact");
            if (contact == null) {
                continue;
            }
            String type = contact.GetAttributeValue("type");
            if (!type.equals("in")) {
                continue;
            }
            SaveContact(user);
            //subscribe
            String jid = user.GetAttributeValue("jid");
            LoginController.getInstance().GetEngine().Subscribe(jid);
            LoginController.getInstance().GetEngine().GetHDHead(jid);
        }
        LinkedHashSet hashSet = new LinkedHashSet(friends);
        userList.getItems().clear();
        userList.getItems().addAll(hashSet);
        UpdateOnlineFriendCount();
    }

    void SaveContact(ProtocolTreeNode user) {
        ProtocolTreeNode status = user.GetChild("status");
        String contactStatus =  new String(status.GetData());
        String jid = user.GetAttributeValue("jid");
        int index = jid.indexOf("@");
        String name = jid;
        if (index != -1) {
            name = jid.substring(0, index);
        }

        userConfig.SaveContact(jid, name,contactStatus);

        User userItem = new User();
        userItem.jid =  jid;
        userItem.name = name;
        userItem.status =  contactStatus;
        userList.getItems().add(userItem);
    }

    void UpdateOnlineFriendCount() {
        int onlineCount = 0;
        for (User user : friends) {
            if (user.online) {
                onlineCount++;
            }
        }
        onlineCountLabel.setText(String.valueOf(onlineCount));
    }

    public void OnNameClicked(MouseEvent mouseEvent) {
        usernameLabel.setVisible(false);
        usernameEdit.setVisible(true);
        usernameEdit.setText(usernameLabel.getText());
    }

    public void OnEditNameFinish(KeyEvent keyEvent) {
        if (keyEvent.getCode() !=  KeyCode.ENTER) {
            return;
        }
        usernameLabel.setVisible(true);
        usernameEdit.setVisible(false);
        if (usernameEdit.getText().equals(usernameLabel.getText())) {
            return;
        }
        usernameLabel.setText(usernameEdit.getText());

        LoginController.getInstance().GetEngine().SetPushName(usernameEdit.getText());
        userConfig.UpdatePushName(usernameEdit.getText());
    }


    public void AddChatPane(User user) {
         Tab chatTab = chatTabMap.get(user.jid);
         if (chatTab == null) {
             try {
                 chatTab = new Tab(user.name);
                 FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("views/ChatPane.fxml"));
                 Parent root = loader.load();
                 chatTab.setContent(root);
                 ChatPaneController paneController = loader.getController();
                 paneController.InitData(user);

                 chatTab.setUserData(paneController);

                 chatTabMap.put(user.jid, chatTab);
                 chatPans.getTabs().add(chatTab);
             }
             catch (Exception e) {
             }
         }
        chatPans.getSelectionModel().select(chatTab);
    }

    public void OnUploadHead(MouseEvent mouseEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a JPG image with a resolution of 640* 640 and a size of less than 100KB.");

        FileChooser.ExtensionFilter imageFilter =   new FileChooser.ExtensionFilter("JPG Image (.jpg,.jpeg)", "*.jpg", "*.jpeg");
        fileChooser.getExtensionFilters().add(imageFilter);

        fileChooser.setSelectedExtensionFilter(imageFilter);
        File file = fileChooser.showOpenDialog(userImageView.getScene().getWindow());
        if (file != null) {
            try {
                this.userImageView.setImage(new Image(new FileInputStream(file)));
                LoginController.getInstance().GetEngine().SetHDHead(file.getAbsolutePath());
                userConfig.UpdateHead(StringUtil.ReadFileContent(file.getAbsolutePath()));
            }
            catch (Exception e) {

            }
        }
    }

    public void OnMouseEntered(MouseEvent mouseEvent) {
        userImageView.setCursor(Cursor.HAND);
    }

    public void DeleteSession(User user) {
        //delete from userlist
        userList.getItems().remove(user);
        userConfig.DeleteContact(user.jid);
        Tab chatTab = chatTabMap.get(user.jid);
        if (chatTab != null) {
            chatPans.getTabs().remove(chatTab);
        }
    }

    public void OnRefreshHead(ActionEvent actionEvent) {
        LoginController.getInstance().GetEngine().GetHDHead(selfJid);
    }
}