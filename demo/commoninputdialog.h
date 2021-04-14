#ifndef COMMONINPUTDIALOG_H
#define COMMONINPUTDIALOG_H

#include <QDialog>

namespace Ui {
class CommonInputDialog;
}

class CommonInputDialog : public QDialog
{
    Q_OBJECT

public:
	CommonInputDialog(const QString& title, const QString& value1 = "", const QString& value2 = "", QWidget *parent = 0);
    ~CommonInputDialog();

	QString GetValue1();
	QString GetValue2();

private slots:
    void on_ok_clicked();

private:
    Ui::CommonInputDialog *ui;
};

#endif // COMMONINPUTDIALOG_H
