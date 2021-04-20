package jni;

public class Register {
    public static native String EncodeExistRequest(byte[] params, byte[] env);
    public static native String CodeRequest(byte[] params, byte[] env);
    public static native String Register(byte[] params, byte[] env);
}
