package Util;

import java.io.*;

public class FileUtil {
    public static byte[] ReadFileContent(String path) {
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(path);
            int len =  inputStream.available();
            byte[] buffer = new byte[len];
            inputStream.read(buffer);
            inputStream.close();
            return buffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (null != inputStream) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void WriteFileContent(byte[] buffer, String path) {
        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            outputStream.write(buffer);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
