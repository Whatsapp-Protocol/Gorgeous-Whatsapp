#ifndef PROXYDIALOG_H
#define PROXYDIALOG_H

#include <QDialog>

struct ProxyInfo {
  QString proxy_host;
  int proxy_port = 0;
  QString proxy_username;
  QString proxy_password;
  void Reset() {
    proxy_host = "";
    proxy_username = "";
    proxy_password = "";
    proxy_port = 0;
  }
};

namespace Ui {
class ProxyDialog;
}

class ProxyDialog : public QDialog {
  Q_OBJECT

 public:
  explicit ProxyDialog(const ProxyInfo& info, QWidget* parent = 0);
  ~ProxyDialog();

  ProxyInfo GetProxyInfo();

 Q_SIGNALS:
  void ClearProxy();

 private slots:
  void on_ok_clicked();

  void on_clear_clicked();

 private:
  Ui::ProxyDialog* ui;
};

#endif  // PROXYDIALOG_H
