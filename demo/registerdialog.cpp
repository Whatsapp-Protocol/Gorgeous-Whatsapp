#include "registerdialog.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMessageBox>
#include "ui_registerdialog.h"

RegisterDialog::RegisterDialog(const ProxyInfo& proxy,
                               const QString& host,
                               int port,
                               QWidget* parent)
    : QDialog(parent), ui(new Ui::RegisterDialog), proxy_(proxy) {
  ui->setupUi(this);
  ui->code_group->setVisible(false);
  connect(&register_websocket_, &QWebSocket::connected, this,
          &RegisterDialog::OnConnected);
  connect(&register_websocket_, &QWebSocket::disconnected, this,
          &RegisterDialog::OnDisconnect);
  register_websocket_.open(QUrl(QString("ws://%1:%2").arg(host).arg(port)));
}

RegisterDialog::~RegisterDialog() {
  delete ui;
}

void RegisterDialog::OnConnected() {
  connect(&register_websocket_, &QWebSocket::textMessageReceived, this,
          &RegisterDialog::onMessageReceived);
}

void RegisterDialog::OnDisconnect() {
  // QMessageBox::warning(this, QString::fromStdWString(L"错误"),
  // QString::fromStdWString(L"连接服务器失败"));
}

void RegisterDialog::onMessageReceived(const QString& message) {
  if (register_status_ == kStatusInit) {
    ui->tips->setText(message);
    return;
  }
  ui->output->insertPlainText(message);
  QJsonParseError jsonError;
  QJsonDocument doucment =
      QJsonDocument::fromJson(message.toUtf8(), &jsonError);
  if (!doucment.isObject()) {
    return;
  }
  QJsonObject root = doucment.object();
  int code = root["code"].toInt();
  if (code != 0) {
    return;
  }
  QString result = root["result"].toString();

  switch (register_status_) {
    case kStatusExist: {
      HandleExistStatus(result);
    } break;
    case kStatusReigster: {
      HandleRegisterStatus(result);
    } break;
    default:
      break;
  }
}

void RegisterDialog::on_coderequest_clicked() {
  QString cc = ui->cc->text();
  QString phone = ui->phone->text();
  if (cc.isEmpty() || phone.isEmpty()) {
    QMessageBox::warning(this, QString::fromStdWString(L"错误"),
                         QString::fromStdWString(L"参数没填完整"));
    return;
  }
  HandleInitStatus();
}

void RegisterDialog::on_register_2_clicked() {
  QString cc = ui->cc->text();
  QString phone = ui->phone->text();
  QString code = ui->code->text();
  if (cc.isEmpty() || phone.isEmpty() || code.isEmpty()) {
    QMessageBox::warning(this, QString::fromStdWString(L"错误"),
                         QString::fromStdWString(L"参数没填完整"));
    return;
  }

  QJsonObject command;
  command.insert("task_id", current_task_id_++);
  command.insert("command", "register");

  command.insert("cc", ui->cc->text());
  command.insert("phone", ui->phone->text());

  command.insert("code", ui->code->text());

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  register_websocket_.sendTextMessage(QString(byteArray));
  register_status_ = kStatusReigster;
}

void RegisterDialog::HandleInitStatus() {
  //发送获取账号状态请求
  QJsonObject command;
  command.insert("task_id", current_task_id_++);
  command.insert("command", "exist");

  command.insert("proxy_server", proxy_.proxy_host);
  command.insert("proxy_port", proxy_.proxy_port);
  command.insert("proxy_username", proxy_.proxy_username);
  command.insert("proxy_password", proxy_.proxy_password);
  command.insert("cc", ui->cc->text());
  command.insert("phone", ui->phone->text());

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  register_websocket_.sendTextMessage(QString(byteArray));
  ui->tips->setText(QString::fromStdWString(L"获取账号是否存在..."));
  register_status_ = kStatusExist;
}

void RegisterDialog::HandleExistStatus(const QString& message) {
  QJsonParseError jsonError;
  QJsonDocument doucment =
      QJsonDocument::fromJson(message.toUtf8(), &jsonError);
  if (!doucment.isObject()) {
    return;
  }
  QJsonObject root = doucment.object();
  QString status = root["status"].toString();
  if (status == "ok") {
    ui->tips->setText(QString::fromStdWString(L"账号存在可以直接登录"));
  } else {
    ui->tips->setText(QString::fromStdWString(L"输入手机验证码..."));
    ui->code_group->setVisible(true);
    ui->coderequest->setVisible(false);
  }
}

void RegisterDialog::HandleRegisterStatus(const QString& message) {
  QJsonParseError jsonError;
  QJsonDocument doucment =
      QJsonDocument::fromJson(message.toUtf8(), &jsonError);
  if (!doucment.isObject()) {
    return;
  }
  QJsonObject root = doucment.object();
  QString status = root["status"].toString();
  if (status == "ok") {
    ui->tips->setText(QString::fromStdWString(L"注册成功，可以登录了"));
  } else {
    ui->tips->setText(message);
  }
}
