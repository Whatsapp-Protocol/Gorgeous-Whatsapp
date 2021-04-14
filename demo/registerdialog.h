#ifndef REGISTERDIALOG_H
#define REGISTERDIALOG_H

#include <QtWebSockets/qwebsocket.h>
#include <QDialog>
#include "proxydialog.h"

namespace Ui {
class RegisterDialog;
}

enum RegisterStatus {
  kStatusInit,
  kStatusExist,
  kStatusReigster,

};

class RegisterDialog : public QDialog {
  Q_OBJECT

 public:
  RegisterDialog(const ProxyInfo& proxy,
                 const QString& host,
                 int port,
                 QWidget* parent = 0);
  ~RegisterDialog();

 Q_SIGNALS:

 private slots:
  void OnConnected();
  void OnDisconnect();
  void onMessageReceived(const QString& message);

  void on_coderequest_clicked();
  void on_register_2_clicked();

 private:
  void HandleInitStatus();
  void HandleExistStatus(const QString& message);
  void HandleRegisterStatus(const QString& message);

 private:
  Ui::RegisterDialog* ui;
  QWebSocket register_websocket_;
  RegisterStatus register_status_ = kStatusInit;
  int current_task_id_ = 0;
  ProxyInfo proxy_;
};

#endif  // REGISTERDIALOG_H
