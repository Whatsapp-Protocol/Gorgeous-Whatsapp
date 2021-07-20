package application.client.login;


import Gorgeous.GorgeousEngine;
import ProtocolTree.ProtocolTreeNode;
import Util.GorgeousLooper;
import Util.StringUtil;
import application.GorgeousConfig;
import application.client.StageFactory;
import application.client.chatwindow.ChatController;
import application.client.util.ResizeHelper;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import jni.NoiseJni;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  Created by Dominic on 12-Nov-15.
 */
public class LoginController implements Initializable, GorgeousEngine.GorgeousEngineDelegate, SignalProtocolLogger {
    @FXML private ImageView Defaultview;
    @FXML private TextField usernameTextfield;
    @FXML private ChoiceBox imagePicker;
    public static ChatController con;
    @FXML private BorderPane borderPane;
    private double xOffset;
    private double yOffset;
    private Scene scene;
    GorgeousEngine engine;
    AtomicBoolean checkVersion = new AtomicBoolean(false);
    static String tmpDir = System.getProperty("user.dir") + "/out";

    private static LoginController instance;

    public LoginController() {
        SignalProtocolLoggerProvider.setProvider(this);
        GorgeousLooper.Instance().Init();
        String os = System.getProperty("os.name");
        if (os.startsWith("Linux")) {
            //sudo apt install libssl-dev
            //sudo apt-get install curl libcurl4-openssl-dev
            System.load(System.getProperty("user.dir") + "/jni/libNoiseJni.so");
        } else if (os.startsWith("Windows")) {
            System.load(System.getProperty("user.dir") + "\\jni\\libNoiseJni.dll");
        } else if (os.startsWith("Mac")) {
            System.load(System.getProperty("user.dir") + "/jni/libNoiseJni.dylib");
        }

        new Thread(() -> {
            //proxyType :  "socks5" or "http"
            String status = NoiseJni.CheckWhatsappVersion("", "",0,"","", new File(System.getProperty("user.dir"), "jni").getAbsolutePath());
            if (!status.equals("success")) {
                showErrorDialog(status);
            } else {
                checkVersion.set(true);
            }
        }).start();
        File tmpDirFile = new File(tmpDir);
        if (!tmpDirFile.isDirectory()) {
            tmpDirFile.delete();
            tmpDirFile.mkdirs();
        }
        instance = this;
    }

    public static LoginController getInstance() {
        return instance;
    }

    public GorgeousEngine GetEngine() {
        return  engine;
    }
    public void loginButtonAction() throws IOException {
        if (!checkVersion.get()) {
            //check you can visit  https://www.whatsapp.com/android/
            showErrorDialog("CheckWhatsappVersion failed, check your network");
            return;
        }
		
        
        String username = usernameTextfield.getText();
        if (StringUtil.isEmpty(username)) {
            showErrorDialog("you must select config");
            return;
        }
        if (null != engine) {
            engine.StopEngine();
        }
        engine = new GorgeousEngine(username, this, null, tmpDir);
        boolean start = engine.StartEngine("your token");
        if (!start) {
            engine.StopEngine();
            engine = null;
            showErrorDialog("start engine failed");
            return;
        }
        borderPane.setDisable(true);
    }

    public void showScene(String fullPhone) {
        Platform.runLater(() -> {
            Stage stage = (Stage) usernameTextfield.getScene().getWindow();
            stage.setResizable(true);
            stage.setWidth(1324);
            stage.setHeight(748);

            stage.setOnCloseRequest((WindowEvent e) -> {
                Platform.exit();
                System.exit(0);
            });
            stage.setScene(this.scene);
            stage.setMinWidth(800);
            stage.setMinHeight(300);
            ResizeHelper.addResizeListener(stage);
            stage.centerOnScreen();
            con.setUsernameLabel(fullPhone);
        });
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
        List<GorgeousConfig.User> users = GorgeousConfig.Instance().GetUsers();
        imagePicker.getItems().addAll(users);
        if (!users.isEmpty()) {
            imagePicker.getSelectionModel().selectFirst();
            GorgeousConfig.User firstUser = users.get(0);
            if (firstUser.head != null) {
                Defaultview.setImage(new Image(new ByteArrayInputStream(firstUser.head)));
            }
            usernameTextfield.setText(firstUser.path);
        }

        imagePicker.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<GorgeousConfig.User>() {
            @Override
            public void changed(ObservableValue<? extends GorgeousConfig.User> selected, GorgeousConfig.User oldPhone, GorgeousConfig.User newPhone) {
               if (newPhone.head != null) {
                   Defaultview.setImage(new Image(new ByteArrayInputStream(newPhone.head)));
               } else {
                   Defaultview.setImage(new Image(getClass().getClassLoader().getResource("images/default.png").toString()));
               }
                usernameTextfield.setText(newPhone.path);
            }
        });
        int numberOfSquares = 30;
        while (numberOfSquares > 0){
            generateAnimation();
            numberOfSquares--;
        }
    }


    /* This method is used to generate the animation on the login window, It will generate random ints to determine
     * the size, speed, starting points and direction of each square.
     */
    public void generateAnimation(){
        Random rand = new Random();
        int sizeOfSqaure = rand.nextInt(50) + 1;
        int speedOfSqaure = rand.nextInt(10) + 5;
        int startXPoint = rand.nextInt(420);
        int startYPoint = rand.nextInt(350);
        int direction = rand.nextInt(5) + 1;

        KeyValue moveXAxis = null;
        KeyValue moveYAxis = null;
        Rectangle r1 = null;

        switch (direction){
            case 1 :
                // MOVE LEFT TO RIGHT
                r1 = new Rectangle(0,startYPoint,sizeOfSqaure,sizeOfSqaure);
                moveXAxis = new KeyValue(r1.xProperty(), 350 -  sizeOfSqaure);
                break;
            case 2 :
                // MOVE TOP TO BOTTOM
                r1 = new Rectangle(startXPoint,0,sizeOfSqaure,sizeOfSqaure);
                moveYAxis = new KeyValue(r1.yProperty(), 420 - sizeOfSqaure);
                break;
            case 3 :
                // MOVE LEFT TO RIGHT, TOP TO BOTTOM
                r1 = new Rectangle(startXPoint,0,sizeOfSqaure,sizeOfSqaure);
                moveXAxis = new KeyValue(r1.xProperty(), 350 -  sizeOfSqaure);
                moveYAxis = new KeyValue(r1.yProperty(), 420 - sizeOfSqaure);
                break;
            case 4 :
                // MOVE BOTTOM TO TOP
                r1 = new Rectangle(startXPoint,420-sizeOfSqaure ,sizeOfSqaure,sizeOfSqaure);
                moveYAxis = new KeyValue(r1.xProperty(), 0);
                break;
            case 5 :
                // MOVE RIGHT TO LEFT
                r1 = new Rectangle(420-sizeOfSqaure,startYPoint,sizeOfSqaure,sizeOfSqaure);
                moveXAxis = new KeyValue(r1.xProperty(), 0);
                break;
            case 6 :
                //MOVE RIGHT TO LEFT, BOTTOM TO TOP
                r1 = new Rectangle(startXPoint,0,sizeOfSqaure,sizeOfSqaure);
                moveXAxis = new KeyValue(r1.xProperty(), 350 -  sizeOfSqaure);
                moveYAxis = new KeyValue(r1.yProperty(), 420 - sizeOfSqaure);
                break;

            default:
                System.out.println("default");
        }

        r1.setFill(Color.web("#F89406"));
        r1.setOpacity(0.1);

        KeyFrame keyFrame = new KeyFrame(Duration.millis(speedOfSqaure * 1000), moveXAxis, moveYAxis);
        Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(true);
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
        borderPane.getChildren().add(borderPane.getChildren().size()-1,r1);
    }

    /* Terminates Application */
    public void closeSystem(){
        Platform.exit();
        System.exit(0);
    }

    public void minimizeWindow(){
        MainLauncher.getPrimaryStage().setIconified(true);
    }

    /* This displays an alert message to the user */
    public void showErrorDialog(String message) {
        Platform.runLater(()-> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning!");
            alert.setHeaderText(message);
            alert.showAndWait();
        });

    }

    public void selectButtonAction(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(usernameTextfield.getScene().getWindow());
        if (file != null) {
            usernameTextfield.setText(file.getAbsolutePath());
        }
    }

    @Override
    public void OnLogin(int code, String fullPhone, ProtocolTreeNode desc) {
        borderPane.setDisable(false);
        if (0 == code) {
            //save
            GorgeousConfig.Instance().SaveUser(fullPhone, usernameTextfield.getText());

            try {
                FXMLLoader fmxlLoader = new FXMLLoader(getClass().getResource("/views/ChatView.fxml"));
                Parent window = (Pane) fmxlLoader.load();
                con = fmxlLoader.<ChatController>getController();
                this.scene = new Scene(window);
                showScene(fullPhone);
            }
            catch (Exception e) {
                showErrorDialog(e.getMessage());
            }
        } else {
            showErrorDialog(desc.toString());
        }
    }

    @Override
    public void OnDisconnect(String desc) {
        borderPane.setDisable(false);
        /*if (con != null) {
            con.logoutScene();
        }*/
        showErrorDialog(desc);
    }

    @Override
    public void OnSync(ProtocolTreeNode content) {
        con.OnSync(content);
    }

    @Override
    public void OnPacketResponse(String type, ProtocolTreeNode content) {
        con.OnPacketResponse(type, content);
    }


    @Override
    public void log(int priority, String tag, String message) {
        System.out.println(message);
    }
}
