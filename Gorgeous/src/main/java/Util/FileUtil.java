package Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Base64;

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
}
