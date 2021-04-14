#include "addcontactdialog.h"
#include "ui_addcontactdialog.h"

#include <QMessageBox>
AddContactDialog::AddContactDialog(QWidget *parent) :
    QDialog(parent),
    ui(new Ui::AddContactDialog)
{
    ui->setupUi(this);
}

AddContactDialog::~AddContactDialog()
{
    delete ui;
}

void AddContactDialog::on_ok_clicked()
{
	QString cc = ui->cc->text();
	QString phone = ui->phone->text();
	if (cc.isEmpty() || phone.isEmpty()){
		QMessageBox::warning(this, QString::fromStdWString(L"错误"), QString::fromStdWString(L"参数没填完整"));
		return;
	}
    accept();
}


QString AddContactDialog::GetContact(){
    return QString("+") + ui->cc->text() + ui->phone->text();
}
