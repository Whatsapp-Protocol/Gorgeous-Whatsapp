#include "dialog.h"
#include "registerdialog.h"
#include "ui_dialog.h"

#include <commoninputdialog.h>
#include <QDomDocument>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMenu>
#include <QMessageBox>
#include <QtSql/QSqlError>
#include <QtSql/QSqlQuery>
#include "addcontactdialog.h"

std::string JidNormalize(const std::string& jid) {
  auto pos = jid.find("@");
  if (pos != std::string::npos) {
    return jid;
  }
  pos = jid.find("-");
  if (pos != std::string::npos) {
    return jid + "@g.us";
  }
  return jid + "@s.whatsapp.net";
}

bool IsGroup(const std::string& jid) {
  auto pos = jid.find("-");
  if (pos != std::string::npos) {
    return true;
  }
  return false;
}

Dialog::Dialog(QWidget* parent) : QDialog(parent), ui(new Ui::Dialog) {
  ui->setupUi(this);
  ui->chat_group->setVisible(false);
  ui->contactlists->setTextElideMode(Qt::ElideRight);
  ui->contactlists->setResizeMode(QListView::Adjust);

  common_db_ = QSqlDatabase::addDatabase("QSQLITE", "Gorgeous");
  common_db_.setDatabaseName(QApplication::applicationDirPath() +
                             "\\Gorgeous.db");
  if (!common_db_.open()) {
    QMessageBox::warning(0, QString::fromStdWString(L"Database error"),
                         common_db_.lastError().text());
    return;
  }
  CreateCommonDbTable();
  LoadFromCommonDb();

  connect(&client_websocket_, &QWebSocket::connected, this,
          &Dialog::OnConnected);
  connect(&client_websocket_, &QWebSocket::disconnected, this,
          &Dialog::OnDisconnect);
  connect(&client_websocket_, &QWebSocket::textMessageReceived, this,
          &Dialog::onMessageReceived);

  connect(ui->output, &QTextBrowser::cursorPositionChanged, this,
          &Dialog::OutputAutoScroll);

  client_websocket_.open(
      QUrl(QString("ws://%1:%2").arg(websocket_host_).arg(websocket_port_)));
}

Dialog::~Dialog() {
  delete ui;
}

void Dialog::OnConnected() {
  ui->output_2->insertPlainText("connect success, try login or register");
}

void Dialog::OnDisconnect() {
  QMessageBox::warning(this, QString::fromStdWString(L"Error"),
                       QString::fromStdWString(L"Connect server failed"));
}

void Dialog::onMessageReceived(const QString& message) {
  //先解析json格式
  QJsonParseError jsonError;
  QJsonDocument doucment =
      QJsonDocument::fromJson(message.toUtf8(), &jsonError);
  if (!doucment.isObject()) {
    return;
  }
  QJsonObject root = doucment.object();
  QString command = root["command"].toString();

  // result 字段是一个base64 的字符串
  QString content = root["content"].toString();

  if (command == "help") {
    ui->output->insertPlainText(root["readme"].toString());
    return;
  }

  QDomDocument document;
  if (!document.setContent(content)) {
    return;
  }
  if (document.isNull()) {
    return;
  }
  QDomElement xml_root = document.documentElement();
  if (command == "login") {
    HandleLoginResponse(message, xml_root);
    return;
  } else if (command == "sync") {
    HandleSync(content, xml_root);
  } else if (command == "SyncContact") {
    HandleSyncContact(content, xml_root);
  } else if (command == "GetHDHead") {
    HandleHD(content, xml_root);
  } else if (command == "receipt") {
  } else if (command == "leavegroup") {
    HandleLeaveGroup(content, xml_root);
  } else if (command == "creategroup") {
    HandleCreateGroup(content, xml_root);
  } else {
    HandleDefault(content, xml_root);
  }
}

void Dialog::OnError(const QAbstractSocket::SocketError& error) {}

void Dialog::on_login_clicked() {
  QString fullphone = ui->fullphone->currentText();
  if (fullphone.isEmpty()) {
    QMessageBox::warning(this, QString::fromStdWString(L"Error"),
                         QString::fromStdWString(L"Check params"));
    return;
  }

  QJsonObject command;

  command.insert("task_id", 1);
  command.insert("command", "loginphone");

  command.insert("proxy_server", proxy_.proxy_host);
  command.insert("proxy_port", proxy_.proxy_port);
  command.insert("proxy_username", proxy_.proxy_username);
  command.insert("proxy_password", proxy_.proxy_password);

  command.insert("fullphone", fullphone);

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  client_websocket_.sendTextMessage(QString(byteArray));
  ui->login->setEnabled(false);
}

void Dialog::on_register_2_clicked() {
  RegisterDialog dlg(proxy_, websocket_host_, websocket_port_);
  dlg.exec();
}

void Dialog::HandleLoginResponse(const QString& message,
                                 const QDomElement& root) {
  ui->output_2->insertPlainText(message);
  QString tag = root.tagName();
  if (tag != "success") {
    ui->login->setEnabled(true);
  } else {
    ui->login_group->setVisible(false);
    ui->chat_group->setVisible(true);
    //保存账号信息
    SaveUser(ui->fullphone->currentText());
    //登录成功字后读取数据库
    LoadFromDb();
  }
}

void Dialog::HandleSyncContact(const QString& message,
                               const QDomElement& xml_root) {
  QDomElement usync = xml_root.firstChildElement("usync");
  if (usync.isNull()) {
    return;
  }
  QDomElement lists = usync.firstChildElement("list");
  QDomElement user = lists.firstChildElement();
  QString jid = user.attribute("jid");
  while (!user.isNull()) {
    QDomElement status = user.firstChildElement("status");
    QDomElement contact = user.firstChildElement("contact");
    QString contact_type = contact.attribute("type");
    if (contact_type == "in") {
      //添加到左侧
      InsertContactItem(jid, QByteArray());
      //保存联系人
      SaveContact(jid, QByteArray());
      //获取一下头像
      GetHdFromServer(jid);
    } else {
      //提示
      ui->output->insertPlainText(
          QString::fromStdWString(L"%1 NO whatsapp").arg(jid));
    }

    user = user.nextSiblingElement();
  }
  ui->contactlists->update();
}

void Dialog::HandleHD(const QString& message, const QDomElement& xml_root) {
  QDomElement picture = xml_root.firstChildElement("picture");
  if (picture.isNull()) {
    return;
  }
  QString jid = xml_root.attribute("from");
  for (int i = 0; i < ui->contactlists->count(); i++) {
    QListWidgetItem* pItem = ui->contactlists->item(i);
    if (pItem->text() == jid) {
      QByteArray picture_data = QByteArray::fromBase64(picture.text().toUtf8());
      QPixmap pixmap;
      if (pixmap.loadFromData(picture_data)) {
        pItem->setIcon(QIcon(pixmap));
      }
      SaveContact(jid, picture_data);
      break;
    }
  }
}

void Dialog::HandleDefault(const QString& message, const QDomElement& root) {
  ui->output->insertPlainText(message);
}

void Dialog::HandleSync(const QString& message, const QDomElement& xml_root) {
  QString tag_name = xml_root.tagName();
  if (tag_name == "message") {
    QString jid = xml_root.attribute("from");
    ui->output->insertPlainText(
        QString::fromStdWString(L"%1:\r\n %2").arg(jid).arg(message));
    //判断是否已经存在联系人，如果存在就不添加，否则需要添加
    bool find = false;
    for (int i = 0; i < ui->contactlists->count(); i++) {
      QListWidgetItem* contact = ui->contactlists->item(i);
      if (contact->text() == jid) {
        find = true;
        break;
      }
    }
    if (!find) {
      SaveContact(jid, QByteArray());
      InsertContactItem(jid, QByteArray());
      GetHdFromServer(jid);
    }
  }
}

void Dialog::HandleCreateGroup(const QString& message,
                               const QDomElement& root) {
  QDomElement group = root.firstChildElement("group");
  if (group.isNull()) {
    return;
  }
  QString groupid = group.attribute("id");
  groupid = QString::fromStdString(JidNormalize(groupid.toStdString()));
  //保存到数据库
  SaveContact(groupid, QByteArray());
  //添加一个
  InsertContactItem(groupid, QByteArray());
}

void Dialog::DeleteContact(const QString& jid) {
  QSqlQuery query(db_);
  query.prepare("delete from contacts where jid=?");
  query.addBindValue(jid);
  query.exec();
}

void Dialog::HandleLeaveGroup(const QString& message,
                              const QDomElement& xml_root) {
  db_.transaction();

  QDomElement leave = xml_root.firstChildElement("leave");
  if (leave.isNull()) {
    return;
  }
  QDomElement group = leave.firstChildElement("group");
  while (!group.isNull()) {
    QString jid = group.attribute("id");
    //从数据库删除
    DeleteContact(jid);
    RemoveContactItem(jid);

    group = group.nextSiblingElement();
  }
  ui->contactlists->update();

  db_.commit();
}

void Dialog::LoadFromCommonDb() {
  {
    //加载代理
    QSqlQuery query(common_db_);
    query.exec("select * from proxy;");
    if (query.next()) {
      proxy_.proxy_host = query.value("host").toString();
      proxy_.proxy_port = query.value("port").toInt();
      proxy_.proxy_username = query.value("username").toString();
      proxy_.proxy_password = query.value("password").toString();
    }
  }

  {
    //加载用户
    QSqlQuery query(common_db_);
    query.exec("select * from usersinfo;");
    while (query.next()) {
      QString fullphone = query.value("fullphone").toString();
      ui->fullphone->addItem(fullphone);
    }
  }
}

void Dialog::CreateCommonDbTable() {
  {
    //创建代理
    QSqlQuery query(common_db_);
    query.exec(
        "CREATE TABLE IF NOT EXISTS proxy (_id int PRIMARY KEY, host text, "
        "port int, username text, password text);");
  }

  {
    //创建用户
    QSqlQuery query(common_db_);
    query.exec(
        "CREATE TABLE IF NOT EXISTS usersinfo (fullphone text primary  key);");
  }
}

void Dialog::SaveProxy() {
  QSqlQuery query(common_db_);
  query.prepare(
      "INSERT OR REPLACE INTO proxy (_id, host, port,username,password) "
      "values(0, ?,?,?,?)");
  query.addBindValue(proxy_.proxy_host);
  query.addBindValue(proxy_.proxy_port);
  query.addBindValue(proxy_.proxy_username);
  query.addBindValue(proxy_.proxy_password);
  query.exec();
}

void Dialog::SaveUser(const QString& fullphone) {
  QSqlQuery query(common_db_);
  query.prepare("INSERT OR REPLACE INTO usersinfo (fullphone) values(?)");
  query.addBindValue(fullphone);
  query.exec();
}

void Dialog::LoadFromDb() {
  db_ = QSqlDatabase::addDatabase("QSQLITE");
  db_.setDatabaseName(QApplication::applicationDirPath() + "\\" +
                      ui->fullphone->currentText());
  if (!db_.open()) {
    QMessageBox::warning(0, QString::fromStdWString(L"Database error"),
                         db_.lastError().text());
    return;
  }
  CreateDefaultTable();
  LoadContacts();
}

void Dialog::CreateDefaultTable() {
  {
    //创建联系人表
    QSqlQuery query(db_);
    query.exec(
        "CREATE TABLE IF NOT EXISTS contacts (jid TEXT PRIMARY KEY, head "
        "BLOB);");
  }
}

void Dialog::LoadContacts() {
  QSqlQuery query(db_);
  query.exec("select * from contacts;");
  while (query.next()) {
    QString jid = query.value("jid").toString();
    QByteArray head = query.value("head").toByteArray();
    InsertContactItem(jid, head);
  }
}

void Dialog::GetHdFromServer(const QString& jid) {
  QJsonObject command;
  command.insert("task_id", task_id_++);
  command.insert("command", "GetHDHead");
  command.insert("jid", jid);

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  client_websocket_.sendTextMessage(QString(byteArray));
}

void Dialog::SaveContact(const QString& jid, const QByteArray& data) {
  QSqlQuery query(db_);
  query.prepare("INSERT OR REPLACE INTO contacts (jid, head) values(?,?)");
  query.addBindValue(jid);
  query.addBindValue(data);
  query.exec();
}

void Dialog::InsertContactItem(const QString& jid, const QByteArray& data) {
  QListWidgetItem* contact_item = new QListWidgetItem(ui->contactlists);
  contact_item->setText(jid);
  QPixmap pixmap;
  if (pixmap.loadFromData(data)) {
    contact_item->setIcon(QIcon(pixmap));
  }
  contact_item->setTextAlignment(Qt::AlignHCenter);
  contact_item->setFlags(Qt::ItemIsSelectable | Qt::ItemIsEnabled |
                         Qt::ItemIsEditable | Qt::ItemIsDragEnabled);
}

void Dialog::RemoveContactItem(const QString& jid) {
  for (int i = 0; i < ui->contactlists->count(); i++) {
    if (ui->contactlists->item(i)->text() == jid) {
      delete ui->contactlists->takeItem(
          ui->contactlists->row(ui->contactlists->item(i)));
      return;
    }
  }
}

void Dialog::on_addcontact_clicked() {
  AddContactDialog dlg;
  if (QDialog::Accepted == dlg.exec()) {
    QString contact = dlg.GetContact();

    QJsonObject command;
    command.insert("task_id", task_id_++);
    command.insert("command", "SyncContact");

    QJsonArray phones;
    phones.append(contact);

    command.insert("phones", phones);

    QJsonDocument document;
    document.setObject(command);
    QByteArray byteArray = document.toJson(QJsonDocument::Compact);
    client_websocket_.sendTextMessage(QString(byteArray));
  }
}

void Dialog::on_send_clicked() {
  if (ui->sendcontent->toPlainText().isEmpty()) {
    return;
  }
  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  if (selected.isEmpty()) {
    return;
  }
  for (auto contact : selected) {
    QJsonObject command;
    command.insert("task_id", task_id_++);
    command.insert("command", "SendText");
    command.insert("jid", contact->text());
    command.insert("content", ui->sendcontent->toPlainText());

    QJsonDocument document;
    document.setObject(command);
    QByteArray byteArray = document.toJson(QJsonDocument::Compact);
    client_websocket_.sendTextMessage(QString(byteArray));
  }
  ui->sendcontent->clear();
}

void Dialog::OutputAutoScroll() {
  QTextCursor cursor = ui->output->textCursor();
  cursor.movePosition(QTextCursor::End);
  ui->output->setTextCursor(cursor);
}

void Dialog::on_contactlists_customContextMenuRequested(const QPoint& pos) {
  QListWidgetItem* curItem = ui->contactlists->itemAt(pos);
  if (curItem == NULL)
    return;

  QMenu* popMenu = new QMenu(this);
  //删除菜单
  QAction* delete_contact =
      new QAction(QString::fromStdWString(L"delete"), this);
  popMenu->addAction(delete_contact);
  connect(delete_contact, SIGNAL(triggered()), this, SLOT(on_delete_contact()));

  //创建群组
  QAction* create_group = nullptr;
  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  if (selected.size() >= 2) {
    create_group = new QAction(QString::fromStdWString(L"CreateGroup"), this);
    popMenu->addAction(create_group);
    connect(create_group, SIGNAL(triggered()), this, SLOT(on_create_group()));
  }

  //修改群名
  QAction* modify_group_subject = nullptr;
  QAction* leave_group = nullptr;
  {
    if (selected.size() == 1) {
      QString jid = selected[0]->text();
      if (IsGroup(jid.toStdString())) {
        {
          modify_group_subject =
              new QAction(QString::fromStdWString(L"ModifyGroupName"), this);
          popMenu->addAction(modify_group_subject);
          connect(modify_group_subject, SIGNAL(triggered()), this,
                  SLOT(on_modify_group_name()));
        }
        {
          leave_group =
              new QAction(QString::fromStdWString(L"LeaveGroup"), this);
          popMenu->addAction(leave_group);
          connect(leave_group, SIGNAL(triggered()), this,
                  SLOT(on_leave_group()));
        }
      }
    }
  }

  popMenu->exec(QCursor::pos());
  delete popMenu;
  delete delete_contact;
  delete create_group;
  delete modify_group_subject;
  delete leave_group;
}

void Dialog::on_delete_contact() {
  db_.transaction();
  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  for (auto contact : selected) {
    DeleteContact(contact->text());
    delete ui->contactlists->takeItem(ui->contactlists->row(contact));
  }
  db_.commit();
}

void Dialog::on_create_group() {
  CommonInputDialog dlg(QString::fromStdWString(L"Input Group Subject"));
  if (dlg.exec() != QDialog::Accepted) {
    return;
  }

  QJsonObject command;
  command.insert("task_id", task_id_++);
  command.insert("command", "CreateGroup");
  command.insert("subject", dlg.GetValue1());

  QJsonArray members;
  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  for (auto contact : selected) {
    members.append(contact->text());
  }
  command.insert("members", members);

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  client_websocket_.sendTextMessage(QString(byteArray));
}

void Dialog::on_modify_group_name() {
  CommonInputDialog dlg(QString::fromStdWString(L"Input Group Subject"));
  if (dlg.exec() != QDialog::Accepted) {
    return;
  }

  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  if (selected.size() != 1) {
    return;
  }
  QJsonObject command;
  command.insert("task_id", task_id_++);
  command.insert("command", "ModifyGroupSubject");
  command.insert("subject", dlg.GetValue1());
  command.insert("jid", selected[0]->text());

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  client_websocket_.sendTextMessage(QString(byteArray));
}

void Dialog::on_leave_group() {
  QList<QListWidgetItem*> selected = ui->contactlists->selectedItems();
  if (selected.size() != 1) {
    return;
  }
  QJsonObject command;
  command.insert("task_id", task_id_++);
  command.insert("command", "LeaveGroups");

  QJsonArray groups;
  groups.append(selected[0]->text());
  command.insert("groups", groups);

  QJsonDocument document;
  document.setObject(command);
  QByteArray byteArray = document.toJson(QJsonDocument::Compact);
  client_websocket_.sendTextMessage(QString(byteArray));
}

void Dialog::on_setproxy_clicked() {
  ProxyDialog dlg(proxy_);
  connect(&dlg, &ProxyDialog::ClearProxy, this, &Dialog::on_clearproxy);
  if (dlg.exec() == QDialog::Accepted) {
    proxy_ = dlg.GetProxyInfo();
    SaveProxy();
  }
}

void Dialog::on_clearproxy() {
  QSqlQuery query(common_db_);
  query.prepare("delete from proxy");
  query.exec();
  proxy_.Reset();
}
