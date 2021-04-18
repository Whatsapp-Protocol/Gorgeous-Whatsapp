#include "proxydialog.h"
#include <QMessageBox>
#include "ui_proxydialog.h"
ProxyDialog::ProxyDialog(const ProxyInfo& info, QWidget* parent)
    : QDialog(parent), ui(new Ui::ProxyDialog) {
  ui->setupUi(this);
  ui->proxyhost->setText(info.proxy_host);
  ui->proxyport->setText(QString("%1").arg(info.proxy_port));
  ui->proxyusername->setText(info.proxy_username);
  ui->proxypassword->setText(info.proxy_password);
}

ProxyDialog::~ProxyDialog() {
  delete ui;
}

ProxyInfo ProxyDialog::GetProxyInfo() {
  ProxyInfo info;
  info.proxy_host = ui->proxyhost->text();
  info.proxy_port = ui->proxyport->text().toInt();
  info.proxy_username = ui->proxyusername->text();
  info.proxy_password = ui->proxypassword->text();
  return info;
}

void ProxyDialog::on_ok_clicked() {
  QString proxy_host = ui->proxyhost->text();
  QString proxy_port = ui->proxyport->text();
  if (proxy_host.isEmpty() || proxy_port.isEmpty()) {
    QMessageBox::warning(this, QString::fromStdWString(L"Error"),
                         QString::fromStdWString(L"Check params"));
    return;
  }

  accept();
}

void ProxyDialog::on_clear_clicked() {
  emit ClearProxy();
}
