package io.cmichel.boilerplate;

public interface KeyWalletDevice {

    void Connection();

    void DisConnect();

    boolean IsCardPresent() throws Exception;

    short PowerOn() throws Exception;

    short PowerOff() throws Exception;

    boolean IsPowerOn();

    byte[] GetAtr() throws Exception;

    byte[] Transceive(byte[] abApduCmd) throws Exception;

}
