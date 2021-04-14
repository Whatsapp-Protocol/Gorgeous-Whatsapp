#include "commoninputdialog.h"
#include "ui_commoninputdialog.h"

CommonInputDialog::CommonInputDialog(const QString& title, const QString& value1 , const QString& value2 , QWidget *parent) :
    QDialog(parent),
    ui(new Ui::CommonInputDialog)
{
    ui->setupUi(this);
	setWindowTitle(title);
	ui->value1->setText(value1);
	ui->value2->setText(value2);
}

CommonInputDialog::~CommonInputDialog()
{
    delete ui;
}

QString CommonInputDialog::GetValue1()
{
	return ui->value1->text();
}

QString CommonInputDialog::GetValue2()
{
	return ui->value2->text();
}

void CommonInputDialog::on_ok_clicked()
{
	if (ui->value1->text().isEmpty())
	{
		return;
	}
	accept();
}
