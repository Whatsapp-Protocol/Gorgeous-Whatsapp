#ifndef ADDCONTACTDIALOG_H
#define ADDCONTACTDIALOG_H

#include <QDialog>

namespace Ui {
class AddContactDialog;
}

class AddContactDialog : public QDialog
{
    Q_OBJECT

public:
    explicit AddContactDialog(QWidget *parent = 0);
    ~AddContactDialog();

public:
    QString GetContact();


private slots:
    void on_ok_clicked();

private:
    Ui::AddContactDialog *ui;
};

#endif // ADDCONTACTDIALOG_H
